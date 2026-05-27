package com.example.mallcs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（JJWT 0.12.x API）。
 *
 * <p>payload 包含：sub=userId, username, role
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    private SecretKey getKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 生成 JWT Token */
    public String generateToken(String userId, String username, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    /** 解析 Token，异常则抛出 */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserId(String token)   { return parseToken(token).getSubject(); }
    public String getUsername(String token) { return parseToken(token).get("username", String.class); }
    public String getRole(String token)     { return parseToken(token).get("role", String.class); }

    public boolean isValid(String token) {
        try { parseToken(token); return true; }
        catch (Exception e) { return false; }
    }
}
