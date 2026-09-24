package org.hrcopilot.service;

import org.hrcopilot.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.hrcopilot.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwt;

    public String issueToken(String username, String password) {

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));

        String role = users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated account was not found"))
                .getRole().name();

        return jwt.issue(username, role);
    }
}
