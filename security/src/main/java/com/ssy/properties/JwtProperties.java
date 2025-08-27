package com.ssy.properties;

import com.ssy.factory.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Data
@Order(Ordered.HIGHEST_PRECEDENCE)  // 最高优先级
@PropertySource(value = "classpath:security.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "jwt")
@Component
public class JwtProperties {
    private String secretKey;
    private long ttl;
    private String headName;
    private String headBase;

    // 兼容方法
    public String getHeadName() {
        return this.headName;
    }

    public String getHeadBase() {
        return this.headBase;
    }

    public String getSecretKey() {
        return this.secretKey;
    }

    public long getTtl() {
        return this.ttl;
    }
}
