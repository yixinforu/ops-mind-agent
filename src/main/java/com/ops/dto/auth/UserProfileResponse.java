package com.ops.dto.auth;

import lombok.Data;

/**
 * 当前登录用户信息
 */
@Data
public class UserProfileResponse {
    private Long userId;
    private String username;
}
