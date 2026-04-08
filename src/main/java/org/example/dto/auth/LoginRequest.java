package org.example.dto.auth;

import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
    private String captchaId;
    private String captchaAnswer;
}
