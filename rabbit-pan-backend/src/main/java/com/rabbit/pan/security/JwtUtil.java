package com.rabbit.pan.security;

import com.rabbit.pan.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：accessToken + refreshToken 双令牌签发与解析
 */
@Slf4j
@Component
public class JwtUtil {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpireMillis;
    private final long refreshExpireMillis;

    public JwtUtil(AppProperties properties) {
        AppProperties.Jwt jwt = properties.getJwt();
        this.key = Keys.hmacShaKeyFor(jwt.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessExpireMillis = jwt.getAccessExpireHours() * 3600_000L;
        this.refreshExpireMillis = jwt.getRefreshExpireDays() * 86_400_000L;
    }

    /** 签发 accessToken */
    public String createAccessToken(Long userId, String username, String role) {
        return build(userId, username, role, TOKEN_TYPE_ACCESS, accessExpireMillis);
    }

    /** 签发 refreshToken */
    public String createRefreshToken(Long userId, String username, String role) {
        return build(userId, username, role, TOKEN_TYPE_REFRESH, refreshExpireMillis);
    }

    private String build(Long userId, String username, String role, String type, long expireMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名，失败抛出 JwtException */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class));
    }

    public long getRefreshExpireMillis() {
        return refreshExpireMillis;
    }

    public long getAccessExpireMillis() {
        return accessExpireMillis;
    }
}
