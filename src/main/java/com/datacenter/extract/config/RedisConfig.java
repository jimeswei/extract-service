package com.datacenter.extract.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis缓存配置 - v4.0性能优化
 * 
 * 🚀 特性：
 * 1. 分层缓存策略 (Caffeine + Redis)
 * 2. AI结果缓存优化
 * 3. 模板分布式缓存
 * 4. 可配置开关
 */
@Configuration
@ConditionalOnProperty(name = "cache.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${cache.redis.key-prefix:extract:v4:}")
    private String keyPrefix;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用String序列化器作为key序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 使用JSON序列化器作为value序列化器
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("🚀 Redis缓存配置完成 - Key前缀: {}", keyPrefix);
        return template;
    }

    /**
     * 获取缓存键前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }
}