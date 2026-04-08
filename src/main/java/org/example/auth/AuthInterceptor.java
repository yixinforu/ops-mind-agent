package org.example.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.common.ApiResponse;
import org.example.service.auth.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * API 鉴权拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractBearerToken(request);
        if (token == null) {
            writeUnauthorized(response);
            return false;
        }

        Optional<TokenPayload> payloadOptional = jwtService.parseToken(token);
        if (payloadOptional.isEmpty()) {
            writeUnauthorized(response);
            return false;
        }

        TokenPayload payload = payloadOptional.get();
        if (authService.isTokenBlacklisted(payload.getJti())) {
            writeUnauthorized(response);
            return false;
        }

        AuthUser authUser = new AuthUser();
        authUser.setUserId(payload.getUserId());
        authUser.setUsername(payload.getUsername());
        authUser.setJti(payload.getJti());
        AuthContext.setCurrentUser(authUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (!authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Object> apiResponse = ApiResponse.error("未登录或登录已失效");
        apiResponse.setCode(401);
        objectMapper.writeValue(response.getWriter(), apiResponse);
    }
}
