package com.ssy.config;

import com.ssy.service.ApiEndpointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * API接口扫描器配置类
 * 在应用启动完成后自动扫描所有API接口并保存到数据库
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Component
@Order(100) // 确保在其他初始化完成后执行
public class ApiEndpointScannerConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ApiEndpointScannerConfig.class);

    @Autowired
    private ApiEndpointService apiEndpointService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            logger.info("🚀 应用启动完成，开始自动扫描API接口...");

            long startTime = System.currentTimeMillis();
            int scannedCount = apiEndpointService.scanAndSaveAllEndpoints();
            long endTime = System.currentTimeMillis();

            logger.info("✅ API接口自动扫描完成！");
            logger.info("📊 扫描结果：新增 {} 个接口，耗时 {} ms", scannedCount, (endTime - startTime));

        } catch (Exception e) {
            logger.error("❌ API接口自动扫描失败：{}", e.getMessage(), e);
        }
    }
}
