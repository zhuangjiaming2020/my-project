package com.example.mallcs.controller;

import com.example.mallcs.entity.AppUser;
import com.example.mallcs.security.AesTokenUtil;
import com.example.mallcs.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口。
 *
 * <ul>
 *   <li>POST /api/auth/login    - 用户登录，返回 AES Token</li>
 *   <li>POST /api/auth/register - 注册新用户</li>
 *   <li>GET  /api/auth/me       - 获取当前登录用户信息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final UserService userService;
    private final AesTokenUtil tokenUtil;

    public AuthController(AuthenticationManager authManager,
                          UserService userService,
                          AesTokenUtil tokenUtil) {
        this.authManager = authManager;
        this.userService = userService;
        this.tokenUtil   = tokenUtil;
    }

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "认证失败：" + e.getMessage()));
        }

        AppUser user = (AppUser) userService.loadUserByUsername(req.username());
        String token = tokenUtil.generateToken(String.valueOf(user.getId()), user.getUsername(), user.getRole());

        return ResponseEntity.ok(new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole()
        ));
    }

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            AppUser user = userService.register(req.username(), req.password(), req.nickname(), "USER");
            String token = tokenUtil.generateToken(String.valueOf(user.getId()), user.getUsername(), user.getRole());
            return ResponseEntity.ok(new LoginResponse(
                    token, user.getId(), user.getUsername(), user.getNickname(), user.getRole()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // 当前用户信息
    // ------------------------------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        String userId   = (String) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        return ResponseEntity.ok(Map.of(
                "userId",   userId,
                "username", username
        ));
    }

    // ------------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------------

    public record LoginRequest(String username, String password) {}

    public record RegisterRequest(String username, String password, String nickname) {}

    public record LoginResponse(
            String token,
            String userId,
            String username,
            String nickname,
            String role
    ) {}
}
