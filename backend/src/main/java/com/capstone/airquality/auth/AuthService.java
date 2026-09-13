package com.capstone.airquality.auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * User store and stateless bearer-token manager for role-based access.
 *
 * Tokens are HMAC-signed with expiry so they stay valid across backend
 * restarts (this was the original pain point: in-memory sessions died with
 * every restart and the dashboard started returning 401). Logout revokes a
 * token for the lifetime of the running process via a revocation set.
 *
 * The signing secret is persisted under ~/aq-runtime/.auth-secret so a
 * backend restart re-issues equally valid tokens (the frontend is not forced
 * to re-login). Credentials and roles are seeded in memory, isolated to this
 * class so swapping to a real user table later changes no other code.
 */
@Component
public class AuthService {

    public enum Role { VIEWER, FACILITY_MANAGER, ADMIN }

    /** Token time-to-live: 24 hours. */
    private static final long TTL_SECONDS = 24L * 60 * 60;

    private static final String SECRET_FILE =
            System.getProperty("user.home") + "/aq-runtime/.auth-secret";

    /** username -> (password hash, role) */
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    /** Revoked tokens (logout), cleared on restart by design. */
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    private final byte[] secret;

    public record UserAccount(String username, String passwordHash, Role role) {}

    public AuthService() {
        this.secret = signingSecret();
        seed();
    }

    private void seed() {
        // default accounts - change/hide via environment in production
        addUser("viewer", "viewer123", Role.VIEWER);
        addUser("manager", "manager123", Role.FACILITY_MANAGER);
        addUser("admin", "admin123", Role.ADMIN);
    }

    public void addUser(String username, String plainPassword, Role role) {
        users.put(username, new UserAccount(username, hash(plainPassword), role));
    }

    /** Login: returns a signed bearer token if credentials are valid, else null. */
    public String login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        UserAccount account = users.get(username);
        if (account == null || !account.passwordHash().equals(hash(password))) {
            return null;
        }
        long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((username + "." + expiresAt).getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            revoked.add(token);
        }
    }

    /** Resolve the role for a given bearer token, or null if invalid/expired/revoked. */
    public Role roleForToken(String token) {
        if (token == null || token.isEmpty() || revoked.contains(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int dot = decoded.indexOf('.');
        if (dot < 0) return null;
        String username = decoded.substring(0, dot);
        long expiresAt = Long.parseLong(decoded.substring(dot + 1));
        if (Instant.now().getEpochSecond() > expiresAt) {
            return null;
        }
        UserAccount account = users.get(username);
        return account != null ? account.role() : null;
    }

    public boolean hasRole(String token, Role required) {
        Role role = roleForToken(token);
        if (role == null) return false;
        // Admin can do anything; FACILITY_MANAGER can do VIEWER things but not ADMIN.
        return switch (required) {
            case ADMIN -> role == Role.ADMIN;
            case FACILITY_MANAGER -> role == Role.ADMIN || role == Role.FACILITY_MANAGER;
            case VIEWER -> true; // every authenticated user is at least a viewer
        };
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    /**
     * Load or create the signing secret. Persisted under the aq-runtime dir so
     * restarts keep issuing tokens that the frontend already holds.
     */
    private static byte[] signingSecret() {
        String envSecret = System.getenv("AUTH_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret.getBytes(StandardCharsets.UTF_8);
        }
        try {
            Path file = Path.of(SECRET_FILE);
            if (Files.exists(file)) {
                return Files.readString(file).trim().getBytes(StandardCharsets.UTF_8);
            }
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            String hex = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            Files.createDirectories(file.getParent());
            Files.writeString(file, hex);
            return hex.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize auth secret", e);
        }
    }

    /** Simple salted hash (no plain-text passwords in memory). */
    private static String hash(String pw) {
        String salted = "aqt-salt::" + pw;
        StringBuilder sb = new StringBuilder();
        int hash = 0x811c9dc5;
        for (int i = 0; i < salted.length(); i++) {
            hash ^= salted.charAt(i);
            hash *= 0x01000193;
        }
        sb.append(Integer.toHexString(hash));
        sb.append(Integer.toHexString(salted.hashCode()));
        return sb.toString();
    }
}