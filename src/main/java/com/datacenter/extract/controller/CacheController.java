package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.CacheService;
import com.datacenter.extract.util.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 缓存管理控制器 - v4.0性能优化
 * 
 * 提供缓存统计、管理和监控功能
 */
@RestController
@RequestMapping("/api/v1/cache")
@CrossOrigin(origins = "*")
public class CacheController {

    private static final Logger log = LoggerFactory.getLogger(CacheController.class);

    private final CacheService cacheService;

    @Autowired
    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("🚀 CacheController初始化完成");
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public JSONObject getCacheStats() {
        try {
            Object stats = cacheService.getCacheStats();

            return ResponseBuilder.success("获取缓存统计成功")
                    .data("cache_stats", stats)
                    .build();

        } catch (Exception e) {
            log.error("❌ 获取缓存统计失败", e);
            return ResponseBuilder.error("获取缓存统计失败: " + e.getMessage()).build();
        }
    }

    /**
     * 清理所有缓存
     */
    @PostMapping("/clear")
    public JSONObject clearAllCache() {
        try {
            cacheService.evictAll();

            return ResponseBuilder.success("缓存清理成功")
                    .data("action", "clear_all")
                    .data("timestamp", System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("❌ 清理缓存失败", e);
            return ResponseBuilder.error("清理缓存失败: " + e.getMessage()).build();
        }
    }

    /**
     * 缓存健康检查
     */
    @GetMapping("/health")
    public JSONObject cacheHealthCheck() {
        try {
            Object stats = cacheService.getCacheStats();

            return ResponseBuilder.success("缓存服务健康")
                    .data("status", "healthy")
                    .data("stats", stats)
                    .build();

        } catch (Exception e) {
            log.error("❌ 缓存健康检查失败", e);
            return ResponseBuilder.error("缓存服务异常: " + e.getMessage()).build();
        }
    }
}