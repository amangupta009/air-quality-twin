package com.capstone.airquality.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces role-based access on /api/* routes.
 *
 * Public routes (whitelist): /api/auth/login, /api/rooms (read-only for any
 * authenticated user), /actuator/* (health/metrics operational evidence).
 * Mutation actions (ventilation, occupancy, rename, calibration, settings)
 * require at least FACILITY_MANAGER; calibration/settings/admin require ADMIN.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Whitelist: login always public
        if (path.equals("/api/auth/login")) return true;

        // Public metadata (e.g. room list on the login screen)
        if (path.startsWith("/api/meta")) return true;

        // Extract bearer token
        String auth = request.getHeader("Authorization");
        String token = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }

        // Determine required role
        AuthService.Role required = requiredRole(path, method);

        if (required == null) {
            // Path not under /api -> allow (e.g. static, websocket handshake)
            return true;
        }

        if (token == null || !authService.hasRole(token, required)) {
            response.setStatus(required == null ? 401 : 403);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return false;
        }
        return true;
    }

    private AuthService.Role requiredRole(String path, String method) {
        // Everything under /api is protected by default
        if (!path.startsWith("/api")) return null;

        // Read-only GET /api/rooms* -> any authenticated viewer
        if (path.startsWith("/api/rooms") && "GET".equalsIgnoreCase(method)) {
            return AuthService.Role.VIEWER;
        }

        // Auth logout
        if (path.equals("/api/auth/logout")) return AuthService.Role.VIEWER;

        // Settings (thresholds) -> ADMIN
        if (path.startsWith("/api/settings")) return AuthService.Role.ADMIN;

        // Calibration -> ADMIN
        if (path.startsWith("/api/calibration")) return AuthService.Role.ADMIN;

        // Summary -> any viewer (read-only)
        if (path.startsWith("/api/summary")) return AuthService.Role.VIEWER;

        // Replay -> viewer (read-only)
        if (path.startsWith("/api/replay")) return AuthService.Role.VIEWER;

        // Simulation -> facility manager
        if (path.startsWith("/api/simulate")) return AuthService.Role.FACILITY_MANAGER;

        // Room entry (audit) -> any authenticated user (viewer and up)
        if (method.equalsIgnoreCase("POST") && path.matches("/api/rooms/[^/]+/enter"))
            return AuthService.Role.VIEWER;

        // Room creation (POST /api/rooms) -> ADMIN only
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/rooms"))
            return AuthService.Role.ADMIN;

        // Any other /api mutation (ventilation, occupancy, rename) -> FACILITY_MANAGER
        if (path.startsWith("/api/rooms")) return AuthService.Role.FACILITY_MANAGER;

        return AuthService.Role.VIEWER;
    }
}
