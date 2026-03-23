package com.ssy.properties;

import com.ssy.factory.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Data
@Component
@PropertySource(value = "classpath:security.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "security.audit")
public class AuditLogProperties {

    /**
     * 审计总开关
     */
    private boolean enabled = true;

    /**
     * 日志保留天数
     */
    private int retentionDays = 90;

    /**
     * 单表最大行数，达到后自动分表。
     */
    private long maxRowsPerTable = 20_000_000L;

    /**
     * 异步写入队列容量
     */
    private int queueCapacity = 4096;
}
