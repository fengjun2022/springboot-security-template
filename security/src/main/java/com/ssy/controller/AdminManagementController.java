package com.ssy.controller;

import com.common.result.Result;
import com.ssy.entity.ApiEndpointEntity;
import com.ssy.entity.ServiceAppEntity;
import com.ssy.entity.ServiceTokenEntity;
import com.ssy.service.*;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员权限管理页面控制器
 * 提供完整的权限管理Web界面
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Api(tags = "管理员权限管理页面")
@Controller
@RequestMapping("/admin")
public class AdminManagementController {

    @Autowired
    private AdminManagementService adminManagementService;

    @Autowired
    private UserService userService;

    @Autowired
    private ServiceAppService serviceAppService;

    @Autowired
    private ServiceTokenService serviceTokenService;

    @Autowired
    private ApiEndpointService apiEndpointService;

    /**
     * 系统层面Token测试页面
     */
    @GetMapping("/test-system-token")
    public String testSystemToken() {
        return "admin/test-system-token";
    }

    /**
     * 登录页面
     * 支持通过URL参数直接登录：/admin/login?username=xxx&password=xxx
     */
    @GetMapping("/login")
    public String login(@RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            Model model) {
        try {
            // 如果提供了用户名和密码，尝试直接登录
            if (username != null && !username.trim().isEmpty() &&
                    password != null && !password.trim().isEmpty()) {

                username = username.trim();
                password = password.trim();

                // 尝试登录
                try {
                    // 创建新用户（如果不存在）
                    UserCreateDTO userDTO = new UserCreateDTO();
                    userDTO.setUsername(username);
                    userDTO.setPassword(password);
                    userDTO.setEmail(username + "@example.com");
                    userDTO.setPhone("");
                    List<String> roles = new ArrayList<>();
                    roles.add("USER");
                    userDTO.setRoles(roles);
                    userDTO.setRemark("通过URL参数注册");

                    adminManagementService.createUser(userDTO);

                    // 生成token并保存到模型中
                    String token = adminManagementService.generateAdminToken(username);
                    model.addAttribute("autoLoginToken", token);
                    model.addAttribute("username", username);
                    model.addAttribute("success", "注册并登录成功！");

                } catch (Exception e) {
                    // 如果注册失败，可能是用户已存在，尝试登录
                    if (e.getMessage() != null && e.getMessage().contains("已存在")) {
                        // 尝试使用默认管理员账号登录
                        if ("admin".equals(username) && "admin123".equals(password)) {
                            String token = adminManagementService.generateAdminToken(username);
                            model.addAttribute("autoLoginToken", token);
                            model.addAttribute("username", username);
                            model.addAttribute("success", "管理员登录成功！");
                        } else {
                            model.addAttribute("error", "用户已存在，登录失败：用户名或密码错误");
                        }
                    } else {
                        model.addAttribute("error", "登录失败: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            model.addAttribute("error", "系统错误: " + e.getMessage());
        }

        return "admin/login";
    }

    /**
     * 处理登录/注册请求
     * 支持通过 /admin/login 接口进行用户注册
     */
    @PostMapping("/login")
    @ResponseBody
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        try {
            // 检查参数
            if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
                return Result.error("用户名不能为空");
            }
            if (loginRequest.getPassword() == null || loginRequest.getPassword().trim().isEmpty()) {
                return Result.error("密码不能为空");
            }

            String username = loginRequest.getUsername().trim();
            String password = loginRequest.getPassword().trim();

            // 尝试注册新用户
            try {
                // 创建新用户
                UserCreateDTO userDTO = new UserCreateDTO();
                userDTO.setUsername(username);
                userDTO.setPassword(password);
                userDTO.setEmail(username + "@example.com"); // 默认邮箱
                userDTO.setPhone(""); // 默认空手机号
                List<String> roles = new ArrayList<>();
                roles.add("USER");
                userDTO.setRoles(roles); // 默认普通用户角色
                userDTO.setRemark("通过API注册");

                adminManagementService.createUser(userDTO);

                // 注册成功后自动登录，生成token
                String token = adminManagementService.generateAdminToken(username);
                return Result.success(new LoginResponse(token, "注册并登录成功"));

            } catch (Exception e) {
                // 如果注册失败，可能是用户已存在，尝试登录
                if (e.getMessage() != null && e.getMessage().contains("已存在")) {
                    // 尝试使用默认管理员账号登录
                    if ("admin".equals(username) && "admin123".equals(password)) {
                        String token = adminManagementService.generateAdminToken(username);
                        return Result.success(new LoginResponse(token, "管理员登录成功"));
                    } else {
                        return Result.error("用户已存在，登录失败：用户名或密码错误");
                    }
                } else {
                    return Result.error("注册失败: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * Token测试页面
     */
    @GetMapping("/test-token")
    public String testToken() {
        return "admin/test-token";
    }

    /**
     * 检查登录状态
     */
    @GetMapping("/api/auth/status")
    @ResponseBody
    public Result<Map<String, Object>> checkAuthStatus(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Map<String, Object> status = new HashMap<>();
            if (token != null && token.startsWith("Bearer ")) {
                String actualToken = token.substring(7);
                boolean isValid = adminManagementService.validateAdminToken(actualToken);
                status.put("authenticated", isValid);
                if (isValid) {
                    status.put("user", adminManagementService.getUserFromToken(actualToken));
                }
            } else {
                status.put("authenticated", false);
            }
            return Result.success(status);
        } catch (Exception e) {
            return Result.error("检查登录状态失败: " + e.getMessage());
        }
    }

    /**
     * 管理首页
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // 获取系统统计信息
        Map<String, Object> stats = adminManagementService.getSystemStats();
        model.addAttribute("stats", stats);
        return "admin/dashboard";
    }

    /**
     * 用户管理页面
     */
    @GetMapping("/users")
    public String userManagement(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("roles", adminManagementService.getAllRoles());
        return "admin/user-management";
    }

    /**
     * 应用管理页面
     */
    @GetMapping("/apps")
    public String appManagement(Model model) {
        model.addAttribute("apps", serviceAppService.getAllApps());
        model.addAttribute("apiEndpoints", apiEndpointService.getAllEndpoints());
        return "admin/app-management";
    }

    /**
     * 接口管理页面
     */
    @GetMapping("/endpoints")
    public String endpointManagement(Model model) {
        ApiEndpointService.PageResult<ApiEndpointEntity> result = apiEndpointService.getEndpointsByPage(1, 50, null,
                null);
        model.addAttribute("endpoints", result.getRecords());
        model.addAttribute("modules", apiEndpointService.getAllModuleGroups());
        return "admin/endpoint-management";
    }

    /**
     * Token管理页面
     */
    @GetMapping("/tokens")
    public String tokenManagement(Model model) {
        model.addAttribute("tokens", serviceTokenService.getAllTokens());
        return "admin/token-management";
    }

    /**
     * 权限分配页面
     */
    @GetMapping("/permissions")
    public String permissionManagement(Model model) {
        model.addAttribute("apps", serviceAppService.getAllApps());
        model.addAttribute("endpoints", apiEndpointService.getAllEndpoints());
        model.addAttribute("users", userService.getAllUsers());
        return "admin/permission-management";
    }

    /**
     * 系统日志页面
     */
    @GetMapping("/logs")
    public String systemLogs(Model model) {
        model.addAttribute("logs", adminManagementService.getSystemLogs());
        return "admin/system-logs";
    }

    /**
     * 系统监控页面
     */
    @GetMapping("/monitor")
    public String systemMonitor(Model model) {
        model.addAttribute("systemInfo", adminManagementService.getSystemInfo());
        model.addAttribute("cacheStatus", adminManagementService.getCacheStatus());
        return "admin/system-monitor";
    }

    // ========== AJAX API接口 ==========

    /**
     * 新增用户
     */
    @PostMapping("/api/users")
    @ResponseBody
    public Result<String> createUser(@RequestBody UserCreateDTO userDTO) {
        try {
            adminManagementService.createUser(userDTO);
            return Result.success("用户创建成功");
        } catch (Exception e) {
            return Result.error("用户创建失败: " + e.getMessage());
        }
    }

    /**
     * 分配用户角色
     */
    @PostMapping("/api/users/{userId}/roles")
    @ResponseBody
    public Result<String> assignUserRoles(@PathVariable Long userId, @RequestBody List<String> roles) {
        try {
            adminManagementService.assignUserRoles(userId, roles);
            return Result.success("角色分配成功");
        } catch (Exception e) {
            return Result.error("角色分配失败: " + e.getMessage());
        }
    }

    /**
     * 新增应用
     */
    @PostMapping("/api/apps")
    @ResponseBody
    public Result<ServiceAppEntity> createApp(@RequestBody AppCreateDTO appDTO) {
        try {
            ServiceAppEntity app = adminManagementService.createApp(appDTO);
            return Result.success(app);
        } catch (Exception e) {
            return Result.error("应用创建失败: " + e.getMessage());
        }
    }

    /**
     * 分配应用权限
     */
    @PostMapping("/api/apps/{appId}/permissions")
    @ResponseBody
    public Result<String> assignAppPermissions(@PathVariable String appId, @RequestBody List<String> apiPaths) {
        try {
            adminManagementService.assignAppPermissions(appId, apiPaths);
            return Result.success("权限分配成功");
        } catch (Exception e) {
            return Result.error("权限分配失败: " + e.getMessage());
        }
    }

    /**
     * 刷新Token
     */
    @PostMapping("/api/apps/{appId}/refresh-token")
    @ResponseBody
    public Result<ServiceTokenEntity> refreshToken(@PathVariable String appId) {
        try {
            ServiceTokenEntity token = adminManagementService.refreshAppToken(appId);
            return Result.success(token);
        } catch (Exception e) {
            return Result.error("Token刷新失败: " + e.getMessage());
        }
    }

    /**
     * 手动扫描接口
     */
    @PostMapping("/api/endpoints/scan")
    @ResponseBody
    public Result<String> scanEndpoints() {
        try {
            int count = apiEndpointService.scanAndSaveAllEndpoints();
            return Result.success("扫描完成，新增 " + count + " 个接口");
        } catch (Exception e) {
            return Result.error("接口扫描失败: " + e.getMessage());
        }
    }

    /**
     * 更新接口状态
     */
    @PutMapping("/api/endpoints/{id}/status")
    @ResponseBody
    public Result<String> updateEndpointStatus(@PathVariable Long id, @RequestParam Integer status) {
        try {
            adminManagementService.updateEndpointStatus(id, status);
            return Result.success("接口状态更新成功");
        } catch (Exception e) {
            return Result.error("接口状态更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统实时状态
     */
    @GetMapping("/api/system/status")
    @ResponseBody
    public Result<Map<String, Object>> getSystemStatus() {
        try {
            Map<String, Object> status = adminManagementService.getSystemStatus();
            return Result.success(status);
        } catch (Exception e) {
            return Result.error("获取系统状态失败: " + e.getMessage());
        }
    }

    /**
     * 清理系统缓存
     */
    @PostMapping("/api/system/clear-cache")
    @ResponseBody
    public Result<String> clearSystemCache() {
        try {
            adminManagementService.clearSystemCache();
            return Result.success("缓存清理成功");
        } catch (Exception e) {
            return Result.error("缓存清理失败: " + e.getMessage());
        }
    }

    // ========== 登录相关DTO ==========

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class LoginResponse {
        private String token;
        private String message;

        public LoginResponse(String token, String message) {
            this.token = token;
            this.message = message;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // ========== DTO类 ==========

    public static class UserCreateDTO {
        private String username;
        private String password;
        private String email;
        private String phone;
        private List<String> roles;
        private String remark;

        // Getters and Setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class AppCreateDTO {
        private String appName;
        private List<String> allowedApis;
        private String remark;

        // Getters and Setters
        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public List<String> getAllowedApis() {
            return allowedApis;
        }

        public void setAllowedApis(List<String> allowedApis) {
            this.allowedApis = allowedApis;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
