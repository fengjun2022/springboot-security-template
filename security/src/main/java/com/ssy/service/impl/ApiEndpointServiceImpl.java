package com.ssy.service.impl;

import com.ssy.entity.ApiEndpointEntity;
import com.ssy.mapper.ApiEndpointMapper;
import com.ssy.service.ApiEndpointService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API接口管理服务实现类
 * 实现API接口的自动扫描、存储和查询功能
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Service
public class ApiEndpointServiceImpl implements ApiEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(ApiEndpointServiceImpl.class);

    @Autowired
    private ApiEndpointMapper apiEndpointMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    @Transactional
    public int scanAndSaveAllEndpoints() {
        logger.info("开始扫描系统中的所有API接口...");

        // 获取所有Controller Bean
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);
        controllers.putAll(applicationContext.getBeansWithAnnotation(Controller.class));

        List<ApiEndpointEntity> allEndpoints = new ArrayList<>();

        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            Object controller = entry.getValue();
            Class<?> controllerClass = controller.getClass();

            // 获取类级别的RequestMapping
            String basePath = getClassBasePath(controllerClass);
            String moduleGroup = getModuleGroup(controllerClass);

            // 扫描所有方法
            Method[] methods = controllerClass.getDeclaredMethods();
            for (Method method : methods) {
                List<ApiEndpointEntity> endpoints = scanMethodEndpoints(
                        controllerClass, method, basePath, moduleGroup);
                allEndpoints.addAll(endpoints);
            }
        }

        if (allEndpoints.isEmpty()) {
            logger.warn("未扫描到任何API接口");
            return 0;
        }

        // 增量插入或更新
        int savedCount = 0;
        for (ApiEndpointEntity endpoint : allEndpoints) {
            ApiEndpointEntity existing = apiEndpointMapper.selectByPathAndMethod(
                    endpoint.getPath(), endpoint.getMethod());

            if (existing == null) {
                // 新接口，插入
                endpoint.setCreateTime(LocalDateTime.now());
                endpoint.setUpdateTime(LocalDateTime.now());
                apiEndpointMapper.insert(endpoint);
                savedCount++;
                logger.debug("新增接口: {} {}", endpoint.getMethod(), endpoint.getPath());
            } else {
                // 已存在的接口，更新控制器信息
                existing.setControllerClass(endpoint.getControllerClass());
                existing.setControllerMethod(endpoint.getControllerMethod());
                existing.setBasePath(endpoint.getBasePath());
                existing.setDescription(endpoint.getDescription());
                existing.setModuleGroup(endpoint.getModuleGroup());
                existing.setUpdateTime(LocalDateTime.now());
                apiEndpointMapper.update(existing);
                logger.debug("更新接口: {} {}", existing.getMethod(), existing.getPath());
            }
        }

        logger.info("API接口扫描完成，共扫描到 {} 个接口，新增 {} 个", allEndpoints.size(), savedCount);
        return savedCount;
    }

    @Override
    @Transactional
    public int forceRescanAllEndpoints() {
        logger.info("开始强制重新扫描所有API接口...");

        // 删除所有现有接口
        int deletedCount = apiEndpointMapper.deleteAll();
        logger.info("已删除 {} 个现有接口记录", deletedCount);

        // 重新扫描并插入
        List<ApiEndpointEntity> allEndpoints = scanAllEndpoints();

        if (!allEndpoints.isEmpty()) {
            for (ApiEndpointEntity endpoint : allEndpoints) {
                endpoint.setCreateTime(LocalDateTime.now());
                endpoint.setUpdateTime(LocalDateTime.now());
            }
            int insertedCount = apiEndpointMapper.batchInsert(allEndpoints);
            logger.info("强制重新扫描完成，共插入 {} 个接口", insertedCount);
            return insertedCount;
        }

        return 0;
    }

    @Override
    public int incrementalScanEndpoints() {
        logger.info("开始增量扫描新API接口...");
        return scanAndSaveAllEndpoints();
    }

    @Override
    public PageResult<ApiEndpointEntity> getEndpointsByPage(int page, int size, String keyword, String moduleGroup) {
        int offset = (page - 1) * size;

        List<ApiEndpointEntity> records = apiEndpointMapper.selectByPage(offset, size, keyword, moduleGroup);
        int total = apiEndpointMapper.countByCondition(keyword, moduleGroup);

        return new PageResult<>(records, total, page, size);
    }

    @Override
    public List<String> getAllModuleGroups() {
        return apiEndpointMapper.selectAllModuleGroups();
    }

    @Override
    public ApiEndpointEntity getEndpointById(Long id) {
        return apiEndpointMapper.selectAll().stream()
                .filter(endpoint -> endpoint.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean updateEndpoint(ApiEndpointEntity apiEndpoint) {
        apiEndpoint.setUpdateTime(LocalDateTime.now());
        return apiEndpointMapper.update(apiEndpoint) > 0;
    }

    /**
     * 扫描所有端点（不保存到数据库）
     */
    private List<ApiEndpointEntity> scanAllEndpoints() {
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);
        controllers.putAll(applicationContext.getBeansWithAnnotation(Controller.class));

        List<ApiEndpointEntity> allEndpoints = new ArrayList<>();

        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            Object controller = entry.getValue();
            Class<?> controllerClass = controller.getClass();

            String basePath = getClassBasePath(controllerClass);
            String moduleGroup = getModuleGroup(controllerClass);

            Method[] methods = controllerClass.getDeclaredMethods();
            for (Method method : methods) {
                List<ApiEndpointEntity> endpoints = scanMethodEndpoints(
                        controllerClass, method, basePath, moduleGroup);
                allEndpoints.addAll(endpoints);
            }
        }

        return allEndpoints;
    }

    /**
     * 获取类级别的基础路径
     */
    private String getClassBasePath(Class<?> controllerClass) {
        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            return classMapping.value()[0];
        }
        return null;
    }

    /**
     * 获取模块分组
     */
    private String getModuleGroup(Class<?> controllerClass) {
        Api apiAnnotation = controllerClass.getAnnotation(Api.class);
        if (apiAnnotation != null && !apiAnnotation.tags()[0].isEmpty()) {
            return apiAnnotation.tags()[0];
        }

        String className = controllerClass.getSimpleName();
        if (className.endsWith("Controller")) {
            return className.substring(0, className.length() - 10);
        }

        return "其他";
    }

    /**
     * 扫描方法的所有端点
     */
    private List<ApiEndpointEntity> scanMethodEndpoints(Class<?> controllerClass, Method method,
            String basePath, String moduleGroup) {
        List<ApiEndpointEntity> endpoints = new ArrayList<>();

        // 检查各种映射注解
        String[] paths = null;
        String[] httpMethods = null;

        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            httpMethods = Arrays.stream(mapping.method())
                    .map(RequestMethod::name)
                    .toArray(String[]::new);
            if (httpMethods.length == 0) {
                httpMethods = new String[] { "GET", "POST", "PUT", "DELETE", "PATCH" };
            }
        } else if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            httpMethods = new String[] { "GET" };
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            httpMethods = new String[] { "POST" };
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            httpMethods = new String[] { "PUT" };
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            httpMethods = new String[] { "DELETE" };
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            PatchMapping mapping = method.getAnnotation(PatchMapping.class);
            paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            httpMethods = new String[] { "PATCH" };
        }

        if (paths == null || httpMethods == null) {
            return endpoints;
        }

        // 获取方法描述
        String description = getMethodDescription(method);

        // 生成所有路径和方法的组合
        for (String path : paths) {
            for (String httpMethod : httpMethods) {
                String fullPath = buildFullPath(basePath, path);

                ApiEndpointEntity endpoint = new ApiEndpointEntity();
                endpoint.setPath(fullPath);
                endpoint.setMethod(httpMethod);
                endpoint.setControllerClass(controllerClass.getSimpleName());
                endpoint.setControllerMethod(method.getName());
                endpoint.setBasePath(basePath);
                endpoint.setDescription(description);
                endpoint.setModuleGroup(moduleGroup);
                endpoint.setRequireAuth(1); // 默认需要认证
                endpoint.setStatus(1); // 默认启用

                endpoints.add(endpoint);
            }
        }

        return endpoints;
    }

    /**
     * 获取方法描述
     */
    private String getMethodDescription(Method method) {
        ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
        if (apiOperation != null && !apiOperation.value().isEmpty()) {
            return apiOperation.value();
        }

        return method.getName();
    }

    /**
     * 构建完整路径
     */
    private String buildFullPath(String basePath, String methodPath) {
        if (basePath == null) {
            basePath = "";
        }
        if (methodPath == null) {
            methodPath = "";
        }

        String fullPath = basePath + methodPath;

        // 标准化路径
        if (!fullPath.startsWith("/")) {
            fullPath = "/" + fullPath;
        }

        // 去除重复的斜杠
        fullPath = fullPath.replaceAll("/+", "/");

        return fullPath;
    }

    @Override
    public List<ApiEndpointEntity> getAllEndpoints() {
        return apiEndpointMapper.selectAll();
    }
}
