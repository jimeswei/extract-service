package com.datacenter.extract.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 分层缓存服务 - v4.0性能优化
 * L1: Caffeine 本地缓存
 * L2: Redis 分布式缓存
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final Cache<String, String> aiResultCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean redisEnabled;
    private final String keyPrefix;

    @Autowired
    public CacheService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
            @Value("${cache.redis.enabled:false}") boolean redisEnabled,
            @Value("${cache.redis.key-prefix:extract:v4:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled;
        this.keyPrefix = keyPrefix;

        // AI结果缓存
        this.aiResultCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .recordStats()
                .build();

        log.info("🚀 CacheService初始化完成 - Redis: {}", redisEnabled ? "启用" : "禁用");
    }

    /**
     * AI结果缓存获取
     */
    public String getAIResult(String textHash, String extractType) {
        String key = "ai:" + extractType + ":" + textHash;

        // L1缓存
        String result = aiResultCache.getIfPresent(key);
        if (result != null) {
            log.debug("AI结果L1缓存命中");
            return result;
        }

        // L2缓存
        if (redisEnabled && redisTemplate != null) {
            try {
                result = (String) redisTemplate.opsForValue().get(keyPrefix + key);
                if (result != null) {
                    log.debug("AI结果L2缓存命中");
                    aiResultCache.put(key, result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Redis读取失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * AI结果缓存设置
     */
    public void putAIResult(String textHash, String extractType, String result) {
        String key = "ai:" + extractType + ":" + textHash;

        // L1缓存
        aiResultCache.put(key, result);

        // L2缓存
        if (redisEnabled && redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(keyPrefix + key, result, Duration.ofHours(2));
                log.debug("AI结果已缓存");
            } catch (Exception e) {
                log.warn("Redis写入失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 清理缓存
     */
    public void evictAll() {
        aiResultCache.invalidateAll();
        log.info("L1缓存已清理");
    }

    /**
     * 缓存统计
     */
    public Object getCacheStats() {
        return java.util.Map.of(
                "ai_cache_size", aiResultCache.estimatedSize(),
                "ai_cache_stats", aiResultCache.stats(),
                "redis_enabled", redisEnabled);
    }
}