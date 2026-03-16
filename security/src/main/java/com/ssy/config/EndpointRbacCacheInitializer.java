package com.ssy.config;

import com.ssy.service.impl.EndpointRbacCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(210)
public class EndpointRbacCacheInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(EndpointRbacCacheInitializer.class);

    private final EndpointRbacCacheService endpointRbacCacheService;

    public EndpointRbacCacheInitializer(EndpointRbacCacheService endpointRbacCacheService) {
        this.endpointRbacCacheService = endpointRbacCacheService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            endpointRbacCacheService.refresh();
            log.info("接口RBAC缓存初始化完成: size={}, cost={}ms",
                    endpointRbacCacheService.size(),
                    (System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.warn("接口RBAC缓存初始化失败: {}", e.getMessage(), e);
        }
    }
}
