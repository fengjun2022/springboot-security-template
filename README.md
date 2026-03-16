# doc-admin（RBAC + 服务调用权限 + 接口扫描 + 异常识别）

当前项目权限体系已经升级为：

- 标准 RBAC（账号/角色/权限分离）
- JWT 用户认证（用户身份 + 角色/权限快照）
- 服务间调用权限校验（`X-Service-Call` + `appId` + 服务Token）
- API 接口自动扫描（`api_endpoints`）
- 接口级 RBAC 预检缓存（`api_endpoints + sys_permission_endpoint_rel`）
- 异常识别/黑白名单/频率异常拦截

## 主文档（请优先看）

- [`RBAC权限系统与接口安全文档.md`](./RBAC权限系统与接口安全文档.md)

## 快速开始（简版）

1. 准备 MySQL，确保应用连接配置正确
2. 启动应用（程序会优先检查并自动执行 `security.sql` 创建缺失表）
3. 首次使用请登录默认 `superadmin`（首次初始化会写入）
4. 通过 `/iam/*` 接口配置角色、权限、角色授予规则
5. 通过 `/iam/endpoints/{id}/permissions` 或 `/iam/endpoints/module-permissions` 绑定接口权限
6. 接口绑定完成后，再开启严格模式 `security.endpoint-rbac.strict-unbound-permission-deny=true`

## 说明

- 历史分散文档已合并，旧文档内容不再作为当前版本依据。
- 以 `security.sql` 与主文档为准。
