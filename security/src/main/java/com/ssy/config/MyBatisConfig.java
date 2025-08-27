package com.ssy.config;

import com.ssy.handler.CollectionTypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/8/26
 * @email 3278440884@qq.com
 */

@Configuration
public class MyBatisConfig {

    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(CollectionTypeHandler.class);
        };
    }
}