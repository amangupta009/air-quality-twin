package com.capstone.airquality.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the role-based AuthService: login success/failure,
 * role hierarchy, and token lifecycle.
 */
class AuthServiceTest {

    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(); // seeds admin/manager/viewer
    }

    @Test
    void validLoginReturnsToken() {
        String token = auth.login("admin", "admin123");
        assertNotNull(token);
        assertEquals(AuthService.Role.ADMIN, auth.roleForToken(token));
    }

    @Test
    void wrongPasswordReturnsNull() {
        assertNull(auth.login("admin", "wrong"));
    }

    @Test
    void unknownUserReturnsNull() {
        assertNull(auth.login("ghost", "whatever"));
    }

    @Test
    void adminCanDoEverything() {
        String token = auth.login("admin", "admin123");
        assertTrue(auth.hasRole(token, AuthService.Role.ADMIN));
        assertTrue(auth.hasRole(token, AuthService.Role.FACILITY_MANAGER));
        assertTrue(auth.hasRole(token, AuthService.Role.VIEWER));
    }

    @Test
    void managerCannotDoAdminThings() {
        String token = auth.login("manager", "manager123");
        assertFalse(auth.hasRole(token, AuthService.Role.ADMIN));
        assertTrue(auth.hasRole(token, AuthService.Role.FACILITY_MANAGER));
        assertTrue(auth.hasRole(token, AuthService.Role.VIEWER));
    }

    @Test
    void viewerOnlyReads() {
        String token = auth.login("viewer", "viewer123");
        assertTrue(auth.hasRole(token, AuthService.Role.VIEWER));
        assertFalse(auth.hasRole(token, AuthService.Role.FACILITY_MANAGER));
        assertFalse(auth.hasRole(token, AuthService.Role.ADMIN));
    }

    @Test
    void logoutInvalidatesToken() {
        String token = auth.login("viewer", "viewer123");
        assertNotNull(auth.roleForToken(token));
        auth.logout(token);
        assertNull(auth.roleForToken(token));
    }

    @Test
    void noTokenHasNoRole() {
        assertNull(auth.roleForToken(null));
        assertNull(auth.roleForToken(""));
        assertNull(auth.roleForToken("garbage"));
    }

    @Test
    void nullLoginRejectedGracefully() {
        assertNull(auth.login(null, "x"));
    }

    @Test
    void customUserCanBeAdded() {
        auth.addUser("custom", "pass123", AuthService.Role.FACILITY_MANAGER);
        String token = auth.login("custom", "pass123");
        assertNotNull(token);
        assertEquals(AuthService.Role.FACILITY_MANAGER, auth.roleForToken(token));
    }
}
