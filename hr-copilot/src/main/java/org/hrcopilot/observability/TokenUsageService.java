package org.hrcopilot.observability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.hrcopilot.config.ModelPricing;
import org.hrcopilot.config.PricingProperties;
import org.hrcopilot.persistence.entity.TokenUsageEntity;
import org.hrcopilot.persistence.repository.TokenUsageRepository;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class TokenUsageService {
    private final TokenUsageRepository usageRepository;
    private final PricingProperties pricing;
    private final String chatModel;

    public TokenUsageService(TokenUsageRepository usageRepository, PricingProperties pricing,
                             Environment environment) {
        this.usageRepository = usageRepository;
        this.pricing = pricing;
        this.chatModel = environment.getProperty("spring.ai.google.genai.chat.model", "gemini-3.5-flash-lite");
    }

    public String chatModel() {
        return chatModel;
    }

    public void record(String source, String kind, String model, int prompt, int completion, String agent) {
        ModelPricing modelPricing = pricing.forModel(model);
        BigDecimal cost = BigDecimal.valueOf(prompt * modelPricing.getPrompt()
                        + completion * modelPricing.getCompletion())
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        String runId = MDC.get("runId");
        usageRepository.save(new TokenUsageEntity(runId == null ? null : UUID.fromString(runId),
                source, kind, agent, model, prompt, completion, cost));
    }

    public static int roughTokens(String text) {
        return Math.max(1, (text == null ? 0 : text.length()) / 4);
    }
}
