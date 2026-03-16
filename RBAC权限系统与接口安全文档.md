# RBAC权限系统与接口安全文档

本文档是当前项目权限体系的唯一主文档，整合了：

- 标准 RBAC（账号/角色/权限）
- 用户登录与 JWT
- 线程内用户上下文（`CurrentUser` / `RequestUserContext`）
- `api_endpoints` 接口扫描与接口级 RBAC 联动
- 服务间调用权限校验
- 异常识别（威胁检测）模块
- 启动自动建表与初始化

适用目录：`/Users/fengjun/Desktop/prejoct/doc-admin/doc-admin`

## 1. 当前系统结构（简版）

系统现在有两条安全链路：

1. 用户调用链路（JWT + RBAC）
2. 服务间调用链路（`X-Service-Call` + `appId` + 服务Token）

### 用户调用链路（推荐理解顺序）

1. `JwtAuthorizationFilter`：解析用户 JWT，提取 `userId/roles/permissions`
2. `RequestUserContextFilter`：写入线程上下文（当前用户信息）
3. `EndpointRbacAuthorizationFilter`：基于 `api_endpoints + sys_permission_endpoint_rel` 做接口级快速预检（内存）
4. `@PreAuthorize(...)`：方法级最终校验（复杂表达式兜底）

### 服务间调用链路

1. `ServicePermissionFilter` 检查 `X-Service-Call: true`
2. 校验 `appid + service token`
3. 从权限缓存判断该服务是否允许访问目标接口
4. 通过后继续进入业务 Controller

## 2. 标准 RBAC 数据模型（核心）

当前 RBAC 不再以 `user.authorities` 作为主模型（仅保留兼容字段）。

### 核心表（在 `security.sql`）

- `sys_role`：角色
- `sys_permission`：权限点
- `sys_permission_endpoint_rel`：权限-接口绑定关系
- `sys_user_role`：用户-角色关系
- `sys_role_permission`：角色-权限关系
- `sys_role_grant_rule`：角色授予规则（谁能创建/赋予/撤销哪些角色）

### 关键能力

- “创建用户”和“修改用户”是不同权限点
- “修改用户角色”是单独权限点
- “谁能创建某角色账号”由 `sys_role_grant_rule` 动态控制（不是写死代码）

## 3. `hasAuthority(...)` 是什么，为什么要迁移

### `hasAuthority(...)`

是 Spring Security 的方法级权限表达式，用于检查“权限点”。

示例：

```java
@PreAuthorize("hasAuthority('iam:user:create')")
```

含义：当前登录用户必须拥有权限码 `iam:user:create` 才能调用该方法。

### `hasRole(...)`

是检查角色（Spring 内部通常会加 `ROLE_` 前缀）。

示例：

```java
@PreAuthorize("hasRole('ADMIN')")
```

### 为什么迁移到 `hasAuthority(...)`

因为你要的是标准 RBAC：

- 角色（Role）负责“归类”
- 权限（Permission）负责“操作能力”

如果只用 `hasRole('ADMIN')`，就做不到以下能力：

- 同样是管理员，A 能建用户，B 只能看用户
- 创建用户/修改用户/改角色分开授权
- 后续按模块细粒度授权（例如 `svc:token:manage`）

所以：

- 角色用于聚合权限
- 接口鉴权尽量用 `hasAuthority(...)`
- `hasRole(...)` 仅保留少量历史兼容场景（可逐步移除）

## 4. `api_endpoints` 如何与 RBAC 联动（当前实现）

### 当前已经联动（是“关系表驱动”）

`api_endpoints` 现在负责接口资源目录，核心字段是：

- `path`
- `method`
- `require_auth`
- `status`
- `module_group`
- `threat_monitor_enabled`

真正的接口权限绑定走：

- `sys_permission_endpoint_rel`

也就是：

- 接口是什么：看 `api_endpoints`
- 接口需要什么权限：看 `sys_permission_endpoint_rel`
- 角色拥有什么权限：看 `sys_role_permission`
- 用户拥有什么角色：看 `sys_user_role`

### 请求时怎么鉴权

启动时 `EndpointRbacCacheService` 会把：

- `api_endpoints`
- `sys_permission_endpoint_rel`

一起加载到内存。

请求进入时由 `EndpointRbacAuthorizationFilter` 进行快速预检：

1. 按 `method + path` 匹配接口
2. 找到该接口绑定的权限列表
3. 从线程上下文拿当前用户权限集合
4. `Set.contains(...)` 做快速判断

### `api_endpoints.auth` 现在的状态

- 字段还保留
- 主要用于扫描展示/兼容
- 不再是过滤器主鉴权依据

### 严格模式

已支持配置：

- `security.endpoint-rbac.strict-unbound-permission-deny`

含义：

- 接口已扫描到 `api_endpoints`
- 且 `require_auth = 1`
- 但没有绑定任何权限
- 是否直接拒绝访问

默认是 `false`，用于平滑迁移；全部接口绑定完成后建议切成 `true`。

1. `sys_permission.endpoint_id`（一对一/多对一）
2. `sys_permission_api_endpoint` 关系表（多对多）

当前版本先用字符串联动，已经能满足“高性能 + 可维护”。

## 5. 线程内用户上下文（重点）

### 你现在不需要再写全限定名

推荐方式：注入 `CurrentUser`

