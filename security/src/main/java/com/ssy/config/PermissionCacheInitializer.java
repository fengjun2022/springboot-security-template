package com.ssy.config;

import com.ssy.service.PermissionCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 权限缓存初始化器
 * 在应用完全启动后自动初始化权限缓存
 * 使用ApplicationReadyEvent确保数据库连接池已完全准备好
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Component
@Order(50) // 在API接口扫描之前执行
public class PermissionCacheInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PermissionCacheInitializer.class);

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 使用异步方式初始化，避免阻塞应用启动
        new Thread(this::initCacheWithRetry, "PermissionCacheInit").start();
    }

    /**
     * 带重试机制的缓存初始化
     */
    private void initCacheWithRetry() {
        int maxRetries = 3;
        int retryDelay = 2000; // 2秒

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("🔄 开始初始化权限缓存... (尝试 {}/{})", attempt, maxRetries);

                // 等待数据库连接完全准备好
                Thread.sleep(retryDelay);

                permissionCacheService.initPermissionCache();
                logger.info("✅ 权限缓存初始化成功");
                return; // 成功后退出

            } catch (Exception e) {
                logger.warn("❌ 权限缓存初始化失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt == maxRetries) {
                    logger.error("💥 权限缓存初始化最终失败，已达到最大重试次数", e);
                } else {
                    try {
                        Thread.sleep(retryDelay * attempt); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("🛑 权限缓存初始化被中断", ie);
                        return;
                    }
                }
            }
        }
    }
}
