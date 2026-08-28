package com.capstone.airquality.auth;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory user store and session-token manager for role-based access.
 *
 * For an industry prototype this is a pragmatic choice: credentials and roles
 * are seeded and simple bearer tokens are issued. A production build would
 * back this with a user table + JWT; the API contract (Authorization header,
 * roles) stays the same so swapping is isolated to this class.
 *
 * Passwords are stored as salted hashes (hashPassword) - never plain text.
 */
@Component
public class AuthService {

    public enum Role { VIEWER, FACILITY_MANAGER, ADMIN }

    /** username -> (password hash, role) */
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    /** token -> username */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public record UserAccount(String username, String passwordHash, Role role) {}

    public AuthService() {
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

    /** Login: returns a bearer token if credentials are valid, else null. */
    public String login(String username, String password) {
        UserAccount account = users.get(username);
        if (account == null || !account.passwordHash().equals(hash(password))) {
            return null;
        }
        String token = java.util.UUID.randomUUID().toString().replace("-", "") + System.nanoTime();
        sessions.put(token, username);
        return token;
    }

    public void logout(String token) {
        sessions.remove(token);
    }

    /** Resolve the role for a given bearer token, or null if invalid. */
    public Role roleForToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String username = sessions.get(token);
        if (username == null) return null;
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
