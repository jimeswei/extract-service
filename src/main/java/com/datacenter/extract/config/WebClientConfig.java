package com.datacenter.extract.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 🌐 WebClient网络优化配置
 * 
 * 🚀 优化特性：
 * 1. 连接池管理：复用连接，减少建连开销
 * 2. 超时控制：多层次超时保护
 * 3. 资源管理：自动清理和回收
 * 4. 性能监控：连接池统计
 */
@Configuration
public class WebClientConfig {

    /**
     * 🚀 优化的WebClient构建器
     */
    @Bean
    @Primary
    public WebClient.Builder optimizedWebClientBuilder() {
        // 🔧 连接池配置
        ConnectionProvider connectionProvider = ConnectionProvider.builder("extract-service-pool")
                .maxConnections(50) // 最大连接数
                .maxIdleTime(Duration.ofSeconds(30)) // 空闲超时
                .maxLifeTime(Duration.ofSeconds(60)) // 连接最大生存时间
                .pendingAcquireTimeout(Duration.ofSeconds(60)) // 获取连接超时
                .evictInBackground(Duration.ofSeconds(10)) // 后台清理周期
                .build();

        // 🌐 HTTP客户端配置
        HttpClient httpClient = HttpClient.create(connectionProvider)
                // 连接超时
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                // 保持连接活跃
                .option(ChannelOption.SO_KEEPALIVE, true)
                // TCP无延迟
                .option(ChannelOption.TCP_NODELAY, true)
                // 读写超时处理器
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(180, TimeUnit.SECONDS));
                    conn.addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS));
                });

        // 🔧 构建WebClient
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 内存缓冲区大小（10MB）
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
                    configurer.defaultCodecs().enableLoggingRequestDetails(true);
                });
    }
}