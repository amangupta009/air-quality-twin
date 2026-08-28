package com.capstone.airquality.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple login/logout for role-based access. Returns a bearer token the
 * client sends in the Authorization header on subsequent calls.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", request.username(),
                "role", authService.roleForToken(token).name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body) {
        String token = body != null ? body.get("token") : null;
        if (token != null) authService.logout(token);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
