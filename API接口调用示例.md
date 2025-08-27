# API接口调用示例

## 🔐 用户注册和登录

### 用户注册
**接口地址**: `POST /admin/login`

**功能**: 通过此接口可以注册新用户，注册成功后自动登录并返回JWT Token

**请求示例**:
```bash
curl -X POST "http://localhost:8080/admin/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "testpass123"
  }'
```

**响应示例**:
```json
{
  "code": 200,
  "msg": "注册并登录成功",
  "data": {
    "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
    "message": "注册并登录成功"
  }
}
```

### 管理员登录
**接口地址**: `POST /admin/login`

**功能**: 管理员账号登录

**请求示例**:
```bash
curl -X POST "http://localhost:8080/admin/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

**响应示例**:
```json
{
  "code": 200,
  "msg": "管理员登录成功",
  "data": {
    "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
    "message": "管理员登录成功"
  }
}
```

**注意事项**:
- 用户名和密码不能为空
- 如果用户已存在，接口会返回错误信息
- 注册成功后会自动生成JWT Token，可用于后续API调用
- 使用返回的token时需要在请求头中添加: `Authorization: Bearer {token}`
- **Token自动保存**: 前端会自动将token保存到localStorage中
- **自动添加Authorization头**: 后续所有AJAX请求会自动包含Authorization头

## 🔑 Token管理说明

### Token保存机制
- 登录成功后，token会自动保存到浏览器的localStorage中
- 所有后续的AJAX请求会自动包含`Authorization: Bearer {token}`头
- 如果token过期或无效，系统会自动跳转到登录页面

### 手动使用Token
如果需要手动发送请求，可以这样使用：
```bash
# 1. 先登录获取token
curl -X POST "http://localhost:8080/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 2. 使用返回的token
curl -X GET "http://localhost:8080/api/service-app/list" \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
```

## 🧪 完整测试流程

### 1. 启动应用
```bash
mvn spring-boot:run -pl server
```

### 2. 访问登录页面
打开浏览器访问：`http://localhost:8080/admin/login`

### 3. 测试登录流程
```bash
# 登录获取token
curl -X POST "http://localhost:8080/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

### 4. 使用token访问API
```bash
# 使用返回的token访问受保护的API
curl -X GET "http://localhost:8080/api/service-app/list" \
  -H "Authorization: Bearer {从登录响应中获取的token}"
```

### 5. 前端自动管理
前端会自动：
- 保存token到localStorage
- 在所有AJAX请求中自动添加Authorization头
- 处理token过期时自动跳转到登录页面

## 🛡️ 三层Token保护机制

### 第一层：AdminAjax封装
```javascript
AdminAjax.get('/api/service-app/list')
  .then(response => {
    console.log('请求成功，token已自动添加');
  });
```

### 第二层：双层保险检查
- 在`beforeSend`中检查并添加token
- 合并用户自定义的`beforeSend`函数

### 第三层：全局AJAX拦截器
- 监听所有jQuery AJAX请求
- 自动为没有Authorization头的请求添加token
- 全局处理401错误

### Token调试
打开浏览器开发者工具，在Console中可以看到：
```
🔑 AdminAjax: Token added to request - eyJ0eXAiOiJKV1QiLC...
🔑 Global: Token added by interceptor - eyJ0eXAiOiJKV1QiLC...
```

## 🧪 完整测试流程

### 1. 启动应用
```bash
mvn spring-boot:run -pl server
```

### 2. 访问登录页面
打开浏览器访问：`http://localhost:8080/admin/login`

### 3. 打开开发者工具
按F12打开浏览器开发者工具，切换到Console标签页

### 4. 登录并观察Token
```bash
# 登录
curl -X POST "http://localhost:8080/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 5. 验证Token自动携带
在浏览器Console中输入：
```javascript
// 测试AdminAjax
AdminAjax.get('/api/service-app/list').then(r => console.log('响应:', r));

