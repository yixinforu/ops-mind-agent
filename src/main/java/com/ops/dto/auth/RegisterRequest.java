package com.ops.dto.auth;

import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String confirmPassword;
    private String captchaId;
    private String captchaAnswer;
}
