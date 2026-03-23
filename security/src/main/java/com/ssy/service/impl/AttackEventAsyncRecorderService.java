package com.ssy.service.impl;

import com.ssy.entity.SecurityAttackEventEntity;
import com.ssy.mapper.SecurityAttackEventMapper;
import com.ssy.properties.ThreatDetectionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异常事件异步落库，避免阻塞请求线程。
 */
@Service
public class AttackEventAsyncRecorderService {

    private static final Logger log = LoggerFactory.getLogger(AttackEventAsyncRecorderService.class);

    private final SecurityAttackEventMapper securityAttackEventMapper;
    private final IpGeoLocationService ipGeoLocationService;
    private final ExecutorService executor;

    public AttackEventAsyncRecorderService(SecurityAttackEventMapper securityAttackEventMapper,
                                           IpGeoLocationService ipGeoLocationService,
                                           ThreatDetectionProperties properties) {
        this.securityAttackEventMapper = securityAttackEventMapper;
        this.ipGeoLocationService = ipGeoLocationService;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(256, properties.getEventQueueCapacity())),
                r -> {
                    Thread t = new Thread(r, "ThreatEventRecorder");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    public void record(SecurityAttackEventEntity entity) {
        if (entity == null) {
            return;
        }
        executor.execute(() -> {
            try {
                securityAttackEventMapper.insert(entity);
                if (entity.getId() != null) {
                    IpGeoLocationService.GeoInfo geoInfo = ipGeoLocationService.resolve(entity.getIp());
                    entity.setCountry(geoInfo.getCountry());
                    entity.setRegionName(geoInfo.getRegionName());
                    entity.setCity(geoInfo.getCity());
                    entity.setIsp(geoInfo.getIsp());
                    entity.setLocationLabel(geoInfo.getLocationLabel());
                    securityAttackEventMapper.updateGeoInfo(entity);
                }
            } catch (Exception e) {
                log.warn("异步写入安全异常事件失败 type={}, path={}: {}",
                        entity.getAttackType(), entity.getPath(), e.getMessage());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }
}
