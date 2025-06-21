package com.datacenter.extract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智能文本提取服务 - 主启动类
 * 按照系统架构设计文档实现
 * 
 * @author DataCenter Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class ExtractServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExtractServiceApplication.class, args);
    }
}