// 测试原生jQuery AJAX（会被全局拦截器处理）
$.ajax({
  url: '/api/service-app/list',
  method: 'GET',
  success: function(response) {
    console.log('原生AJAX响应:', response);
  }
});
```

### 6. 查看localStorage
```javascript
// 在Console中查看token
localStorage.getItem('admin_token');
AdminToken.getToken();
```

### 7. 测试Token是否正确发送
```javascript
// 在Network标签页中查看请求头
AdminAjax.get('/api/service-app/list')
  .then(r => console.log('API响应:', r))
  .catch(e => console.log('API错误:', e));
```

## 📋 故障排除

### 如果token没有被发送：
1. **检查Console日志**：查看是否有"🔑 AdminAjax: Token added to request"消息
2. **检查localStorage**：确认token是否正确保存
3. **检查Network标签页**：查看请求头是否包含Authorization字段

### 如果收到401错误：
1. **检查token格式**：确认token是有效的JWT格式
2. **检查token过期**：JWT token可能已经过期
3. **检查后端配置**：确认后端正确配置了JWT验证

### 页面跳转后的Token保持
**问题**: 登录成功后跳转到其他页面时，token可能丢失。

**解决方案**:
1. **localStorage持久化**: token自动保存到localStorage
2. **页面重新加载**: 每个页面都会重新加载admin.js并检查登录状态
3. **自动重定向**: 未登录时自动跳转到登录页面
4. **状态显示**: 导航栏和页面中显示当前登录状态

### 登录状态检查流程
1. **页面加载** → 加载admin.js
2. **检查localStorage** → 查找保存的token
3. **验证token** → 检查token格式和有效性
4. **设置全局拦截器** → 为所有AJAX请求添加token
5. **更新UI状态** → 显示登录状态指示器

### 调试命令：
```javascript
// 1. 检查token是否存在
console.log('Token in localStorage:', localStorage.getItem('admin_token'));

// 2. 测试AdminToken方法
console.log('Token via AdminToken:', AdminToken.getToken());

// 3. 检查登录状态
console.log('Is logged in:', AdminToken.isLoggedIn());

// 4. 手动测试API调用
AdminAjax.get('/api/service-app/list')
  .then(response => console.log('✅ 成功:', response))
  .catch(error => console.log('❌ 失败:', error));

// 5. 检查页面状态
console.log('Current page:', window.location.pathname);
console.log('AdminToken loaded:', typeof AdminToken !== 'undefined');
console.log('AdminAjax loaded:', typeof AdminAjax !== 'undefined');
```

### 状态指示器
- **导航栏**: 显示当前登录状态和token前缀
- **控制台**: 详细的初始化和状态检查日志
- **页面内容**: 所有管理页面都显示token状态
- **实时更新**: 页面间跳转时状态自动更新

## 📋 所有管理页面都支持Token保持

### 完整的页面列表
| 页面路径 | 页面名称 | Token保持 | 状态显示 |
|----------|----------|-----------|----------|
| `/admin/dashboard` | 系统概览 | ✅ | ✅ |
| `/admin/users` | 用户管理 | ✅ | ✅ |
| `/admin/apps` | 应用管理 | ✅ | ✅ |
| `/admin/endpoints` | 接口管理 | ✅ | ✅ |
| `/admin/tokens` | Token管理 | ✅ | ✅ |
| `/admin/permissions` | 权限分配 | ✅ | ✅ |
| `/admin/logs` | 系统日志 | ✅ | ✅ |
| `/admin/monitor` | 系统监控 | ✅ | ✅ |

### 页面跳转场景测试
1. **登录后跳转**: 登录成功 → 自动跳转到dashboard → token保持
2. **页面间导航**: dashboard → 用户管理 → token保持
3. **刷新页面**: 刷新任意管理页面 → token自动恢复
4. **新开标签**: 在新标签页打开管理页面 → 自动检查登录状态
5. **返回登录**: token过期时自动跳转到登录页面

### 常见问题排查
1. **页面跳转后token丢失**:
   - 检查localStorage是否被清除
   - 查看浏览器控制台的初始化日志

2. **AJAX请求无token**:
   - 检查全局拦截器是否设置
   - 查看Network标签页的请求头

3. **401错误**:
   - 检查token格式是否正确
   - 确认token是否过期

## 🔗 URL参数登录功能

### 功能介绍
支持通过URL参数直接登录，实现一键登录功能。

### 使用方式
```bash
# 基本格式
GET /admin/login?username={username}&password={password}

