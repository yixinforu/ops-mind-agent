package com.ops.auth;

import lombok.Data;

/**
 * JWT 解析后的核心字段
 */
@Data
public class TokenPayload {
    private Long userId;
    private String username;
    private String jti;
    private long expirationTimeMillis;
}