```java
import com.ssy.context.CurrentUser;

@Autowired
private CurrentUser currentUser;

public void demo() {
    Long userId = currentUser.userId();
    String username = currentUser.username();
    boolean canCreate = currentUser.hasPermission("iam:user:create");
}
```

### 上下文字段（当前版本）

`RequestUserContext` 中当前会保存：

- `userId`
- `username`
- `status`
- `roles`
- `permissions`
- `clientIp`
- `requestMethod`
- `requestUri`
- `loginType`（用户登录/管理员登录）
- `serviceCall`（是否服务间调用）

### 相关代码位置

- 上下文模型：`/Users/fengjun/Desktop/prejoct/doc-admin/doc-admin/security/src/main/java/com/ssy/context/RequestUserContext.java`
- 可注入访问器：`/Users/fengjun/Desktop/prejoct/doc-admin/doc-admin/security/src/main/java/com/ssy/context/CurrentUser.java`
- ThreadLocal持有器：`/Users/fengjun/Desktop/prejoct/doc-admin/doc-admin/security/src/main/java/com/ssy/holder/RequestUserContextHolder.java`
- 写入过滤器：`/Users/fengjun/Desktop/prejoct/doc-admin/doc-admin/security/src/main/java/com/ssy/filter/RequestUserContextFilter.java`

## 6. 权限校验性能设计（用户接口）

目标：用户接口权限校验尽量做到 `1-5ms` 的附加开销。

当前实现做到这一点的方式：

1. JWT 保留 `userId` 等基础信息
2. 请求时优先从本地用户权限缓存读取最新 `roles + permissions`
3. 线程上下文里角色/权限转为 `Set`
4. `api_endpoints + sys_permission_endpoint_rel` 启动预加载到内存
5. 动态路径预编译匹配（如 `/api/test/param/{id}`）

注意：

- “权限校验开销”可以做到很低
- 整体请求耗时仍会受数据库/业务逻辑/威胁识别影响

## 7. 用户密码加密（非明文）

系统继续使用 Spring Security 密码编码器：

- `PasswordEncoderFactories.createDelegatingPasswordEncoder()`

账号创建/注册/改密都走 `passwordEncoder.encode(...)`，不会明文入库。

### 生成初始化用户密码哈希（你要求的 test 工具）

已提供工具（可直接运行 `main`）：

- `/Users/fengjun/Desktop/prejoct/doc-admin/doc-admin/security/src/test/java/com/ssy/tools/PasswordHashGeneratorTest.java`

功能：

- 输出加密后的密码（如 `{bcrypt}...`）
- 输出一个雪花 `user_id`

然后你可以把结果写进 `security.sql` 的初始用户种子。

## 8. 当前推荐权限命名规范

格式建议：

- `模块:资源:动作`

示例：

- `iam:user:create`
- `iam:user:update`
- `iam:user:role:assign`
- `svc:app:create`
- `svc:token:manage`
- `api:endpoint:manage`
- `threat:admin:manage`

## 9. 管理接口（当前已支持）

### IAM 基础管理

- `/iam/users`
- `/iam/roles`
- `/iam/permissions`
- `/iam/grant-rules`

### 接口权限绑定

- `GET /iam/endpoints/{endpointId}/permissions`
- `PUT /iam/endpoints/{endpointId}/permissions`
- `PUT /iam/endpoints/module-permissions`

### 推荐配置顺序

1. 启动服务，先扫描生成 `api_endpoints`
2. 创建业务权限点
3. 给接口绑定权限
4. 给角色绑定权限
5. 给用户绑定角色
6. 最后开启严格模式

## 10. 已完成的控制器迁移（`hasAuthority(...)`）

本次已批量迁移一批业务控制器从 `hasRole('ADMIN')` 到 `hasAuthority(...)`：

- `ServiceAppController`
- `ServiceTokenController`
- `PermissionCacheController`
- `ApiEndpointController`
- `ThreatDetectionAdminController`（读/写分权）
- `server` 模块测试控制器 `/test/b`、`/test/c`

对应权限种子已补充到 `security.sql`，默认绑定给 `SUPER_ADMIN` / `ADMIN` / `USER`（测试权限）。

## 11. 启动与初始化（当前版本）

### 自动建表

程序启动时会先检查关键表是否存在，缺失时自动执行项目根目录 `security.sql`。

注意：

- 需要从项目根目录启动（保证能找到 `security.sql`）
- 已存在表结构会走补列逻辑（如 `auth`、`threat_monitor_enabled`）

### 首次登录

`security.sql` 中内置了默认 `superadmin` 用户（首次初始化时创建）。

建议流程：

1. 启动成功后立刻登录
2. 修改密码（使用加密后密码）
3. 通过 `/iam/*` 管理角色、权限、授予规则

## 12. 后续建议（下一阶段）

1. 将 `superadmin` 初始密码改成 bcrypt 版本，不要继续使用 `{noop}`
2. 给各模块完成接口权限绑定后，把 `strict-unbound-permission-deny` 切成 `true`
3. 后续如果需要更强的对象级控制，再补 ABAC 规则（例如“只能改自己部门用户”）

---

如果你继续推进，我建议下一步直接做：

- “权限码清单 + 角色绑定矩阵”
- “按模块查看接口权限绑定结果的接口”
- “对象级权限（ABAC）补充”
