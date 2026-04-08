package org.example.dto.auth;

import lombok.Data;

/**
 * 验证码响应
 */
@Data
public class CaptchaResponse {
    private String captchaId;
    private String question;
}
