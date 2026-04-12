package com.ops.dto.auth;

import lombok.Data;

/**
 * 登录/注册成功响应
 */
@Data
public class AuthResponse {
    private String token;
    private Long userId;
    private String username;
}
