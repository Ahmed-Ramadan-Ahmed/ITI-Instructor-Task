package org.hrcopilot.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hrcopilot.service.AuthenticationService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    @PostMapping("/token") public TokenResponse token(@Valid @RequestBody LoginRequest request) {
        return new TokenResponse(
                authenticationService.issueToken(
                        request.username(), request.password()
                ), "Bearer", 3600);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
