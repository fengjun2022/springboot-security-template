# 📋 API接口扫描测试指南

## 🚀 快速测试步骤

### 1. 启动应用并观察扫描日志

```bash
# 启动应用
mvn spring-boot:run -pl server

# 观察控制台输出，应该看到类似以下日志：
🚀 应用启动完成，开始自动扫描API接口...
📊 扫描结果：新增 X 个接口，耗时 XX ms
✅ API接口自动扫描完成！
```

### 2. 查询扫描结果

#### 获取所有接口（分页）
```bash
curl -X GET "http://localhost:8080/api/endpoints/page?page=1&size=20"
```

#### 搜索包含"test"的接口
```bash
curl -X GET "http://localhost:8080/api/endpoints/search?keyword=test&page=1&size=10"
```

#### 获取所有模块分组
```bash
curl -X GET "http://localhost:8080/api/endpoints/modules"
```

#### 按模块查询（例如：测试接口）
```bash
curl -X GET "http://localhost:8080/api/endpoints/by-module/测试接口?page=1&size=10"
```

### 3. 测试管理功能（需要admin权限）

#### 手动扫描新接口
```bash
curl -X POST "http://localhost:8080/api/endpoints/scan" \
     -H "Authorization: Bearer {admin_token}"
```

#### 强制重新扫描所有接口
```bash
curl -X POST "http://localhost:8080/api/endpoints/rescan" \
     -H "Authorization: Bearer {admin_token}"
```

#### 更新接口信息
```bash
curl -X PUT "http://localhost:8080/api/endpoints/1" \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {admin_token}" \
     -d '{
       "description": "更新后的描述",
       "requireAuth": 0,
       "moduleGroup": "更新的分组",
       "status": 1,
       "remark": "这是一个备注"
     }'
```

## 🔍 预期扫描结果

系统应该能扫描到以下接口类型：

### ServiceAppController（服务管理）
- POST `/api/service-app/register` - 注册服务应用
- GET `/api/service-app/list` - 查询服务应用列表
- PUT `/api/service-app/update` - 更新服务应用
- DELETE `/api/service-app/{id}` - 删除服务应用

### ServiceTokenController（服务管理）
- POST `/api/service-token/issue` - 签发服务Token
- POST `/api/service-token/regenerate` - 重新生成Token
- DELETE `/api/service-token/invalidate` - 失效Token

### PermissionCacheController（权限管理）
- POST `/api/permission-cache/init` - 初始化权限缓存
- POST `/api/permission-cache/refresh/{appId}` - 刷新应用权限
- DELETE `/api/permission-cache/remove/{appId}` - 移除应用权限

### TestApiController（测试接口）
- GET `/api/test/example` - 测试GET接口
- POST `/api/test/example` - 测试POST接口
- GET `/api/test/param/{id}` - 测试带参数的接口
- GET `/api/test/query` - 测试查询参数接口

### NoBasepathController（无根路径测试）
- GET `/no-basepath` - 无根路径的接口

### ApiEndpointController（API接口管理）
- GET `/api/endpoints/page` - 分页查询API接口
- GET `/api/endpoints/search` - 搜索API接口
- GET `/api/endpoints/modules` - 获取所有模块分组
- POST `/api/endpoints/scan` - 手动扫描新增接口
- POST `/api/endpoints/rescan` - 强制重新扫描所有接口

## 📊 响应示例

### 分页查询响应
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "path": "/api/test/example",
        "method": "GET",
        "controllerClass": "TestApiController",
        "controllerMethod": "testGet",
        "basePath": "/api/test",
        "description": "测试GET接口",
        "requireAuth": 1,
        "moduleGroup": "测试接口",
        "status": 1,
        "createTime": "2025-01-27T10:30:00",
        "updateTime": "2025-01-27T10:30:00",
        "remark": null
      }
    ],
    "total": 25,
    "page": 1,
    "size": 20,
    "totalPages": 2
  }
}
```

### 模块分组响应
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    "服务管理",
    "权限管理",
    "测试接口",
    "无根路径测试",
    "API接口管理"
  ]
}
```

## 🧪 测试场景

### 场景1：验证自动扫描
1. 启动应用
2. 检查控制台日志确认扫描执行
3. 查询数据库或API确认接口已存储

### 场景2：验证增量扫描
1. 添加新的Controller或方法
2. 调用手动扫描接口
3. 确认新接口被添加

### 场景3：验证搜索功能
1. 使用不同关键词搜索
2. 验证搜索结果准确性
3. 测试分页功能

### 场景4：验证更新功能
1. 更新接口描述和分组
2. 确认数据库记录已更新
3. 验证更新时间字段

### 场景5：验证权限控制
1. 使用非admin用户访问管理接口
2. 确认返回403权限错误
3. 使用admin用户确认可以正常访问

## 🐛 常见问题排查

### 问题1：扫描不到接口
**可能原因**：
- Controller类没有正确的注解
- 方法没有映射注解
- 包路径不在扫描范围内

**解决方法**：
- 检查`@RestController`或`@Controller`注解
- 确认方法有`@GetMapping`等映射注解
- 查看启动日志中的扫描详情

### 问题2：重复接口问题
**可能原因**：
- 数据库唯一约束失效
- 路径解析错误

**解决方法**：
- 检查数据库表的唯一索引
- 查看解析后的路径是否正确

### 问题3：权限访问失败
**可能原因**：
- Token无效或过期
- 用户没有ADMIN角色

**解决方法**：
- 重新获取admin token
- 确认用户角色配置正确

## 📈 性能监控

### 监控指标
- 扫描耗时
- 扫描接口数量
- 数据库操作次数
- 内存使用情况

### 优化建议
- 如果扫描耗时过长，考虑异步扫描
- 大量接口时可考虑分批处理
- 添加扫描结果缓存

这个测试指南将帮助你全面验证API接口扫描系统的各项功能！🎯
