package com.datacenter.extract.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 性能优化配置中心 - v4.0优化版本
 * 
 * 核心优化策略：
 * 1. 线程池优化：专业化线程池设计
 * 2. 缓存优化：文件处理缓存架构
 * 3. 监控优化：全链路性能监控
 */
@Configuration
@ConfigurationProperties(prefix = "performance")
public class PerformanceConfig {

    /**
     * 主业务线程池 - 高性能设计
     */
    @Bean("mainBusinessExecutor")
    @Primary
    public Executor mainBusinessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 动态线程池设计：根据CPU核数配置
        int coreSize = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(coreSize * 2);
        executor.setMaxPoolSize(coreSize * 4);
        executor.setQueueCapacity(1000);

        // 线程管理优化
        executor.setThreadNamePrefix("MainBiz-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        // 拒绝策略：优雅降级
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 生命周期管理
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * AI专用线程池 - 隔离设计
     */
    @Bean("aiProcessingExecutor")
    public Executor aiProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // AI任务特点：IO密集型，需要更多线程
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);

        executor.setThreadNamePrefix("AI-");
        executor.setKeepAliveSeconds(120); // AI任务可能耗时较长

        // 隔离策略：防止AI任务影响主业务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        executor.initialize();
        return executor;
    }

    /**
     * 文件处理线程池 - v4.0新增
     */
    @Bean("fileProcessingExecutor")
    public Executor fileProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 文件IO特点：适中的并发数
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(200);

        executor.setThreadNamePrefix("File-");
        executor.setKeepAliveSeconds(30);

        executor.initialize();
        return executor;
    }

    /**
     * 一级缓存：热点数据缓存
     */
    @Bean("l1Cache")
    public Cache<String, Object> l1Cache() {
        return Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .expireAfterAccess(Duration.ofMinutes(10))
                .recordStats() // 开启统计
                .build();
    }

    /**
     * AI结果缓存
     */
    @Bean("aiResultCache")
    public Cache<String, String> aiResultCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofHours(2))
                .recordStats()
                .build();
    }

    /**
     * 模板缓存 - v4.0新增
     */
    @Bean("templateCache")
    public Cache<String, Object> templateCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofHours(24))
                .recordStats()
                .build();
    }

    /**
     * 自定义缓存管理器 - 统一缓存接口
     */
    @Bean("customCacheManager")
    public CacheManager customCacheManager() {
        return new CacheManager();
    }

    /**
     * 性能监控器
     */
    @Bean
    public PerformanceMonitor performanceMonitor() {
        return new PerformanceMonitor();
    }

    /**
     * 统一缓存管理器 - v4.0优化版本
     */
    public static class CacheManager {
        private Cache<String, Object> l1Cache;
        private Cache<String, String> aiResultCache;
        private Cache<String, Object> templateCache;

        public CacheManager() {
        }

        public void setL1Cache(Cache<String, Object> l1Cache) {
            this.l1Cache = l1Cache;
        }

        public void setAiResultCache(Cache<String, String> aiResultCache) {
            this.aiResultCache = aiResultCache;
        }

        public void setTemplateCache(Cache<String, Object> templateCache) {
            this.templateCache = templateCache;
        }

        public <T> T get(CacheLevel level, String key, Class<T> type) {
            return switch (level) {
                case L1 -> type.cast(l1Cache.getIfPresent(key));
                case AI_RESULT -> type.cast(aiResultCache.getIfPresent(key));
                case TEMPLATE -> type.cast(templateCache.getIfPresent(key));
            };
        }

        public void put(CacheLevel level, String key, Object value) {
            switch (level) {
                case L1 -> l1Cache.put(key, value);
                case AI_RESULT -> aiResultCache.put(key, (String) value);
                case TEMPLATE -> templateCache.put(key, value);
            }
        }

        public CacheStats getStats(CacheLevel level) {
            return switch (level) {
                case L1 -> l1Cache.stats();
                case AI_RESULT -> aiResultCache.stats();
                case TEMPLATE -> templateCache.stats();
            };
        }
    }

    /**
     * 缓存级别枚举 - v4.0更新
     */
    public enum CacheLevel {
        L1, AI_RESULT, TEMPLATE
    }

    /**
     * 性能监控器
     */
    public static class PerformanceMonitor {

        public void recordMetric(String name, double value) {
            // 这里可以集成Micrometer或其他监控框架
        }

        public void recordLatency(String operation, long latencyMs) {
            // 记录操作延迟
        }
    }
}