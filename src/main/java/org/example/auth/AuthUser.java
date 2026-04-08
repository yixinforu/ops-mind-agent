package org.example.auth;

import lombok.Data;

/**
 * 当前登录用户上下文
 */
@Data
public class AuthUser {
    private Long userId;
    private String username;
    private String jti;
}
