package com.example.mallcs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 商城智能客服启动类
 *
 * <p>技术栈：Java 17 + Spring Boot 3.5 + Spring AI Alibaba + DeepSeek + MyBatis + PostgreSQL
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.example.mallcs.mapper")
public class MallCustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallCustomerServiceApplication.class, args);
    }
}
