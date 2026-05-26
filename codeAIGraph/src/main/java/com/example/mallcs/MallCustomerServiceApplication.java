package com.example.mallcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商城智能客服启动类
 *
 * <p>技术栈：Java 17 + Spring Boot 3.5 + Spring AI Alibaba 1.1.2.0 + DeepSeek
 *
 * <p>运行前请设置环境变量：
 * <pre>
 *   DEEPSEEK_API_KEY=sk-xxxx
 * </pre>
 *
 * <p>或在 application.yml 中直接填写 api-key（不推荐提交到 Git）
 */
@SpringBootApplication
public class MallCustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallCustomerServiceApplication.class, args);
    }
}
