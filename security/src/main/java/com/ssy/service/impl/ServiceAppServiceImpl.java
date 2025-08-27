package com.ssy.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.ssy.entity.ServiceAppEntity;
import com.ssy.mapper.ServiceAppMapper;
import com.ssy.service.ServiceAppService;
import com.ssy.utils.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 服务应用Service实现类
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Service
public class ServiceAppServiceImpl implements ServiceAppService {

    @Autowired
    private ServiceAppMapper serviceAppMapper;

    @Autowired
    private IdGenerator idGenerator;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public ServiceAppEntity registerApp(String appName, List<String> allowedApis, String createBy, String remark) {
        // 检查应用名称是否已存在
        ServiceAppEntity existApp = serviceAppMapper.selectByAppName(appName);
        if (existApp != null) {
            throw new RuntimeException("应用名称已存在：" + appName);
        }

        // 创建新的服务应用
        ServiceAppEntity serviceApp = new ServiceAppEntity();
        serviceApp.setAppName(appName);
        serviceApp.setAppId(String.valueOf(idGenerator.nextId())); // 使用雪花ID生成appId
        serviceApp.setAuthCode(generateAuthCode()); // 生成授权码
        serviceApp.setAllowedApis(JSON.toJSONString(allowedApis)); // 将接口列表转换为JSON存储
        serviceApp.setStatus(1); // 默认启用
        serviceApp.setCreateTime(LocalDateTime.now());
        serviceApp.setUpdateTime(LocalDateTime.now());
        serviceApp.setCreateBy(createBy);
        serviceApp.setRemark(remark);

        // 插入数据库
        serviceAppMapper.insert(serviceApp);

        // 设置返回对象的接口列表
        serviceApp.setAllowedApiList(allowedApis);

        return serviceApp;
    }

    @Override
    public ServiceAppEntity getById(Long id) {
        ServiceAppEntity serviceApp = serviceAppMapper.selectById(id);
        if (serviceApp != null) {
            convertJsonToList(serviceApp);
        }
        return serviceApp;
    }

    @Override
    public ServiceAppEntity getByAppId(String appId) {
        ServiceAppEntity serviceApp = serviceAppMapper.selectByAppId(appId);
        if (serviceApp != null) {
            convertJsonToList(serviceApp);
        }
        return serviceApp;
    }

    @Override
    public List<ServiceAppEntity> getAllApps() {
        List<ServiceAppEntity> apps = serviceAppMapper.selectAll();
        apps.forEach(this::convertJsonToList);
        return apps;
    }

    @Override
    public List<ServiceAppEntity> getAllEnabledApps() {
        List<ServiceAppEntity> apps = serviceAppMapper.selectAllEnabled();
        apps.forEach(this::convertJsonToList);
        return apps;
    }

    @Override
    public ServiceAppEntity updateApp(ServiceAppEntity serviceApp) {
        // 将接口列表转换为JSON
        if (!CollectionUtils.isEmpty(serviceApp.getAllowedApiList())) {
            serviceApp.setAllowedApis(JSON.toJSONString(serviceApp.getAllowedApiList()));
        }

        serviceApp.setUpdateTime(LocalDateTime.now());
        serviceAppMapper.update(serviceApp);

        return getById(serviceApp.getId());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        serviceAppMapper.updateStatus(id, status);
    }

    @Override
    public void deleteApp(Long id) {
        serviceAppMapper.deleteById(id);
    }

    @Override
    public ServiceAppEntity validateApp(String appId, String authCode) {
        ServiceAppEntity serviceApp = serviceAppMapper.validateApp(appId, authCode);
        if (serviceApp != null) {
            convertJsonToList(serviceApp);
        }
        return serviceApp;
    }

    @Override
    public boolean hasPermission(String appId, String apiPath) {
        ServiceAppEntity serviceApp = serviceAppMapper.selectByAppId(appId);
        if (serviceApp == null || serviceApp.getStatus() != 1) {
            return false;
        }

        // 解析允许访问的接口列表
        if (StringUtils.hasText(serviceApp.getAllowedApis())) {
            try {
                List<String> allowedApis = JSON.parseObject(serviceApp.getAllowedApis(),
                        new TypeReference<List<String>>() {
                        });
                if (!CollectionUtils.isEmpty(allowedApis)) {
                    // 支持通配符匹配
                    for (String allowedApi : allowedApis) {
                        if (matchesPattern(apiPath, allowedApi)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // JSON解析失败，记录日志
                System.err.println("解析允许访问的接口列表失败，appId: " + appId + ", error: " + e.getMessage());
            }
        }

        return false;
    }

    /**
     * 生成32位随机授权码
     */
    private String generateAuthCode() {
        byte[] randomBytes = new byte[24]; // 24字节编码后约32字符
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 将JSON字符串转换为接口列表
     */
    private void convertJsonToList(ServiceAppEntity serviceApp) {
        if (StringUtils.hasText(serviceApp.getAllowedApis())) {
            try {
                List<String> allowedApiList = JSON.parseObject(serviceApp.getAllowedApis(),
                        new TypeReference<List<String>>() {
                        });
                serviceApp.setAllowedApiList(allowedApiList);
            } catch (Exception e) {
                // JSON解析失败，设置空列表
                serviceApp.setAllowedApiList(null);
            }
        }
    }

    /**
     * 匹配模式，支持通配符*
     * 
     * @param path    请求路径
     * @param pattern 匹配模式
     * @return 是否匹配
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern.equals("*")) {
            return true; // 全匹配
        }

        if (pattern.equals(path)) {
            return true; // 精确匹配
        }

        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path.startsWith(prefix); // 前缀匹配
        }

        if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1);
            return path.endsWith(suffix); // 后缀匹配
        }

        return false;
    }
}
