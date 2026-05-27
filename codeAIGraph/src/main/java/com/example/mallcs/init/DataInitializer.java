package com.example.mallcs.init;

import com.example.mallcs.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动初始化器 —— 创建默认用户（BCrypt 动态生成密码，避免 SQL 中硬编码哈希）。
 *
 * <p>默认账号：
 * <ul>
 *   <li>admin / admin123  （管理员）</li>
 *   <li>user1 / user123   （普通用户）</li>
 * </ul>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;

    public DataInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        createUserIfAbsent("admin", "admin123", "系统管理员", "ADMIN");
        createUserIfAbsent("user1", "user123",  "测试用户",   "USER");
        log.info("[DataInit] 默认用户初始化完成");
    }

    private void createUserIfAbsent(String username, String password, String nickname, String role) {
        if (!userService.existsByUsername(username)) {
            userService.register(username, password, nickname, role);
            log.info("[DataInit] 创建用户: username={}, role={}", username, role);
        }
    }
}
