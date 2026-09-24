(() => {
  const $ = (id) => document.getElementById(id);
  const state = { token: null, username: null, role: null, activeView: null, reviewsTimer: null, lastRunId: null };
  const views = { ask: 'askView', screening: 'screeningView', reviews: 'reviewsView', trace: 'traceView' };
  const icons = { ask: '⌕', screening: '＋', reviews: '◷', trace: '⌁' };

  function decodeRole(token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload.role;
    } catch (_) { return null; }
  }

  async function request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (state.token) headers.Authorization = `Bearer ${state.token}`;
    if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    const response = await fetch(path, { ...options, headers });
    const raw = await response.text();
    let body = null;
    try { body = raw ? JSON.parse(raw) : null; } catch (_) { body = raw; }
    if (response.status === 401) {
      signOut();
      throw new Error('Your session expired. Sign in again to continue.');
    }
    if (!response.ok) {
      const message = body && typeof body === 'object' ? (body.message || body.error || body.detail) : body;
      throw new Error(message || `Request failed (${response.status})`);
    }
    return { body, response };
  }

  function showToast(message, error = false) {
    const toast = $('toast');
    toast.textContent = message;
    toast.classList.toggle('error', error);
    toast.classList.remove('hidden');
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => toast.classList.add('hidden'), 4200);
  }

  function setBusy(button, busy, label) {
    if (!button) return;
    button.disabled = busy;
    if (busy) { button.dataset.original = button.innerHTML; button.textContent = label || 'Working…'; }
    else if (button.dataset.original) { button.innerHTML = button.dataset.original; delete button.dataset.original; }
  }

  function signIn(token, username) {
    const role = decodeRole(token);
    if (!['RECRUITER', 'REVIEWER'].includes(role)) throw new Error('The server returned an invalid role token.');
    state.token = token;
    state.username = username;
    state.role = role;
    $('loginScreen').classList.add('hidden');
    $('workspace').classList.remove('hidden');
    $('sessionBadge').classList.remove('hidden');
    $('sessionName').textContent = `${username} · ${role === 'RECRUITER' ? 'Recruiter' : 'Reviewer'}`;
    buildNavigation();
    goTo(role === 'RECRUITER' ? 'ask' : 'reviews');
  }

  function signOut() {
    state.token = null;
    state.username = null;
    state.role = null;
    state.lastRunId = null;
    if (state.reviewsTimer) window.clearInterval(state.reviewsTimer);
    state.reviewsTimer = null;
    $('workspace').classList.add('hidden');
    $('sessionBadge').classList.add('hidden');
    $('loginScreen').classList.remove('hidden');
    $('loginForm').reset();
    $('answerPanel').classList.add('hidden');
    $('traceResult').classList.add('hidden');
    $('screeningNotice').classList.add('hidden');
    $('loginError').textContent = '';
  }

  function buildNavigation() {
    const items = state.role === 'RECRUITER'
      ? [['ask', 'Knowledge assistant'], ['screening', 'New screening'], ['trace', 'Run trace']]
      : [['reviews', 'Review queue'], ['ask', 'Knowledge assistant'], ['trace', 'Run trace']];
    const nav = $('navigation');
    nav.replaceChildren();
    for (const [key, title] of items) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'nav-item';
      button.dataset.view = key;
      button.innerHTML = `<span class="nav-icon" aria-hidden="true">${icons[key]}</span><span>${title}</span>`;
      button.addEventListener('click', () => goTo(key));
      nav.append(button);
    }
  }

  function goTo(name) {
    if (!views[name]) return;
    state.activeView = name;
    for (const [key, id] of Object.entries(views)) $(id).classList.toggle('hidden', key !== name);
    document.querySelectorAll('.nav-item').forEach((button) => button.classList.toggle('active', button.dataset.view === name));
    if (name === 'reviews') loadReviews();
  }

  async function checkHealth() {
    const stateEl = document.querySelector('.service-state');
    try {
      const response = await fetch('/actuator/health');
      if (!response.ok) throw new Error();
      stateEl.classList.add('online');
      $('serviceText').textContent = 'Service online';
    } catch (_) {
      stateEl.classList.add('offline');
      $('serviceText').textContent = 'Service unavailable';
    }
  }

  $('loginForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = $('loginButton');
    $('loginError').textContent = '';
    setBusy(button, true, 'Signing in…');
    try {
      const { body } = await request('/api/auth/token', {
        method: 'POST',
        body: JSON.stringify({ username: $('username').value.trim(), password: $('password').value })
      });
      signIn(body.accessToken, $('username').value.trim());
    } catch (error) { $('loginError').textContent = error.message; }
    finally { setBusy(button, false); }
  });

  $('logoutButton').addEventListener('click', signOut);

  $('askForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = $('askButton');
    setBusy(button, true, 'Searching the corpus…');
    $('answerPanel').classList.add('hidden');
    try {
      const { body, response } = await request('/api/ask', {
        method: 'POST',
        body: JSON.stringify({ question: $('question').value.trim() })
      });
      $('answerText').textContent = body.answer || 'The API returned an empty answer.';
      const status = $('answerStatus');
      status.textContent = body.refused ? 'Not enough evidence' : 'Grounded response';
      status.classList.toggle('refused', Boolean(body.refused));
      const list = $('citationList');
      list.replaceChildren();
      for (const citation of (body.citations || [])) {
        const item = document.createElement('div');
        item.className = 'citation';
        const title = document.createElement('strong');
        title.textContent = citation.sourceFileName || 'Corpus source';
        const detail = document.createElement('small');
        detail.textContent = `${citation.sectionOrPage || 'Section unavailable'} · ${citation.chunkId || ''}`;
        item.append(title, detail);
        list.append(item);
      }
      const runId = response.headers.get('X-Run-Id');
      $('askRunId').textContent = runId ? `RUN ID  ${runId}` : '';
      $('answerTraceButton').classList.toggle('hidden', !runId);
      $('answerTraceButton').onclick = () => openTrace(runId);
      $('answerPanel').classList.remove('hidden');
    } catch (error) { showToast(error.message, true); }
    finally { setBusy(button, false); }
  });

  $('profileSource').addEventListener('change', () => {
    const fromDocument = $('profileSource').value === 'document';
    $('profileJsonPanel').classList.toggle('hidden', fromDocument);
    $('profileDocumentPanel').classList.toggle('hidden', !fromDocument);
    $('profileJson').required = !fromDocument;
    $('profileFile').required = fromDocument;
  });

  $('profileFile').addEventListener('change', async () => {
    const file = $('profileFile').files[0];
    $('extractedPreview').value = '';
    $('extractStatus').textContent = '';
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) { $('extractStatus').textContent = 'File is larger than 10 MB.'; return; }
    $('extractStatus').textContent = 'Extracting document text…';
    const form = new FormData(); form.append('profileFile', file);
    try {
      const { body } = await request('/api/screenings/extract', { method: 'POST', body: form });
      $('extractedPreview').value = body.extractedText;
      $('extractStatus').textContent = `Extracted from ${body.sourceFile}. Review the text before creating the draft.`;
    } catch (error) { $('extractStatus').textContent = error.message; }
  });

  $('screeningForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = $('screeningButton');
    let profile;
    if ($('profileSource').value === 'document') {
      const extracted = $('extractedPreview').value.trim();
      if (!extracted) { showToast('Choose a readable candidate document first.', true); return; }
      profile = { sourceFile: $('profileFile').files[0]?.name || 'candidate document', extractedText: extracted };
    } else {
      try { profile = JSON.parse($('profileJson').value); }
      catch (_) { showToast('Candidate profile must be valid JSON.', true); return; }
      if (!profile || typeof profile !== 'object' || Array.isArray(profile)) {
        showToast('Candidate profile must be a JSON object.', true);
        return;
      }
    }
    setBusy(button, true, 'Creating draft…');
    const notice = $('screeningNotice');
    notice.classList.remove('hidden', 'error');
    notice.textContent = 'The screening workflow is running. You can follow its progress in Run trace.';
    try {
      const { body } = await request('/api/screenings', {
        method: 'POST',
        body: JSON.stringify({
          name: $('candidateName').value.trim(),
          roleApplied: $('roleApplied').value,
          profileJson: JSON.stringify(profile)
        })
      });
      state.lastRunId = body.runId;
      notice.textContent = `Draft submitted. Run ${body.runId}. A reviewer will see it after the agent workflow finishes.`;
      $('traceRunId').value = body.runId;
      showToast('Screening draft submitted.');
    } catch (error) {
      notice.classList.add('error');
      notice.textContent = error.message;
    } finally { setBusy(button, false); }
  });

  function parseDraft(value) {
    if (typeof value === 'string') {
      try { return JSON.parse(value); } catch (_) { return {}; }
    }
    if (value && typeof value === 'object') {
      // PostgreSQL JSONB can be serialized by Jackson as a PGobject bean instead of JSON text.
      if (value.type === 'jsonb' && typeof value.value === 'string') return parseDraft(value.value);
      if (typeof value.value === 'string' && value.null === false) {
        try { return JSON.parse(value.value); } catch (_) { return value; }
      }
      return value;
    }
    return {};
  }

  function prettyJson(value) {
    if (typeof value === 'string') {
      try { return JSON.stringify(JSON.parse(value), null, 2); } catch (_) { return value; }
    }
    return JSON.stringify(value || {}, null, 2);
  }

  function educationDisplay(value) {
    const text = String(value || 'Not clearly stated').replace(/\s+/g, ' ').trim();
    const degree = text.match(/.{0,140}\b(?:bachelor(?:'s)?|b\.?s\.?c?\.?|b\.?a\.?|b\.?e\.?|b\.?tech|master(?:'s)?|m\.?s\.?c?\.?|m\.?a\.?|m\.?tech|mba|ph\.?d\.?|doctorate|associate(?:'s)?|diploma|high school|secondary school)\b[^,;.!?]*/i);
    return degree ? degree[0].trim() : text;
  }

  function renderReviews(items) {
    const list = $('reviewsList');
    list.replaceChildren();
    $('reviewsEmpty').classList.toggle('hidden', items.length !== 0);
    for (const item of items) {
      const draft = parseDraft(item.decision_draft);
      const card = document.createElement('article');
      card.className = 'review-card';
      const top = document.createElement('div');
      top.className = 'review-card-top';
      const heading = document.createElement('div');
      const title = document.createElement('h3');
      const candidate = draft.candidate || {};
      title.textContent = `${candidate.name || draft.candidateName || 'Candidate'} · ${candidate.roleApplied || draft.roleApplied || draft.role || 'Screening'}`;
      const meta = document.createElement('div');
      meta.className = 'review-meta';
      meta.textContent = `REVIEW ${item.id}  ·  RUN ${item.run_id}`;
      heading.append(title, meta);
      const tag = document.createElement('span');
      tag.className = 'decision-tag';
      tag.textContent = `${draft.decision || 'HOLD'} · ${item.status || 'PENDING'}`;
      top.append(heading, tag);
      const facts = document.createElement('div'); facts.className = 'candidate-facts';
      const match = draft.match || {};
      const experience = candidate.freshmanAssumed
        ? '0 years · freshman assumed (unclear in resume)'
        : `${candidate.yearsExperience ?? 'Not stated'} years`;
      for (const [label, value] of Object.entries({
        Education: educationDisplay(candidate.education),
        Experience: experience,
        'Role match': Number.isInteger(match.percent) ? `${match.percent}%` : 'Not scored'
      })) {
        const fact = document.createElement('div'); fact.className = 'candidate-fact';
        const caption = document.createElement('small'); caption.textContent = label;
        const content = document.createElement('strong'); content.textContent = String(value);
        fact.append(caption, content); facts.append(fact);
      }
      const threshold = document.createElement('p'); threshold.className = `match-threshold ${match.thresholdMet === false ? 'below' : ''}`;
      threshold.textContent = match.interpretation || 'Score is assistive only; every candidate requires human review.';
      const criteria = document.createElement('p'); criteria.className = 'candidate-evidence';
      criteria.textContent = `Criterion scores (0–5): ${Object.entries(match.criterionScores || {}).map(([key, value]) => `${key}: ${value}`).join(' · ') || 'Unavailable'}`;
      const rationale = document.createElement('p');
      rationale.className = 'review-rationale';
      rationale.textContent = draft.rationale || 'No rationale was included in this draft.';
      const flags = document.createElement('div');
      flags.className = 'review-flags';
      for (const flagText of (draft.flags || [])) {
        const flag = document.createElement('span');
        flag.className = 'flag';
        flag.textContent = flagText;
        flags.append(flag);
      }
      const actions = document.createElement('div');
      actions.className = 'review-actions';
      actions.append(
        makeButton('Approve & execute', 'button small approve', () => decide(item, 'approve')),
        makeButton('Edit & approve', 'button small edit', () => toggleEdit(card, item, draft)),
        makeButton('Reject draft', 'button small reject', () => decide(item, 'reject')),
        makeButton('View trace', 'text-button', () => openTrace(item.run_id))
      );
      card.append(top, facts, threshold, criteria);
      if (draft.evidenceSummary) { const evidence=document.createElement('p'); evidence.className='candidate-evidence'; evidence.textContent=`Resume evidence: ${draft.evidenceSummary}`; card.append(evidence); }
      card.append(rationale);
      if (Array.isArray(draft.citations) && draft.citations.length) {
        const details=document.createElement('details'); details.className='review-citations';
        const summary=document.createElement('summary'); summary.textContent=`Supporting rubric and policy citations (${draft.citations.length})`;
        const citeList=document.createElement('ul');
        for (const cite of draft.citations) { const li=document.createElement('li'); li.textContent=cite; citeList.append(li); }
        details.append(summary,citeList); card.append(details);
      }
      card.append(flags, actions);
      list.append(card);
    }
  }

  function makeButton(label, className, action) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = className;
    button.textContent = label;
    button.addEventListener('click', action);
    return button;
  }

  function toggleEdit(card, item, draft) {
    const existing = card.querySelector('.edit-fields');
    if (existing) { existing.remove(); return; }
    const editor = document.createElement('div');
    editor.className = 'edit-fields';
    const decision = document.createElement('select');
    decision.setAttribute('aria-label', 'Edit decision');
    for (const value of ['ADVANCE', 'HOLD', 'DECLINE']) {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      option.selected = (draft.decision || 'HOLD') === value;
      decision.append(option);
    }
    const rationale = document.createElement('textarea');
    rationale.setAttribute('aria-label', 'Edit rationale');
    rationale.value = draft.rationale || '';
    rationale.maxLength = 5000;
    const reason = document.createElement('input');
    reason.placeholder = 'Reason for edit (recorded in audit)';
    reason.maxLength = 1000;
    const save = makeButton('Edit, approve & execute', 'button small primary', async () => {
      const edited = { ...draft, decision: decision.value, rationale: rationale.value.trim() };
      if (!edited.rationale) { showToast('Add a rationale before saving.', true); return; }
      try {
        await request(`/api/reviews/${item.id}/edit-approve`, {
          method: 'POST',
          body: JSON.stringify({ editedPayload: JSON.stringify(edited), reason: reason.value.trim() })
        });
        await request(`/api/reviews/${item.id}/execute`, { method: 'POST' });
        showToast('Edit approved and decision executed.');
        loadReviews();
      } catch (error) { showToast(error.message, true); }
    });
    const label = document.createElement('label');
    label.className = 'edit-reason';
    label.append(document.createTextNode('Audit reason'), reason);
    const commit = document.createElement('div');
    commit.append(label, save);
    editor.append(decision, rationale, commit);
    card.append(editor);
  }

  async function decide(item, action) {
    if (action === 'reject') {
      const reason = window.prompt('Reason for rejecting this draft:');
      if (reason === null) return;
      try {
        await request(`/api/reviews/${item.id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) });
        showToast('Draft rejected and recorded.');
        loadReviews();
      } catch (error) { showToast(error.message, true); }
      return;
    }
    if (!window.confirm('Approve this draft and execute the screening decision? This will record your approval before execution.')) return;
    try {
      await request(`/api/reviews/${item.id}/approve`, { method: 'POST' });
      await request(`/api/reviews/${item.id}/execute`, { method: 'POST' });
      showToast('Approved decision executed.');
      loadReviews();
    } catch (error) { showToast(error.message, true); }
  }

  async function loadReviews() {
    if (state.role !== 'REVIEWER') return;
    try {
      const { body } = await request('/api/reviews');
      renderReviews(Array.isArray(body) ? body : []);
    } catch (error) { showToast(error.message, true); }
  }

  $('refreshReviews').addEventListener('click', loadReviews);

  function openTrace(runId) {
    $('traceRunId').value = runId || '';
    goTo('trace');
    if (runId) loadTrace();
  }

  $('loadTrace').addEventListener('click', loadTrace);
  $('traceRunId').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') { event.preventDefault(); loadTrace(); }
  });

  async function loadTrace() {
    const runId = $('traceRunId').value.trim();
    if (!runId) { showToast('Enter a run ID first.', true); return; }
    try {
      const { body } = await request(`/api/runs/${encodeURIComponent(runId)}/trace`);
      renderTrace(body);
    } catch (error) { showToast(error.message, true); }
  }

  function renderTrace(trace) {
    const result = $('traceResult');
    result.replaceChildren();
    const summary = document.createElement('div');
    summary.className = 'panel trace-summary';
    const title = document.createElement('h3');
    title.textContent = 'Run overview';
    const grid = document.createElement('div');
    grid.className = 'trace-grid';
    for (const [key, value] of Object.entries({
      'Run ID': trace.id,
      'State': trace.current_state || 'Unavailable',
      'Outcome': trace.outcome || 'Pending',
      'Started': trace.started_at || 'Unavailable',
      'Completed': trace.completed_at || 'Pending',
      'Candidate ID': trace.candidate_id || 'Unavailable'
    })) {
      const cell = document.createElement('div');
      cell.className = 'trace-cell';
      const small = document.createElement('small'); small.textContent = key;
      const strong = document.createElement('strong'); strong.textContent = String(value);
      cell.append(small, strong); grid.append(cell);
    }
    summary.append(title, grid); result.append(summary);

    const latestReview = (trace.review || [])[0];
    if (latestReview) {
      const draft = parseDraft(latestReview.edited_payload || latestReview.decision_draft);
      const candidate = draft.candidate || {};
      const match = draft.match || {};
      const candidatePanel = document.createElement('div'); candidatePanel.className = 'panel trace-summary';
      const candidateTitle = document.createElement('h3'); candidateTitle.textContent = `${candidate.name || 'Candidate'} · ${candidate.roleApplied || 'Profile summary'}`;
      const candidateGrid = document.createElement('div'); candidateGrid.className = 'candidate-facts';
      for (const [label,value] of Object.entries({
        Education: educationDisplay(candidate.education),
        Experience: candidate.freshmanAssumed ? '0 years · freshman assumed' : `${candidate.yearsExperience ?? 'Not stated'} years`,
        'Role match': Number.isInteger(match.percent) ? `${match.percent}%` : 'Not scored',
        'Review threshold': match.interpretation || 'Human review required'
      })) {
        const fact=document.createElement('div'); fact.className='candidate-fact';
        const caption=document.createElement('small'); caption.textContent=label;
        const content=document.createElement('strong'); content.textContent=String(value);
        fact.append(caption,content); candidateGrid.append(fact);
      }
      candidatePanel.append(candidateTitle,candidateGrid); result.append(candidatePanel);
    }

    const steps = document.createElement('div');
    steps.className = 'panel trace-section';
    const stepsTitle = document.createElement('h3'); stepsTitle.textContent = `Agent steps (${(trace.steps || []).length})`;
    steps.append(stepsTitle);
    for (const step of (trace.steps || [])) {
      const row = document.createElement('div'); row.className = 'trace-step';
      const name = document.createElement('strong'); name.textContent = step.agent_name || 'Agent';
      const status = document.createElement('span'); status.textContent = step.status || '';
      const detail = document.createElement('div');
      detail.className = 'trace-json';
      detail.textContent = step.error || prettyJson(step.output_json || step.input_json || {});
      row.append(name, status, detail); steps.append(row);
    }
    result.append(steps);

    const usage = document.createElement('div'); usage.className = 'panel trace-section';
    const usageTitle = document.createElement('h3'); usageTitle.textContent = `Token usage (${(trace.tokenUsage || []).length} rows)`;
    usage.append(usageTitle);
    const usageRows = trace.tokenUsage || [];
    if (!usageRows.length) {
      const empty = document.createElement('div'); empty.className = 'muted'; empty.textContent = 'No token usage rows are linked to this run yet.'; usage.append(empty);
    }
    for (const row of usageRows) {
      const line = document.createElement('div'); line.className = 'trace-step';
      const who = document.createElement('strong'); who.textContent = row.agent_name || row.source || 'Model';
      const counts = document.createElement('span'); counts.textContent = `${row.prompt_tokens || 0} in / ${row.completion_tokens || 0} out`;
      const model = document.createElement('div'); model.className = 'trace-json';
      model.textContent = `${row.model || ''} · estimated $${Number(row.estimated_cost_usd || 0).toFixed(6)}`;
      line.append(who, counts, model); usage.append(line);
    }
    result.append(usage);

    const review = document.createElement('div'); review.className = 'panel trace-section';
    const reviewTitle = document.createElement('h3'); reviewTitle.textContent = 'Review history'; review.append(reviewTitle);
    const reviewData = trace.review || [];
    if (!reviewData.length) {
      const empty = document.createElement('div'); empty.className = 'muted'; empty.textContent = 'No review item is available for this run yet.'; review.append(empty);
    }
    for (const row of reviewData) {
      const line = document.createElement('div'); line.className = 'trace-step';
      const status = document.createElement('strong'); status.textContent = row.status || 'PENDING';
      const when = document.createElement('span'); when.textContent = row.decided_at || 'Not decided';
      const draft = document.createElement('div'); draft.className = 'trace-json'; draft.textContent = prettyJson(row.edited_payload || row.decision_draft || {});
      line.append(status, when, draft); review.append(line);
    }
    result.append(review);
    result.classList.remove('hidden');
  }

  checkHealth();
  window.setInterval(checkHealth, 30000);
})();
