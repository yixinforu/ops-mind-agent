package com.ops.service.auth;

import com.ops.auth.JwtService;
import com.ops.auth.TokenPayload;
import com.ops.dto.auth.AuthResponse;
import com.ops.dto.auth.CaptchaResponse;
import com.ops.dto.auth.LoginRequest;
import com.ops.dto.auth.RegisterRequest;
import com.ops.entity.UserAccount;
import com.ops.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 认证服务（注册/登录/验证码/锁定/退出）
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    private static final String LOGIN_FAIL_KEY_PREFIX = "auth:login:fail:";
    private static final String LOGIN_LOCK_KEY_PREFIX = "auth:login:lock:";
    private static final String JWT_BLACKLIST_KEY_PREFIX = "auth:jwt:blacklist:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${auth.captcha.ttl-minutes:5}")
    private long captchaTtlMinutes;

    @Value("${auth.login.max-retry:5}")
    private int maxRetry;

    @Value("${auth.login.lock-minutes:60}")
    private long lockMinutes;

    private final Map<String, FallbackEntry<String>> fallbackCaptchaStore = new ConcurrentHashMap<>();
    private final Map<String, FallbackEntry<Integer>> fallbackLoginFailStore = new ConcurrentHashMap<>();
    private final Map<String, FallbackEntry<String>> fallbackLoginLockStore = new ConcurrentHashMap<>();
    private final Map<String, FallbackEntry<String>> fallbackJwtBlacklistStore = new ConcurrentHashMap<>();

    public CaptchaResponse generateCaptcha() {
        int left = RANDOM.nextInt(9) + 1;
        int right = RANDOM.nextInt(9) + 1;

        String question;
        int answer;
        if (RANDOM.nextBoolean()) {
            question = left + " + " + right + " = ?";
            answer = left + right;
        } else {
            int max = Math.max(left, right);
            int min = Math.min(left, right);
            question = max + " - " + min + " = ?";
            answer = max - min;
        }

        String captchaId = UUID.randomUUID().toString();
        saveCaptchaAnswer(captchaId, String.valueOf(answer));

        CaptchaResponse response = new CaptchaResponse();
        response.setCaptchaId(captchaId);
        response.setQuestion(question);
        return response;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = safeTrim(request == null ? null : request.getUsername());
        String password = request == null ? "" : safeTrim(request.getPassword());
        String confirmPassword = request == null ? "" : safeTrim(request.getConfirmPassword());

        validateUsername(username);
        validatePassword(password);

        if (!password.equals(confirmPassword)) {
            throw new AuthBizException("两次输入的密码不一致");
        }

        if (!verifyCaptcha(request == null ? null : request.getCaptchaId(), request == null ? null : request.getCaptchaAnswer())) {
            throw new AuthBizException("验证码错误或已过期");
        }

        if (userAccountRepository.countByUsernameExact(username) > 0) {
            throw new AuthBizException("用户名已存在");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(1);
        UserAccount saved = userAccountRepository.save(user);

        logger.info("用户注册成功 - username: {}, userId: {}", saved.getUsername(), saved.getId());
        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String username = safeTrim(request == null ? null : request.getUsername());
        String password = request == null ? "" : safeTrim(request.getPassword());

        validateUsername(username);
        if (password.length() < 1) {
            throw new AuthBizException("密码不能为空");
        }

        long lockRemainSeconds = getLockRemainSeconds(username);
        if (lockRemainSeconds > 0) {
            throw new AuthBizException("账号已锁定，请稍后再试", 0, lockRemainSeconds);
        }

        if (!verifyCaptcha(request == null ? null : request.getCaptchaId(), request == null ? null : request.getCaptchaAnswer())) {
            handleLoginFailure(username, "验证码错误或已过期");
        }

        Optional<UserAccount> userOptional = userAccountRepository.findByUsernameExact(username);
        if (userOptional.isEmpty()) {
            handleLoginFailure(username, "账号或密码错误");
        }

        UserAccount user = userOptional.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleLoginFailure(username, "账号或密码错误");
        }

        clearLoginFailureState(username);
        return buildAuthResponse(user);
    }

    public void logout(String token) {
        Optional<TokenPayload> payloadOptional = jwtService.parseToken(token);
        if (payloadOptional.isEmpty()) {
            return;
        }

        String jti = payloadOptional.get().getJti();
        if (jti == null || jti.isBlank()) {
            return;
        }

        long remainSeconds = jwtService.getRemainingSeconds(token);
        if (remainSeconds <= 0) {
            return;
        }

        addTokenToBlacklist(jti, remainSeconds);
    }

    public boolean isTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }

        String key = buildJwtBlacklistKey(jti);
        if (redisTemplate != null) {
            try {
                Boolean exists = redisTemplate.hasKey(key);
                return Boolean.TRUE.equals(exists);
            } catch (Exception e) {
                logger.warn("Redis 查询 token 黑名单失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        return existsFallbackEntry(fallbackJwtBlacklistStore, key);
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthBizException("用户名不能为空");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new AuthBizException("用户名只能包含英文和数字，且长度不能超过10位");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 5) {
            throw new AuthBizException("密码长度不能低于5位");
        }
    }

    private AuthResponse buildAuthResponse(UserAccount user) {
        String token = jwtService.createToken(user.getId(), user.getUsername());
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        return response;
    }

    private void handleLoginFailure(String username, String baseMessage) {
        int failCount = increaseLoginFailCount(username);
        int remainCount = Math.max(0, maxRetry - failCount);
        if (remainCount <= 0) {
            lockUsername(username);
            clearFailCounter(username);
            throw new AuthBizException("登录失败次数过多，账号已锁定1小时", 0, Math.max(1L, lockMinutes) * 60L);
        }
        throw new AuthBizException(baseMessage + "，剩余重试次数: " + remainCount, remainCount, null);
    }

    private void addTokenToBlacklist(String jti, long remainSeconds) {
        String key = buildJwtBlacklistKey(jti);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, "1", remainSeconds, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                logger.warn("Redis 写入 token 黑名单失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        putFallbackEntry(fallbackJwtBlacklistStore, key, "1", remainSeconds * 1000L);
    }

    private int increaseLoginFailCount(String username) {
        String key = buildLoginFailKey(username);
        if (redisTemplate != null) {
            try {
                Long value = redisTemplate.opsForValue().increment(key);
                if (value != null && value == 1L) {
                    redisTemplate.expire(key, Math.max(1L, lockMinutes), TimeUnit.MINUTES);
                }
                return value == null ? 1 : value.intValue();
            } catch (Exception e) {
                logger.warn("Redis 更新登录失败次数失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        FallbackEntry<Integer> entry = getFallbackEntry(fallbackLoginFailStore, key);
        int current = entry == null ? 0 : entry.getValue();
        int updated = current + 1;
        putFallbackEntry(fallbackLoginFailStore, key, updated, Math.max(1L, lockMinutes) * 60_000L);
        return updated;
    }

    private void lockUsername(String username) {
        String key = buildLoginLockKey(username);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, "1", Math.max(1L, lockMinutes), TimeUnit.MINUTES);
                return;
            } catch (Exception e) {
                logger.warn("Redis 写入锁定状态失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        putFallbackEntry(fallbackLoginLockStore, key, "1", Math.max(1L, lockMinutes) * 60_000L);
    }

    private long getLockRemainSeconds(String username) {
        String key = buildLoginLockKey(username);
        if (redisTemplate != null) {
            try {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl == null || ttl <= 0) {
                    return 0L;
                }
                return ttl;
            } catch (Exception e) {
                logger.warn("Redis 查询锁定状态失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        FallbackEntry<String> entry = getFallbackEntry(fallbackLoginLockStore, key);
        if (entry == null) {
            return 0L;
        }
        long remainMillis = entry.getExpireAtMillis() - System.currentTimeMillis();
        return remainMillis <= 0 ? 0L : Math.max(1L, remainMillis / 1000L);
    }

    private void clearLoginFailureState(String username) {
        String failKey = buildLoginFailKey(username);
        String lockKey = buildLoginLockKey(username);

        if (redisTemplate != null) {
            try {
                redisTemplate.delete(failKey);
                redisTemplate.delete(lockKey);
                return;
            } catch (Exception e) {
                logger.warn("Redis 清理登录失败状态失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        fallbackLoginFailStore.remove(failKey);
        fallbackLoginLockStore.remove(lockKey);
    }

    private void clearFailCounter(String username) {
        String failKey = buildLoginFailKey(username);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(failKey);
                return;
            } catch (Exception e) {
                logger.warn("Redis 清理登录失败计数失败，降级内存模式 - error: {}", e.getMessage());
            }
        }
        fallbackLoginFailStore.remove(failKey);
    }

    private void saveCaptchaAnswer(String captchaId, String answer) {
        String key = buildCaptchaKey(captchaId);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(
                        key,
                        answer,
                        Math.max(1L, captchaTtlMinutes),
                        TimeUnit.MINUTES
                );
                return;
            } catch (Exception e) {
                logger.warn("Redis 保存验证码失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        putFallbackEntry(fallbackCaptchaStore, key, answer, Math.max(1L, captchaTtlMinutes) * 60_000L);
    }

    private boolean verifyCaptcha(String captchaId, String userAnswer) {
        if (captchaId == null || captchaId.isBlank() || userAnswer == null || userAnswer.isBlank()) {
            return false;
        }

        String key = buildCaptchaKey(captchaId);
        String expected;

        if (redisTemplate != null) {
            try {
                expected = redisTemplate.opsForValue().get(key);
                redisTemplate.delete(key);
                return expected != null && expected.equals(userAnswer.trim());
            } catch (Exception e) {
                logger.warn("Redis 校验验证码失败，降级内存模式 - error: {}", e.getMessage());
            }
        }

        FallbackEntry<String> entry = getFallbackEntry(fallbackCaptchaStore, key);
        fallbackCaptchaStore.remove(key);
        return entry != null && entry.getValue().equals(userAnswer.trim());
    }

    private String buildCaptchaKey(String captchaId) {
        return CAPTCHA_KEY_PREFIX + captchaId;
    }

    private String buildLoginFailKey(String username) {
        return LOGIN_FAIL_KEY_PREFIX + username;
    }

    private String buildLoginLockKey(String username) {
        return LOGIN_LOCK_KEY_PREFIX + username;
    }

    private String buildJwtBlacklistKey(String jti) {
        return JWT_BLACKLIST_KEY_PREFIX + jti;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> void putFallbackEntry(Map<String, FallbackEntry<T>> map, String key, T value, long ttlMillis) {
        map.put(key, new FallbackEntry<>(value, System.currentTimeMillis() + Math.max(1000L, ttlMillis)));
    }

    private <T> FallbackEntry<T> getFallbackEntry(Map<String, FallbackEntry<T>> map, String key) {
        FallbackEntry<T> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.getExpireAtMillis() <= System.currentTimeMillis()) {
            map.remove(key);
            return null;
        }
        return entry;
    }

    private <T> boolean existsFallbackEntry(Map<String, FallbackEntry<T>> map, String key) {
        return getFallbackEntry(map, key) != null;
    }

    /**
     * 内存降级场景的带过期数据
     */
    private static class FallbackEntry<T> {
        private final T value;
        private final long expireAtMillis;

        private FallbackEntry(T value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }

        public T getValue() {
            return value;
        }

        public long getExpireAtMillis() {
            return expireAtMillis;
        }
    }
}
