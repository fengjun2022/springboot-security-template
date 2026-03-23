package com.ssy.service.impl;

import com.ssy.entity.AuditLogRecordEntity;
import com.ssy.properties.AuditLogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class AuditLogAsyncRecorderService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAsyncRecorderService.class);

    private final AuditLogService auditLogService;
    private final ExecutorService executor;

    public AuditLogAsyncRecorderService(AuditLogService auditLogService, AuditLogProperties properties) {
        this.auditLogService = auditLogService;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(256, properties.getQueueCapacity())),
                r -> {
                    Thread thread = new Thread(r, "AuditLogRecorder");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    public void record(AuditLogRecordEntity entity) {
        if (entity == null) {
            return;
        }
        executor.execute(() -> {
            try {
                auditLogService.record(entity);
            } catch (Exception e) {
                log.warn("异步写入审计日志失败 category={}, uri={}: {}",
                        entity.getCategory(), entity.getRequestUri(), e.getMessage());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }
}