# 示例
GET /admin/login?username=admin&password=admin123
GET /admin/login?username=testuser&password=test123
```

### 工作流程
1. **接收URL参数** → 解析用户名和密码
2. **用户注册/登录** → 自动创建用户或验证登录
3. **生成Token** → 生成JWT访问令牌
4. **页面渲染** → 返回登录页面并传递token
5. **自动保存** → 前端JavaScript自动保存token
6. **页面跳转** → 自动跳转到dashboard页面

### 测试页面
访问测试页面体验URL参数登录功能：
```
http://localhost:8080/test-url-login.html
```

### 安全注意事项
- ✅ **参数验证**: 严格验证用户名和密码格式
- ✅ **密码保护**: 密码不会在页面中明文显示
- ✅ **Token安全**: 使用JWT标准加密
- ✅ **自动清理**: 登录失败时不保存任何信息

## 🔧 更新后的API调用方式

### 服务应用注册
```bash
curl -X POST "http://localhost:8080/api/service-app/register" \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {admin_token}" \
     -d '{
       "appName": "测试服务",
       "allowedApis": ["/api/test/*", "/api/common/health"],
       "createBy": "admin",
       "remark": "测试用服务"
     }'
```

### 服务应用更新
```bash
curl -X PUT "http://localhost:8080/api/service-app/update" \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {admin_token}" \
     -d '{
       "id": 1,
       "appName": "测试服务-更新",
       "allowedApis": ["/api/test/*", "/api/common/*"],
       "updateBy": "admin",
       "remark": "更新后的测试服务"
     }'
```

### Token签发
```bash
curl -X POST "http://localhost:8080/api/service-token/issue" \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {admin_token}" \
     -d '{
       "appId": "1750123456789012345",
       "authCode": "abc123def456ghi789",
       "issueBy": "admin"
     }'
```

### Token重新生成
```bash
curl -X POST "http://localhost:8080/api/service-token/regenerate" \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {admin_token}" \
     -d '{
       "appId": "1750123456789012345",
       "authCode": "abc123def456ghi789",
       "issueBy": "admin"
     }'
```

## 📋 DTO类结构

### ServiceAppRegisterDTO
```java
{
    "appName": "string",       // 应用名称（必填）
    "allowedApis": ["string"], // 允许访问的接口列表（必填）
    "createBy": "string",      // 创建者（可选）
    "remark": "string"         // 备注（可选）
}
```

### ServiceAppUpdateDTO
```java
{
    "id": 1,                   // 应用ID（必填）
    "appName": "string",       // 应用名称（可选）
    "allowedApis": ["string"], // 允许访问的接口列表（可选）
    "updateBy": "string",      // 更新者（可选）
    "remark": "string"         // 备注（可选）
}
```

### ServiceTokenIssueDTO
```java
{
    "appId": "string",         // 应用ID（必填）
    "authCode": "string",      // 授权码（必填）
    "issueBy": "string"        // 签发者（可选）
}
```

## ✅ 优势

1. **参数绑定清晰**：避免混合使用@RequestParam和@RequestBody导致的绑定问题
2. **JSON格式统一**：所有请求都使用JSON格式，便于前端调用
3. **类型安全**：DTO提供强类型检查，减少参数错误
4. **可扩展性**：便于后续添加字段验证和业务逻辑
5. **代码清晰**：Controller层代码更加简洁明了

## 🎯 使用建议

- 所有POST/PUT请求使用JSON格式
- 请求头必须包含`Content-Type: application/json`
- 管理操作需要admin权限的Token
- 建议使用Postman或类似工具进行API测试
