package com.example.mallcs.entity;

import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 系统用户（同时实现 Spring Security 的 UserDetails）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser implements UserDetails {

    private String id;
    private String username;
    private String password;
    private String nickname;
    /** USER 或 ADMIN */
    private String role;
    @Builder.Default
    private boolean enabled = true;
    private LocalDateTime createdAt;

    // ── UserDetails ──────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
