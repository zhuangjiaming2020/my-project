package com.example.mallcs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * AES Token 认证过滤器 —— 解析请求头中的 Bearer Token（自研 AES-256/CBC 加密），
 * 验证通过后设置 Spring Security 上下文。
 *
 * <p>同时将 userId 存入 request attribute，供 Controller 直接使用。
 *
 * @see AesTokenUtil
 */
@Component
public class AesAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AesAuthFilter.class);

    private final AesTokenUtil tokenUtil;

    public AesAuthFilter(AesTokenUtil tokenUtil) {
        this.tokenUtil = tokenUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && tokenUtil.isValid(token)) {
            try {
                String userId   = tokenUtil.getUserId(token);
                String username = tokenUtil.getUsername(token);
                String role     = tokenUtil.getRole(token);

                request.setAttribute("userId",   userId);
                request.setAttribute("username", username);

                var auth = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                log.warn("[AesToken] 解析失败: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
