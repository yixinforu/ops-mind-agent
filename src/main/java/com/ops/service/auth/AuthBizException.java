package com.ops.service.auth;

import lombok.Getter;

/**
 * 认证业务异常
 */
@Getter
public class AuthBizException extends RuntimeException {

    private final Integer remainingAttempts;
    private final Long retryAfterSeconds;

    public AuthBizException(String message) {
        super(message);
        this.remainingAttempts = null;
        this.retryAfterSeconds = null;
    }

    public AuthBizException(String message, Integer remainingAttempts, Long retryAfterSeconds) {
        super(message);
        this.remainingAttempts = remainingAttempts;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
