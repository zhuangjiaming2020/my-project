package com.example.mallcs.service;

import com.example.mallcs.entity.AppUser;
import com.example.mallcs.mapper.UserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户服务 —— 实现 Spring Security 的 {@link UserDetailsService}。
 */
@Service
public class UserService implements UserDetailsService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper      = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    /** 注册新用户（密码自动 BCrypt 加密） */
    public AppUser register(String username, String password, String nickname, String role) {
        if (userMapper.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .password(passwordEncoder.encode(password))
                .nickname(nickname != null ? nickname : username)
                .role(role != null ? role.toUpperCase() : "USER")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        return user;
    }

    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }
}
