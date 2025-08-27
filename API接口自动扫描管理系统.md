# 🚀 API接口自动扫描管理系统

## 📖 系统概述

本系统实现了**Java Spring Boot应用的API接口自动发现和管理功能**，能够在系统启动时自动扫描所有Controller接口，并提供完整的接口管理能力。

### 🎯 核心功能

1. **🔍 自动扫描**：系统启动时自动扫描所有`@RestController`和`@Controller`标注的类
2. **📊 智能解析**：解析类级别和方法级别的`@RequestMapping`等注解
3. **💾 数据持久化**：接口信息自动存储到数据库，支持增量更新
4. **🔎 高级搜索**：提供分页查询、关键词搜索、模块分组过滤
5. **⚙️ 接口管理**：支持接口信息的更新、状态管理
6. **🔄 手动刷新**：提供手动重新扫描和强制刷新功能

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    API接口扫描管理系统                          │
├─────────────────────────────────────────────────────────────┤
│  启动扫描层：ApplicationReadyEvent + ApplicationRunner      │
├─────────────────────────────────────────────────────────────┤
│  服务层：ApiEndpointService (扫描、存储、查询)                 │
├─────────────────────────────────────────────────────────────┤
│  控制层：ApiEndpointController (RESTful API)                │
├─────────────────────────────────────────────────────────────┤
│  数据层：ApiEndpointMapper + MySQL                         │
└─────────────────────────────────────────────────────────────┘
```

## 📁 文件结构

```
security/src/main/java/com/ssy/
├── entity/
│   └── ApiEndpointEntity.java              # 接口信息实体类
├── mapper/
│   └── ApiEndpointMapper.java              # 数据访问层
├── service/
│   ├── ApiEndpointService.java             # 服务接口
│   └── impl/
│       └── ApiEndpointServiceImpl.java     # 服务实现类
├── controller/
│   └── ApiEndpointController.java          # RESTful控制器
└── config/
    └── ApiEndpointScannerConfig.java       # 启动扫描配置

api_endpoints.sql                           # 数据库建表脚本
```

## 🗄️ 数据库设计

### 表结构：`api_endpoints`

| 字段名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `id` | BIGINT | 主键ID | 1 |
| `path` | VARCHAR(500) | 接口路径 | `/api/service-app/register` |
| `method` | VARCHAR(20) | HTTP方法 | `POST` |
| `controller_class` | VARCHAR(255) | 控制器类名 | `ServiceAppController` |
| `controller_method` | VARCHAR(100) | 控制器方法名 | `registerApp` |
| `base_path` | VARCHAR(200) | 根路径 | `/api/service-app` |
| `description` | VARCHAR(500) | 接口描述 | `注册服务应用` |
| `require_auth` | TINYINT | 是否需要认证 | 1 |
| `module_group` | VARCHAR(100) | 模块分组 | `服务管理` |
| `status` | TINYINT | 启用状态 | 1 |
| `create_time` | DATETIME | 创建时间 | `2025-01-27 10:30:00` |
| `update_time` | DATETIME | 更新时间 | `2025-01-27 10:30:00` |
| `remark` | VARCHAR(500) | 备注 | 仅管理员可访问 |

### 索引设计

- **唯一索引**：`uk_path_method` (path, method) - 防重复
- **普通索引**：`idx_controller_class`, `idx_module_group`, `idx_status`

## 🔧 核心功能详解

### 1. 🚀 启动时自动扫描

**扫描触发时机**：
- `ApplicationReadyEvent` - 应用完全启动后
- `ApplicationRunner` - 备选方案，确保扫描执行

**扫描逻辑**：
1. 获取所有带`@RestController`和`@Controller`注解的Bean
2. 解析类级别的`@RequestMapping`获取基础路径
3. 扫描所有方法的映射注解（`@GetMapping`, `@PostMapping`等）
4. 提取接口描述（`@ApiOperation`注解）
5. 确定模块分组（`@Api`注解的tags）
6. 增量插入到数据库（已存在则更新）

**智能解析示例**：
```java
@Api(tags = "服务管理")
@RestController
@RequestMapping("/api/service-app")
public class ServiceAppController {
    
    @ApiOperation("注册服务应用")
    @PostMapping("/register")
    public Result<ServiceAppEntity> registerApp() { ... }
}
```

解析结果：
- **路径**：`/api/service-app/register`
- **方法**：`POST`
- **模块分组**：`服务管理`
- **描述**：`注册服务应用`

### 2. 📊 分页查询和搜索

#### 分页查询接口
```http
GET /api/endpoints/page?page=1&size=20&keyword=register&moduleGroup=服务管理
```

**查询参数**：
- `page`：页码（默认1）
- `size`：每页大小（默认20）
- `keyword`：搜索关键词（支持路径、描述、类名模糊搜索）
- `moduleGroup`：模块分组过滤

**响应示例**：
```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "records": [
            {
                "id": 1,
                "path": "/api/service-app/register",
                "method": "POST",
                "controllerClass": "ServiceAppController",
                "controllerMethod": "registerApp",
                "basePath": "/api/service-app",
                "description": "注册服务应用",
                "requireAuth": 1,
                "moduleGroup": "服务管理",
                "status": 1,
                "createTime": "2025-01-27T10:30:00",
                "updateTime": "2025-01-27T10:30:00"
            }
        ],
        "total": 25,
        "page": 1,
        "size": 20,
        "totalPages": 2
    }
}
```

### 3. 🔎 高级搜索功能

#### 关键词搜索
```http
GET /api/endpoints/search?keyword=token&page=1&size=10
```

**搜索范围**：
- 接口路径（path）
- 接口描述（description）
- 控制器类名（controller_class）

#### 模块分组查询
```http
GET /api/endpoints/by-module/服务管理?page=1&size=10
```

#### 获取所有分组
```http
GET /api/endpoints/modules
```

### 4. ⚙️ 接口管理功能

#### 更新接口信息
```http
PUT /api/endpoints/{id}
Content-Type: application/json

