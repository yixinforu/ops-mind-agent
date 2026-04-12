package com.ops.controller;

import jakarta.servlet.http.HttpServletRequest;
import com.ops.auth.AuthContext;
import com.ops.auth.AuthUser;
import com.ops.dto.auth.AuthResponse;
import com.ops.dto.auth.CaptchaResponse;
import com.ops.dto.auth.LoginRequest;
import com.ops.dto.auth.RegisterRequest;
import com.ops.dto.auth.UserProfileResponse;
import com.ops.dto.common.ApiResponse;
import com.ops.service.auth.AuthBizException;
import com.ops.service.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<CaptchaResponse>> captcha() {
        try {
            return ResponseEntity.ok(ApiResponse.success(authService.generateCaptcha()));
        } catch (Exception e) {
            logger.error("生成验证码失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (AuthBizException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("注册失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (AuthBizException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("登录失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request) {
        try {
            String token = extractBearerToken(request);
            authService.logout(token);
            return ResponseEntity.ok(ApiResponse.success("已退出登录"));
        } catch (Exception e) {
            logger.error("退出登录失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me() {
        AuthUser authUser = AuthContext.getCurrentUser();
        if (authUser == null) {
            ApiResponse<UserProfileResponse> error = ApiResponse.error("未登录或登录已失效");
            error.setCode(401);
            return ResponseEntity.status(401).body(error);
        }

        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(authUser.getUserId());
        response.setUsername(authUser.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7).trim();
    }
}
