package com.ssy.service.impl;

import com.ssy.entity.ApiEndpointEntity;
import com.ssy.mapper.ApiEndpointMapper;
import com.ssy.service.ApiEndpointService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * API扫描功能开关，从yml配置文件读取
     * 默认值为true，即默认开启扫描功能
     */
    @Value("${api.scan.enabled:true}")
    private boolean apiScanEnabled;

    /**
     * API扫描详细日志开关
     */
    @Value("${api.scan.debug-log:false}")
    private boolean debugLogEnabled;

    @Override
    @Transactional
    public int scanAndSaveAllEndpoints() {
        if (!apiScanEnabled) {
            logger.info("API扫描功能已关闭，跳过扫描操作");
            return 0;
        }

        logger.info("开始扫描系统中的所有API接口...");

        // 获取所有Controller Bean
        Map<String, Object> restControllers = applicationContext.getBeansWithAnnotation(RestController.class);
        Map<String, Object> mvcControllers = applicationContext.getBeansWithAnnotation(Controller.class);

        logger.info("找到 @RestController: {}", restControllers.keySet());
        logger.info("找到 @Controller: {}", mvcControllers.keySet());

        Map<String, Object> controllers = new HashMap<>(restControllers);
        controllers.putAll(mvcControllers);

        List<ApiEndpointEntity> allEndpoints = new ArrayList<>();

        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            String beanName = entry.getKey();
            Object controller = entry.getValue();
            Class<?> controllerClass = controller.getClass();

            // 处理CGLIB代理类
            if (controllerClass.getName().contains("$")) {
                controllerClass = controllerClass.getSuperclass();
                logger.info("检测到CGLIB代理类，使用父类: {}", controllerClass.getSimpleName());
            }

            logger.info("扫描Controller: {} (Bean: {})", controllerClass.getSimpleName(), beanName);

            // 获取类级别的RequestMapping
            String basePath = getClassBasePath(controllerClass);
            String moduleGroup = getModuleGroup(controllerClass);

            // 扫描所有方法
            Method[] methods = controllerClass.getDeclaredMethods();
            int endpointCount = 0;

            for (Method method : methods) {
                List<ApiEndpointEntity> endpoints = scanMethodEndpoints(
                        controllerClass, method, basePath, moduleGroup);
                allEndpoints.addAll(endpoints);
                endpointCount += endpoints.size();
            }

            logger.info("Controller {} 扫描完成，找到 {} 个端点", controllerClass.getSimpleName(), endpointCount);
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
                if (debugLogEnabled) {
                    logger.debug("新增接口: {} {}", endpoint.getMethod(), endpoint.getPath());
                }
            } else {
                // 已存在的接口，更新控制器信息
                existing.setControllerClass(endpoint.getControllerClass());
                existing.setControllerMethod(endpoint.getControllerMethod());
                existing.setBasePath(endpoint.getBasePath());
                existing.setDescription(endpoint.getDescription());
                existing.setModuleGroup(endpoint.getModuleGroup());
                existing.setAuth(endpoint.getAuth()); // 更新权限信息
                existing.setRequireAuth(endpoint.getRequireAuth()); // 更新权限要求
                existing.setUpdateTime(LocalDateTime.now());
                apiEndpointMapper.update(existing);
                if (debugLogEnabled) {
                    logger.debug("更新接口: {} {}", existing.getMethod(), existing.getPath());
                }
            }
        }

        logger.info("API接口扫描完成，共扫描到 {} 个接口，新增 {} 个", allEndpoints.size(), savedCount);
        return savedCount;
    }

    @Override
    @Transactional
    public int forceRescanAllEndpoints() {
        if (!apiScanEnabled) {
            logger.info("API扫描功能已关闭，跳过强制重新扫描操作");
            return 0;
        }

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
        if (!apiScanEnabled) {
            logger.info("API扫描功能已关闭，跳过增量扫描操作");
            return 0;
        }

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
     * 检查API扫描功能是否启用
     */
    public boolean isApiScanEnabled() {
        return apiScanEnabled;
    }

    /**
     * 动态设置API扫描开关（仅在当前运行时生效）
     */
    public void setApiScanEnabled(boolean enabled) {
        this.apiScanEnabled = enabled;
        logger.info("API扫描功能已{}：{}", enabled ? "开启" : "关闭", enabled);
    }

    /**
     * 扫描所有端点（不保存到数据库）
     */
    private List<ApiEndpointEntity> scanAllEndpoints() {
        if (!apiScanEnabled) {
            logger.info("API扫描功能已关闭，返回空列表");
            return new ArrayList<>();
        }

        Map<String, Object> restControllers = applicationContext.getBeansWithAnnotation(RestController.class);
        Map<String, Object> mvcControllers = applicationContext.getBeansWithAnnotation(Controller.class);

        Map<String, Object> controllers = new HashMap<>(restControllers);
        controllers.putAll(mvcControllers);

        List<ApiEndpointEntity> allEndpoints = new ArrayList<>();

        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            Object controller = entry.getValue();
            Class<?> controllerClass = controller.getClass();

            // 处理CGLIB代理类
            if (controllerClass.getName().contains("$")) {
                controllerClass = controllerClass.getSuperclass();
            }

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

        // 获取权限信息（扫描方法和类级别的权限注解）
        String authInfo = extractAuthInfo(controllerClass, method);

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
                endpoint.setAuth(authInfo); // 设置权限信息
                endpoint.setRequireAuth(authInfo != null && !authInfo.trim().isEmpty() ? 1 : 0);
                endpoint.setStatus(1); // 默认启用

                endpoints.add(endpoint);
            }
        }

        return endpoints;
    }

    /**
     * 提取权限信息（支持多种Spring Security权限注解）
     */
    private String extractAuthInfo(Class<?> controllerClass, Method method) {
        List<String> authExpressions = new ArrayList<>();

        // 1. 扫描方法级别的权限注解（优先级高）
        extractMethodLevelAuth(method, authExpressions);

        // 2. 扫描类级别的权限注解（如果方法级别没有权限注解）
        if (authExpressions.isEmpty()) {
            extractClassLevelAuth(controllerClass, authExpressions);
        }

        // 3. 合并权限表达式
        if (authExpressions.isEmpty()) {
            return null; // 无权限要求
        }

        // 用逗号分隔多个权限表达式
        return String.join(", ", authExpressions);
    }

    /**
     * 扫描方法级别的权限注解
     */
    private void extractMethodLevelAuth(Method method, List<String> authExpressions) {
        // @PreAuthorize 注解
        if (method.isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class)) {
            org.springframework.security.access.prepost.PreAuthorize preAuth =
                    method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            authExpressions.add(extractExpressionContent(preAuth.value()));
        }

        // @PostAuthorize 注解
        if (method.isAnnotationPresent(org.springframework.security.access.prepost.PostAuthorize.class)) {
            org.springframework.security.access.prepost.PostAuthorize postAuth =
                    method.getAnnotation(org.springframework.security.access.prepost.PostAuthorize.class);
            authExpressions.add(extractExpressionContent(postAuth.value()));
        }

        // @Secured 注解
        if (method.isAnnotationPresent(org.springframework.security.access.annotation.Secured.class)) {
            org.springframework.security.access.annotation.Secured secured =
                    method.getAnnotation(org.springframework.security.access.annotation.Secured.class);
            for (String role : secured.value()) {
                authExpressions.add(cleanRoleName(role));
            }
        }

        // @RolesAllowed 注解 (JSR-250)
        try {
            if (method.isAnnotationPresent(javax.annotation.security.RolesAllowed.class)) {
                javax.annotation.security.RolesAllowed rolesAllowed =
                        method.getAnnotation(javax.annotation.security.RolesAllowed.class);
                for (String role : rolesAllowed.value()) {
                    authExpressions.add(cleanRoleName(role));
                }
            }
        } catch (NoClassDefFoundError ignored) {
            // JSR-250 不可用时忽略
        }

        // @PreFilter 注解
        if (method.isAnnotationPresent(org.springframework.security.access.prepost.PreFilter.class)) {
            org.springframework.security.access.prepost.PreFilter preFilter =
                    method.getAnnotation(org.springframework.security.access.prepost.PreFilter.class);
            authExpressions.add(extractExpressionContent(preFilter.value()));
        }

        // @PostFilter 注解
        if (method.isAnnotationPresent(org.springframework.security.access.prepost.PostFilter.class)) {
            org.springframework.security.access.prepost.PostFilter postFilter =
                    method.getAnnotation(org.springframework.security.access.prepost.PostFilter.class);
            authExpressions.add(extractExpressionContent(postFilter.value()));
        }

        // 检查自定义权限注解（可扩展）
        extractCustomAuthAnnotations(method, authExpressions);
    }

    /**
     * 扫描类级别的权限注解
     */
    private void extractClassLevelAuth(Class<?> controllerClass, List<String> authExpressions) {
        // @PreAuthorize 注解
        if (controllerClass.isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class)) {
            org.springframework.security.access.prepost.PreAuthorize preAuth =
                    controllerClass.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            authExpressions.add(extractExpressionContent(preAuth.value()));
        }

        // @Secured 注解
        if (controllerClass.isAnnotationPresent(org.springframework.security.access.annotation.Secured.class)) {
            org.springframework.security.access.annotation.Secured secured =
                    controllerClass.getAnnotation(org.springframework.security.access.annotation.Secured.class);
            for (String role : secured.value()) {
                authExpressions.add(cleanRoleName(role));
            }
        }

        // @RolesAllowed 注解 (JSR-250)
        try {
            if (controllerClass.isAnnotationPresent(javax.annotation.security.RolesAllowed.class)) {
                javax.annotation.security.RolesAllowed rolesAllowed =
                        controllerClass.getAnnotation(javax.annotation.security.RolesAllowed.class);
                for (String role : rolesAllowed.value()) {
                    authExpressions.add(cleanRoleName(role));
                }
            }
        } catch (NoClassDefFoundError ignored) {
            // JSR-250 不可用时忽略
        }

        // 检查自定义权限注解（可扩展）
        extractCustomAuthAnnotations(controllerClass, authExpressions);
    }

    /**
     * 提取表达式内容，去除注解前缀，只保留括号内的内容
     */
    private String extractExpressionContent(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return "";
        }

        // 去除前后空格
        expression = expression.trim();

        // 处理常见的Spring Security表达式
        if (expression.startsWith("hasRole('") && expression.endsWith("')")) {
            // hasRole('ADMIN') -> ADMIN
            return expression.substring(9, expression.length() - 2);
        } else if (expression.startsWith("hasRole(\"") && expression.endsWith("\")")) {
            // hasRole("ADMIN") -> ADMIN
            return expression.substring(9, expression.length() - 2);
        } else if (expression.startsWith("hasAuthority('") && expression.endsWith("')")) {
            // hasAuthority('WRITE') -> WRITE
            return expression.substring(14, expression.length() - 2);
        } else if (expression.startsWith("hasAuthority(\"") && expression.endsWith("\")")) {
            // hasAuthority("WRITE") -> WRITE
            return expression.substring(14, expression.length() - 2);
        } else if (expression.startsWith("hasAnyRole('") && expression.endsWith("')")) {
            // hasAnyRole('ADMIN', 'USER') -> ADMIN, USER
            String roles = expression.substring(12, expression.length() - 2);
            return parseCommaSeparatedRoles(roles);
        } else if (expression.startsWith("hasAnyRole(\"") && expression.endsWith("\")")) {
            // hasAnyRole("ADMIN", "USER") -> ADMIN, USER
            String roles = expression.substring(12, expression.length() - 2);
            return parseCommaSeparatedRoles(roles);
        } else if (expression.startsWith("hasAnyAuthority('") && expression.endsWith("')")) {
            // hasAnyAuthority('READ', 'write') -> read, write
            String authorities = expression.substring(17, expression.length() - 2);
            return parseCommaSeparatedRoles(authorities);
        } else if (expression.startsWith("hasAnyAuthority(\"") && expression.endsWith("\")")) {
            // hasAnyAuthority("read", "write") -> read, write
            String authorities = expression.substring(17, expression.length() - 2);
            return parseCommaSeparatedRoles(authorities);
        } else if (expression.equals("permitAll()")) {
            // permitAll() -> PUBLIC
            return "PUBLIC";
        } else if (expression.equals("denyAll()")) {
            // denyAll() -> DENY
            return "DENY";
        } else if (expression.equals("isAnonymous()")) {
            // isAnonymous() -> ANONYMOUS
            return "ANONYMOUS";
        } else if (expression.equals("isAuthenticated()")) {
            // isAuthenticated() -> AUTHENTICATED
            return "AUTHENTICATED";
        } else if (expression.equals("isFullyAuthenticated()")) {
            // isFullyAuthenticated() -> FULLY_AUTHENTICATED
            return "FULLY_AUTHENTICATED";
        } else if (expression.equals("isRememberMe()")) {
            // isRememberMe() -> REMEMBER_ME
            return "REMEMBER_ME";
        } else if (expression.contains("hasRole") || expression.contains("hasAuthority") ||
                expression.contains("hasAnyRole") || expression.contains("hasAnyAuthority")) {
            // 处理复杂表达式，如：hasRole('ADMIN') or hasRole('MANAGER')
            return parseComplexExpression(expression);
        } else {
            // 其他复杂表达式保持原样
            return expression;
        }
    }

    /**
     * 解析逗号分隔的角色字符串
     */
    private String parseCommaSeparatedRoles(String roles) {
        if (roles == null || roles.trim().isEmpty()) {
            return "";
        }

        List<String> roleList = new ArrayList<>();
        String[] parts = roles.split(",");

        for (String part : parts) {
            String cleanPart = part.trim();
            // 去除引号
            if ((cleanPart.startsWith("'") && cleanPart.endsWith("'")) ||
                    (cleanPart.startsWith("\"") && cleanPart.endsWith("\""))) {
                cleanPart = cleanPart.substring(1, cleanPart.length() - 1);
            }
            roleList.add(cleanRoleName(cleanPart.trim()));
        }

        return String.join(", ", roleList);
    }

    /**
     * 解析复杂的权限表达式
     */
    private String parseComplexExpression(String expression) {
        List<String> roles = new ArrayList<>();

        // 使用正则表达式匹配所有的角色和权限
        Pattern pattern = Pattern.compile(
                "(?:hasRole|hasAuthority|hasAnyRole|hasAnyAuthority)\\(([^)]+)\\)"
        );
        Matcher matcher = pattern.matcher(expression);

        while (matcher.find()) {
            String match = matcher.group(1);
            // 处理单个参数或多个参数
            if (match.contains(",")) {
                // 多个参数，如：'ADMIN', 'USER'
                String[] parts = match.split(",");
                for (String part : parts) {
                    String cleanPart = part.trim();
                    if ((cleanPart.startsWith("'") && cleanPart.endsWith("'")) ||
                            (cleanPart.startsWith("\"") && cleanPart.endsWith("\""))) {
                        cleanPart = cleanPart.substring(1, cleanPart.length() - 1);
                    }
                    String cleanRole = cleanRoleName(cleanPart.trim());
                    if (!roles.contains(cleanRole)) {
                        roles.add(cleanRole);
                    }
                }
            } else {
                // 单个参数，如：'ADMIN'
                String cleanMatch = match.trim();
                if ((cleanMatch.startsWith("'") && cleanMatch.endsWith("'")) ||
                        (cleanMatch.startsWith("\"") && cleanMatch.endsWith("\""))) {
                    cleanMatch = cleanMatch.substring(1, cleanMatch.length() - 1);
                }
                String cleanRole = cleanRoleName(cleanMatch.trim());
                if (!roles.contains(cleanRole)) {
                    roles.add(cleanRole);
                }
            }
        }

        if (roles.isEmpty()) {
            // 如果没有匹配到标准角色表达式，返回原表达式
            return expression;
        }

        return String.join(", ", roles);
    }

    /**
     * 清理角色名称，去除ROLE_前缀
     */
    private String cleanRoleName(String role) {
        if (role == null) {
            return "";
        }

        role = role.trim();

        // 去除ROLE_前缀
        if (role.startsWith("ROLE_")) {
            return role.substring(5);
        }

        return role;
    }

    /**
     * 扫描自定义权限注解（可根据项目需要扩展）
     */
    private void extractCustomAuthAnnotations(Object target, List<String> authExpressions) {
        // 这里可以添加项目特定的权限注解扫描逻辑
        // 例如：
        // if (target instanceof Method) {
        //     Method method = (Method) target;
        //     if (method.isAnnotationPresent(CustomAuth.class)) {
        //         CustomAuth customAuth = method.getAnnotation(CustomAuth.class);
        //         authExpressions.add(customAuth.value());
        //     }
        // } else if (target instanceof Class) {
        //     Class<?> clazz = (Class<?>) target;
        //     if (clazz.isAnnotationPresent(CustomAuth.class)) {
        //         CustomAuth customAuth = clazz.getAnnotation(CustomAuth.class);
        //         authExpressions.add(customAuth.value());
        //     }
        // }
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