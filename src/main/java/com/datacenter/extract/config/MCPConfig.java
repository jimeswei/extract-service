package com.datacenter.extract.config;

import com.datacenter.extract.service.TextExtractionService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP配置类 - 按照系统架构设计文档第3.1章实现
 * 
 * 实现MCP SSE标准的核心配置
 * 自动发现所有@Tool方法，无需手动注册
 */
@Configuration
public class MCPConfig {

    /**
     * 工具回调提供者配置
     * 巧妙设计：自动装配处理链，无需手动配置
     */
    @Bean
    public ToolCallbackProvider extractionTools(TextExtractionService textExtractionService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(textExtractionService)
                .build();
    }

}
