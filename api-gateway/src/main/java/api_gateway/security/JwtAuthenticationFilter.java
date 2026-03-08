//package api_gateway.security;
//
//
//import org.springframework.http.HttpStatus;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ResponseStatusException;
//import org.springframework.web.servlet.function.HandlerFilterFunction;
//import org.springframework.web.servlet.function.HandlerFunction;
//import org.springframework.web.servlet.function.ServerRequest;
//import org.springframework.web.servlet.function.ServerResponse;
//
//import java.util.List;
//
//
//@Component
//public class JwtAuthenticationFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {
//
//    private final JwtUtil jwtUtil;
//
//    // Paths that don't need a token
//    private static final List<String> PUBLIC_PATHS = List.of(
//            "/api/auth/register",
//            "/api/auth/login",
//            "/api/restaurants/search",
//            "/api/restaurants"
//    );
//
//    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
//        this.jwtUtil = jwtUtil;
//    }
//
//
//    @Override
//    public ServerResponse filter(ServerRequest request,
//                                 HandlerFunction<ServerResponse> next) throws Exception {
//        String path = request.path();
//        String method = request.method().name();
//
//        // Step 1 — check if path is public
//        // Public paths - no token needed
//        if (isPublicPath(path, method)) {
//            return next.handle(request);
//        }
//
//        // Step 2 — get Authorization header
//        String authHeader = request.headers().firstHeader("Authorization");
//
//        // Step 3 — no token, reject
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
//        }
//
//        // Step 4 — validate token
//        String token = authHeader.substring(7);
//        if (!jwtUtil.validateToken(token)) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
//        }
//
//        // Step 5 — extract username and forward as header
////        String username = jwtUtil.extractUsername(token);
////        ServerRequest mutatedRequest = ServerRequest.from(request)
////                .header("X-Authenticated-User", username)
////                .build();
////
////        return next.handle(mutatedRequest);
//        String username = jwtUtil.extractUsername(token);
//        request.attributes().put("X-Authenticated-User", username);
//
//        return next.handle(request);
//    }
//
//    private boolean isPublicPath(String path, String method) {
//        if (path.equals("/api/auth/register") || path.equals("/api/auth/login")) return true;
//        if (path.startsWith("/api/restaurants/search")) return true;
//        // Only GET on /api/restaurants is public (browse), POST/PUT/PATCH require auth
//        if (path.startsWith("/api/restaurants") && method.equals("GET")) return true;
//        return false;
//    }
//
//
//}
//
package api_gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isPublicPath(path, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing token");
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid token");
            return;
        }

        String username = jwtUtil.extractUsername(token);

        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("X-Authenticated-User".equalsIgnoreCase(name)) return username;
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if ("X-Authenticated-User".equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(username));
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                names.add("X-Authenticated-User");
                return Collections.enumeration(names);
            }
        };

        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean isPublicPath(String path, String method) {
        if (path.equals("/api/auth/register") || path.equals("/api/auth/login")) return true;
        if (path.startsWith("/api/restaurants/search")) return true;
        if (path.startsWith("/api/restaurants") && method.equals("GET")) return true;
        return false;
    }
}