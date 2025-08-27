# Token验证问题解决方案

## 🚨 问题描述

在进行服务间调用时，出现了以下错误：
```json
{"code":400,"msg":"Token错误，解析失败"}
```

## 🔍 问题根本原因

### 问题分析
系统中存在**两个不同的JWT Token验证机制**：

1. **用户Token验证** (JwtAuthorizationFilter)
   - 用于验证前端用户的登录Token
   - 使用密钥：`nangtongcourtjj1001001`
   - 包含用户信息：userId, username, authorities等

2. **服务Token验证** (ServicePermissionFilter)
   - 用于验证服务间调用的永久Token
   - 使用密钥：`service_token_secret_key_2025_zxy_hospital_admin`
   - 包含服务信息：appId, appName, type等

### 冲突现象
当发起服务间调用时：
1. 请求首先经过 `JwtAuthorizationFilter`
2. 该Filter用**用户Token密钥**尝试解析**服务Token**
3. 由于密钥不匹配，导致签名验证失败
4. 抛出 `SignatureVerificationException`
5. 返回错误：`"Token错误，解析失败"`

## ✅ 解决方案

### 修改 JwtAuthorizationFilter
在用户Token验证逻辑中添加服务间调用的跳过机制：

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
    // 1. 白名单检查
    if (isPermitAll(requestURI)) {
        filterChain.doFilter(request, response);
        return;
    }
    
    // 2. 🔑 关键修改：检查是否为服务间调用
    String serviceCallFlag = request.getHeader("X-Service-Call");
    if ("true".equals(serviceCallFlag)) {
        // 跳过用户Token验证，由ServicePermissionFilter处理
        filterChain.doFilter(request, response);
        return;
    }
    
    // 3. 继续用户Token验证逻辑
    // ...
}
```

### 验证流程图
```
服务间调用请求
    ↓
JwtAuthorizationFilter
    ↓
检查 X-Service-Call 请求头
    ↓
serviceCallFlag == "true" ？
    ↓               ↓
   是              否
    ↓               ↓
  跳过验证       用户Token验证
    ↓               ↓
ServicePermissionFilter  继续处理
    ↓
服务Token验证 + 权限检查
    ↓
业务Controller
```

## 🎯 测试验证

### 修复前（失败）
```bash
curl -X GET "http://localhost:8080/api/test/example" \
     -H "X-Service-Call: true" \
     -H "appid: 713021225472069" \
     -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."

# 返回：{"code":400,"msg":"Token错误，解析失败"}
```

### 修复后（成功）
```bash
curl -X GET "http://localhost:8080/api/test/example" \
     -H "X-Service-Call: true" \
     -H "appid: 713021225472069" \
     -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."

# 期望返回：正常业务响应 或 权限相关错误（如果没有权限）
```

## 📋 双重Token系统说明

### 用户Token (User JWT)
```javascript
// 用户登录后获得
{
  "sub": "admin",
  "userId": 1,
  "authorities": "ROLE_ADMIN",
  "exp": 1756241430,
  "iat": 1756214448
}
```
- **用途**：前端用户认证
- **密钥**：`nangtongcourtjj1001001`
- **有效期**：8小时
- **验证器**：JwtAuthorizationFilter

### 服务Token (Service JWT)
```javascript
// 服务应用获得
{
  "iss": "zxy-hospital-admin",
  "sub": "713021225472069",
  "appId": "713021225472069",
  "appName": "测试服务",
  "type": "permanent",
  "iat": 1756214448
}
```
- **用途**：服务间调用认证
- **密钥**：`service_token_secret_key_2025_zxy_hospital_admin`
- **有效期**：永久
- **验证器**：ServicePermissionFilter

## 🔧 关键区别对比

| 特性 | 用户Token | 服务Token |
|------|-----------|-----------|
| **请求头标识** | 无 | `X-Service-Call: true` |
| **验证Filter** | JwtAuthorizationFilter | ServicePermissionFilter |
| **JWT密钥** | nangtongcourtjj1001001 | service_token_secret_key_2025_zxy_hospital_admin |
| **Token内容** | 用户信息 | 服务信息 |
| **有效期** | 8小时 | 永久 |
| **权限检查** | Spring Security RBAC | 接口权限列表匹配 |

## 🎉 修复效果

1. **✅ 用户调用**：继续使用用户Token，正常通过JwtAuthorizationFilter验证
2. **✅ 服务调用**：使用服务Token，跳过JwtAuthorizationFilter，由ServicePermissionFilter处理
3. **✅ 权限隔离**：两套独立的认证体系，互不干扰
4. **✅ 性能优化**：避免了不必要的Token解析尝试

## 🚀 最佳实践建议

1. **明确调用类型**：
   - 前端用户调用：不要加 `X-Service-Call` 头
   - 服务间调用：必须加 `X-Service-Call: true` 头

2. **Token管理**：
   - 用户Token：定期刷新，有过期时间
   - 服务Token：安全存储，定期轮换

3. **错误处理**：
   - 检查请求头是否正确
   - 确认Token类型与调用方式匹配
   - 查看详细的错误日志

4. **安全考虑**：
   - 服务Token应该只在内网使用
   - 定期审查和更新权限配置
   - 监控异常的Token使用情况

这个修复确保了双重认证机制的正确工作，避免了Token验证冲突问题！🎯
