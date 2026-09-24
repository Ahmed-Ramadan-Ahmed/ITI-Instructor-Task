package org.hrcopilot.security;

import org.springframework.boot.CommandLineRunner;
import org.hrcopilot.persistence.entity.UserEntity;
import org.hrcopilot.persistence.repository.UserRepository;
import org.hrcopilot.model.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoUserSeeder {
    @Bean CommandLineRunner seedUsers(UserRepository users, PasswordEncoder encoder,
            @Value("${app.seed.recruiter-password:RecruiterDemo!23}") String recruiterPassword,
            @Value("${app.seed.reviewer-password:ReviewerDemo!23}") String reviewerPassword) {
        return args -> {
            seedIfMissing(users, encoder, "recruiter", recruiterPassword, UserRole.RECRUITER);
            seedIfMissing(users, encoder, "reviewer", reviewerPassword, UserRole.REVIEWER);
        };
    }

    private void seedIfMissing(UserRepository users, PasswordEncoder encoder,
                               String username, String password, UserRole role) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(new UserEntity(username, encoder.encode(password), role));
        }
    }
}
