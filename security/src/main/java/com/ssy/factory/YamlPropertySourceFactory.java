package com.ssy.factory;

import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Properties;

/**
 * YAML文件配置类，带调试信息
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/5
 * @email 3278440884@qq.com
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        Resource res = resource.getResource();


        if (!res.exists()) {
            throw new IOException("配置文件不存在: " + res.getDescription() +
                    "\n请检查文件是否在 src/main/resources 目录下");
        }

        // 使用 Spring 提供的 YamlPropertiesFactoryBean 将 YAML 转为 Properties
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(res);

        Properties properties = factory.getObject();
        if (properties == null) {
            properties = new Properties();
            System.out.println("警告: YAML文件解析后为空");
        }

        // 若没指定 name，就用文件名做 name
        String sourceName = (name != null ? name : res.getFilename());
        return new PropertiesPropertySource(sourceName, properties);
    }
}