{
    "description": "更新后的描述",
    "requireAuth": 1,
    "moduleGroup": "权限管理",
    "status": 1,
    "remark": "管理员接口"
}
```

#### 手动扫描新接口
```http
POST /api/endpoints/scan
```

#### 强制重新扫描
```http
POST /api/endpoints/rescan
```

## 🎯 使用场景

### 1. **API文档自动生成**
- 系统启动后自动生成完整的API清单
- 配合Swagger生成更完整的API文档

### 2. **权限管理集成**
- 与现有的服务权限系统集成
- 为`ServicePermissionFilter`提供接口清单

### 3. **API监控和治理**
- 监控接口的使用情况
- 识别废弃的接口
- API版本管理

### 4. **开发调试**
- 快速查找接口位置
- 接口分类和管理
- 新接口自动发现

## 🔄 集成配置

### 1. 数据库初始化
```sql
-- 执行建表脚本
mysql -u root -p123456 < api_endpoints.sql
```

### 2. 启动应用
```bash
# 编译项目
mvn clean compile -DskipTests

# 启动应用
mvn spring-boot:run -pl server
```

### 3. 查看扫描日志
```
🚀 应用启动完成，开始自动扫描API接口...
📊 扫描结果：新增 25 个接口，耗时 150 ms
✅ API接口自动扫描完成！
```

## 🎨 前端集成示例

### Vue.js集成
```javascript
// 获取接口列表
async getApiEndpoints(page = 1, size = 20, keyword = '') {
    const response = await axios.get('/api/endpoints/page', {
        params: { page, size, keyword }
    });
    return response.data;
}

// 搜索接口
async searchEndpoints(keyword) {
    const response = await axios.get('/api/endpoints/search', {
        params: { keyword }
    });
    return response.data;
}

// 手动扫描
async scanEndpoints() {
    const response = await axios.post('/api/endpoints/scan');
    return response.data;
}
```

### React集成
```javascript
import { useState, useEffect } from 'react';

function ApiEndpointManager() {
    const [endpoints, setEndpoints] = useState([]);
    const [loading, setLoading] = useState(false);
    
    const loadEndpoints = async (page = 1, keyword = '') => {
        setLoading(true);
        try {
            const response = await fetch(
                `/api/endpoints/page?page=${page}&keyword=${keyword}`
            );
            const data = await response.json();
            setEndpoints(data.data.records);
        } finally {
            setLoading(false);
        }
    };
    
    useEffect(() => {
        loadEndpoints();
    }, []);
    
    return (
        <div>
            {/* 接口列表渲染 */}
        </div>
    );
}
```

## 📈 性能优化

### 1. **扫描性能**
- 使用反射缓存减少重复解析
- 批量插入优化数据库操作
- 异步扫描避免阻塞启动

### 2. **查询性能**
- 合理的数据库索引设计
- 分页查询减少内存占用
- 查询结果缓存（可选）

### 3. **内存优化**
- 扫描完成后释放反射对象
- 分批处理大量接口
- 避免循环引用

## 🛡️ 安全考虑

### 1. **权限控制**
- 接口管理功能仅限管理员访问
- 使用`@PreAuthorize("hasRole('ADMIN')")`

### 2. **数据验证**
- 输入参数校验
- SQL注入防护
- XSS攻击防护

### 3. **敏感信息**
- 不暴露内部实现细节
- 过滤敏感的控制器方法

## 🔧 配置选项

### application.yml配置
```yaml
# API扫描配置
api:
  scanner:
    enabled: true                    # 是否启用自动扫描
    scan-on-startup: true           # 启动时是否扫描
    include-packages:               # 包含的包路径
      - "com.ssy.controller"
      - "com.main.controller"
    exclude-patterns:               # 排除的路径模式
      - "/error"
      - "/actuator/**"
    auto-update: true               # 是否自动更新已存在的接口
```

## 🚨 故障排查

### 常见问题

1. **扫描不到接口**
   - 检查Controller是否有`@RestController`或`@Controller`注解
   - 确认方法有正确的映射注解
   - 查看启动日志中的扫描信息

2. **数据库连接失败**
   - 确认数据库配置正确
   - 检查表是否创建成功
   - 验证数据库权限

3. **权限访问被拒绝**
   - 确认用户有ADMIN角色
   - 检查JWT Token是否有效
   - 验证请求头是否正确

### 调试模式
```yaml
logging:
  level:
    com.ssy.service.impl.ApiEndpointServiceImpl: DEBUG
    com.ssy.config.ApiEndpointScannerConfig: DEBUG
```

## 🎉 总结

API接口自动扫描管理系统为Spring Boot应用提供了完整的接口治理能力：

✅ **自动发现**：无需手动维护接口清单  
✅ **智能解析**：支持多种注解和路径模式  
✅ **高效管理**：分页、搜索、分组一应俱全  
✅ **权限控制**：安全的管理接口访问  
✅ **易于集成**：RESTful API便于前端集成  
✅ **性能优化**：启动快速，查询高效  

这个系统将大大提升API管理效率，为微服务架构下的接口治理提供强大支撑！🚀
