package org.example.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT 生成与校验服务
 */
@Service
public class JwtService {

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @Value("${auth.jwt.expire-minutes:1440}")
    private long expireMinutes;

    public String createToken(Long userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + Math.max(1L, expireMinutes) * 60_000L);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Optional<TokenPayload> parseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            TokenPayload payload = new TokenPayload();
            payload.setJti(claims.getId());
            payload.setUsername(String.valueOf(claims.get("username")));
            payload.setUserId(Long.valueOf(claims.getSubject()));
            payload.setExpirationTimeMillis(claims.getExpiration() == null ? 0L : claims.getExpiration().getTime());
            return Optional.of(payload);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getRemainingSeconds(String token) {
        Optional<TokenPayload> payloadOptional = parseToken(token);
        if (payloadOptional.isEmpty()) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        long expiration = payloadOptional.get().getExpirationTimeMillis();
        if (expiration <= now) {
            return 0L;
        }
        return Math.max(1L, (expiration - now) / 1000L);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
