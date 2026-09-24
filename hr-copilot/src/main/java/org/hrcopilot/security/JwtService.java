package org.hrcopilot.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${app.jwt-secret:local-development-secret-change-before-deploy-123456}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(username).claim("role", role).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600))).signWith(key).compact();
    }

    public io.jsonwebtoken.Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
