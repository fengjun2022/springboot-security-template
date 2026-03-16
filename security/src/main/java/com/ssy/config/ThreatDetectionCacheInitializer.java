package com.ssy.config;

import com.ssy.properties.ThreatDetectionProperties;
import com.ssy.service.impl.EndpointThreatCacheService;
import com.ssy.service.impl.IpAccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Component
@Order(200)
public class ThreatDetectionCacheInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ThreatDetectionCacheInitializer.class);

    private final ThreatDetectionProperties properties;
    private final EndpointThreatCacheService endpointThreatCacheService;
    private final IpAccessControlService ipAccessControlService;

    public ThreatDetectionCacheInitializer(ThreatDetectionProperties properties,
                                           EndpointThreatCacheService endpointThreatCacheService,
                                           IpAccessControlService ipAccessControlService) {
        this.properties = properties;
        this.endpointThreatCacheService = endpointThreatCacheService;
        this.ipAccessControlService = ipAccessControlService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnabled()) {
            log.info("异常识别功能已关闭，跳过缓存初始化");
            return;
        }

        long start = System.currentTimeMillis();
        endpointThreatCacheService.refresh();
        ipAccessControlService.refreshCaches();
        log.info("异常识别缓存初始化完成: endpointCacheSize={}, blacklistSize={}, whitelistSize={}, cost={}ms",
                endpointThreatCacheService.size(),
                ipAccessControlService.blacklistSize(),
                ipAccessControlService.whitelistSize(),
                (System.currentTimeMillis() - start));
    }
}
