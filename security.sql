-- MySQL dump 10.13  Distrib 8.3.0, for macos14 (arm64)
--
-- Host: 192.168.5.249    Database: tem
-- ------------------------------------------------------
-- Server version	8.4.3

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `user`
--

-- 注意：该脚本可能被程序启动时自动执行，用户表禁止使用 DROP TABLE 防止误删数据
-- DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
-- auto-generated definition
CREATE TABLE IF NOT EXISTS `user`
(
    id          bigint auto_increment comment 'userID'
        primary key,
    username    varchar(255)  not null comment '用户名',
    password    varchar(255)  not null comment '密码',
    authorities json          null comment '权限',
    status      int default 0 null comment '账户状态 0 启用 1禁用',
    user_id     bigint        not null comment 'userid',
    constraint user_username_id_uindex
        unique (username, id)
)
    charset = utf8mb4;

create index user_user_id_index
    on user (user_id);




-- =================================================================
-- admin管理系统完整数据库脚本
-- 包含：服务权限管理 + API接口管理
-- 版本：v1.0
-- 创建时间：2025-01-27
-- =================================================================



-- =================================================================
-- 1. 服务应用管理表
-- =================================================================

-- 服务应用注册表
CREATE TABLE IF NOT EXISTS service_apps (
                                            id BIGINT NOT NULL COMMENT '主键ID（雪花ID）',
                                            app_name VARCHAR(100) NOT NULL COMMENT '应用名称',
                                            app_id VARCHAR(50) NOT NULL COMMENT '应用ID（雪花ID）',
                                            auth_code VARCHAR(255) NOT NULL COMMENT '授权码',
                                            allowed_api_list TEXT COMMENT '允许访问的接口列表（JSON格式）',
                                            status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
                                            create_by VARCHAR(50) COMMENT '创建人',
                                            create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                            update_by VARCHAR(50) COMMENT '更新人',
                                            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                            remark VARCHAR(500) COMMENT '备注',
                                            PRIMARY KEY (id),
                                            UNIQUE KEY uk_app_name (app_name) COMMENT '应用名称唯一索引',
                                            UNIQUE KEY uk_app_id (app_id) COMMENT '应用ID唯一索引',
                                            INDEX idx_status (status) COMMENT '状态索引',
                                            INDEX idx_create_time (create_time) COMMENT '创建时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务应用注册表';

-- =================================================================
-- 2. 服务Token管理表
-- =================================================================

-- 服务Token表
CREATE TABLE IF NOT EXISTS service_tokens (
                                              id BIGINT NOT NULL COMMENT '主键ID（雪花ID）',
                                              app_id VARCHAR(50) NOT NULL COMMENT '应用ID',
                                              token TEXT NOT NULL COMMENT 'JWT Token',
                                              token_type VARCHAR(20) DEFAULT 'permanent' COMMENT 'Token类型：permanent-永久',
                                              issue_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
                                              last_used_time DATETIME COMMENT '最后使用时间',
                                              is_valid TINYINT DEFAULT 1 COMMENT '是否有效：0-无效，1-有效',
                                              issue_by VARCHAR(50) COMMENT '签发人',
                                              remark VARCHAR(500) COMMENT '备注',
                                              PRIMARY KEY (id),
                                              UNIQUE KEY uk_app_id (app_id) COMMENT '应用ID唯一索引（一个应用只能有一个有效Token）',
                                              INDEX idx_issue_time (issue_time) COMMENT '签发时间索引',
                                              INDEX idx_is_valid (is_valid) COMMENT '有效性索引',
                                              CONSTRAINT fk_service_tokens_app_id FOREIGN KEY (app_id) REFERENCES service_apps (app_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务Token表';

-- =================================================================
-- 3. API接口管理表
-- =================================================================

-- API接口信息表
CREATE TABLE IF NOT EXISTS api_endpoints (
                                             id BIGINT AUTO_INCREMENT COMMENT '主键ID',
                                             path VARCHAR(500) NOT NULL COMMENT '接口路径',
                                             method VARCHAR(20) NOT NULL COMMENT 'HTTP方法',
                                             controller_class VARCHAR(255) NOT NULL COMMENT '控制器类名',
                                             controller_method VARCHAR(100) NOT NULL COMMENT '控制器方法名',
                                             base_path VARCHAR(200) COMMENT '根路径(类级别RequestMapping)',
                                             description VARCHAR(500) COMMENT '接口描述',
                                             auth VARCHAR(1000) COMMENT '权限表达式(如@PreAuthorize/@Secured等)',
                                             threat_monitor_enabled TINYINT DEFAULT 1 COMMENT '异常识别监控开关(0-白名单直通,1-启用监控)',
                                             require_auth TINYINT DEFAULT 1 COMMENT '是否需要认证(0-不需要,1-需要)',
                                             module_group VARCHAR(100) COMMENT '接口分组/模块',
                                             status TINYINT DEFAULT 1 COMMENT '是否启用(0-禁用,1-启用)',
                                             create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                             update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                             remark VARCHAR(500) COMMENT '备注',
                                             PRIMARY KEY (id),
                                             UNIQUE KEY uk_path_method (path, method) COMMENT '路径和方法的唯一索引',
                                             INDEX idx_controller_class (controller_class) COMMENT '控制器类索引',
                                             INDEX idx_module_group (module_group) COMMENT '模块分组索引',
                                             INDEX idx_status (status) COMMENT '状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API接口信息表';

-- 兼容旧版本表结构：补充 auth 字段（若历史库缺失该列）
SET @ddl_add_api_endpoints_auth = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'api_endpoints'
              AND COLUMN_NAME = 'auth'
        ),
        'SELECT ''api_endpoints.auth exists''',
        'ALTER TABLE api_endpoints ADD COLUMN auth VARCHAR(1000) NULL COMMENT ''权限表达式(如@PreAuthorize/@Secured等)'' AFTER description'
    )
);
PREPARE stmt_add_api_endpoints_auth FROM @ddl_add_api_endpoints_auth;
EXECUTE stmt_add_api_endpoints_auth;
DEALLOCATE PREPARE stmt_add_api_endpoints_auth;

-- 兼容旧版本表结构：补充 threat_monitor_enabled 字段（若历史库缺失该列）
SET @ddl_add_api_endpoints_threat_monitor = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'api_endpoints'
              AND COLUMN_NAME = 'threat_monitor_enabled'
        ),
        'SELECT ''api_endpoints.threat_monitor_enabled exists''',
        'ALTER TABLE api_endpoints ADD COLUMN threat_monitor_enabled TINYINT DEFAULT 1 COMMENT ''异常识别监控开关(0-白名单直通,1-启用监控)'' AFTER auth'
    )
);
PREPARE stmt_add_api_endpoints_threat_monitor FROM @ddl_add_api_endpoints_threat_monitor;
EXECUTE stmt_add_api_endpoints_threat_monitor;
DEALLOCATE PREPARE stmt_add_api_endpoints_threat_monitor;

-- =================================================================
-- 4. 安全异常识别与IP黑白名单表
-- =================================================================

CREATE TABLE IF NOT EXISTS security_ip_blacklist (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    ip VARCHAR(64) NOT NULL COMMENT 'IP地址',
    status TINYINT DEFAULT 1 COMMENT '状态(0-失效,1-生效)',
    source VARCHAR(32) DEFAULT 'AUTO' COMMENT '来源(AUTO/MANUAL)',
    reason VARCHAR(500) COMMENT '拉黑原因',
    attack_type VARCHAR(64) COMMENT '触发攻击类型',
    hit_count INT DEFAULT 1 COMMENT '命中次数',
    first_hit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次命中时间',
    last_hit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近命中时间',
    expire_time DATETIME NULL COMMENT '过期时间(为空表示永久)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_blacklist_ip (ip),
    INDEX idx_blacklist_status_expire (status, expire_time),
    INDEX idx_blacklist_last_hit (last_hit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全IP黑名单';

CREATE TABLE IF NOT EXISTS security_ip_whitelist (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    ip_or_cidr VARCHAR(64) NOT NULL COMMENT 'IP或CIDR',
    status TINYINT DEFAULT 1 COMMENT '状态(0-失效,1-生效)',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_whitelist_ip_cidr (ip_or_cidr),
    INDEX idx_whitelist_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全IP白名单';

CREATE TABLE IF NOT EXISTS security_attack_event (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    ip VARCHAR(64) NOT NULL COMMENT '请求IP',
    attack_type VARCHAR(64) NOT NULL COMMENT '攻击类型',
    path VARCHAR(500) NOT NULL COMMENT '请求路径',
    method VARCHAR(16) NOT NULL COMMENT '请求方法',
    endpoint_id BIGINT NULL COMMENT '命中的api_endpoints主键',
    username VARCHAR(128) NULL COMMENT '登录用户名',
    app_id VARCHAR(128) NULL COMMENT '服务调用appId',
    user_agent VARCHAR(1000) NULL COMMENT 'User-Agent',
    referer VARCHAR(1000) NULL COMMENT 'Referer',
    query_string VARCHAR(2000) NULL COMMENT '查询串',
    request_body_sample TEXT NULL COMMENT '请求体样本(截断)',
    request_body_hash VARCHAR(64) NULL COMMENT '请求体Hash',
    risk_score INT DEFAULT 0 COMMENT '风险分值',
    block_action VARCHAR(32) NOT NULL COMMENT '处理动作(ALLOW/BLOCK/BLACKLIST)',
    block_reason VARCHAR(1000) NOT NULL COMMENT '拦截/记录原因',
    suggested_action VARCHAR(500) NULL COMMENT '建议处理方式',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    INDEX idx_attack_event_ip_time (ip, create_time),
    INDEX idx_attack_event_type_time (attack_type, create_time),
    INDEX idx_attack_event_path_time (path(255), create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全攻击异常事件记录';

-- =================================================================
-- 5. 标准RBAC模型（账号 / 角色 / 权限 / 关系 / 授权规则）
-- =================================================================

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码(唯一)',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    status TINYINT DEFAULT 1 COMMENT '状态(0-禁用,1-启用)',
    is_system TINYINT DEFAULT 0 COMMENT '是否系统内置角色',
    allow_self_register TINYINT DEFAULT 0 COMMENT '是否允许自注册选择该角色',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code),
    INDEX idx_sys_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RBAC角色表';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    perm_code VARCHAR(128) NOT NULL COMMENT '权限编码(如 iam:user:create)',
    perm_name VARCHAR(128) NOT NULL COMMENT '权限名称',
    module_group VARCHAR(100) NULL COMMENT '所属模块',
    status TINYINT DEFAULT 1 COMMENT '状态(0-禁用,1-启用)',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (perm_code),
    INDEX idx_sys_permission_module (module_group),
    INDEX idx_sys_permission_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RBAC权限表';

CREATE TABLE IF NOT EXISTS sys_permission_endpoint_rel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID(sys_permission.id)',
    endpoint_id BIGINT NOT NULL COMMENT '接口ID(api_endpoints.id)',
    status TINYINT DEFAULT 1 COMMENT '状态(0-禁用,1-启用)',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_endpoint_rel (permission_id, endpoint_id),
    INDEX idx_sys_permission_endpoint_rel_endpoint (endpoint_id, status),
    INDEX idx_sys_permission_endpoint_rel_permission (permission_id, status),
    CONSTRAINT fk_sys_perm_endpoint_rel_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id),
    CONSTRAINT fk_sys_perm_endpoint_rel_endpoint FOREIGN KEY (endpoint_id) REFERENCES api_endpoints(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限-接口资源绑定表(动态接口RBAC)';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户业务ID(user.user_id)',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    source VARCHAR(32) DEFAULT 'ADMIN' COMMENT '来源(SELF/ADMIN/SYSTEM)',
    status TINYINT DEFAULT 1 COMMENT '状态(0-失效,1-有效)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    INDEX idx_sys_user_role_user (user_id, status),
    INDEX idx_sys_user_role_role (role_id, status),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    INDEX idx_sys_role_permission_role (role_id),
    INDEX idx_sys_role_permission_perm (permission_id),
    CONSTRAINT fk_sys_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_role_perm_perm FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

CREATE TABLE IF NOT EXISTS sys_role_grant_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    operator_role_id BIGINT NOT NULL COMMENT '操作者角色ID',
    target_role_id BIGINT NOT NULL COMMENT '目标角色ID',
    can_create_user_with_role TINYINT DEFAULT 0 COMMENT '是否可创建该角色账号',
    can_assign_role TINYINT DEFAULT 0 COMMENT '是否可赋予该角色',
    can_revoke_role TINYINT DEFAULT 0 COMMENT '是否可撤销该角色',
    can_update_user_of_role TINYINT DEFAULT 0 COMMENT '是否可修改拥有该角色的用户',
    status TINYINT DEFAULT 1 COMMENT '状态(0-禁用,1-启用)',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_grant_rule (operator_role_id, target_role_id),
    INDEX idx_sys_role_grant_rule_operator (operator_role_id, status),
    INDEX idx_sys_role_grant_rule_target (target_role_id, status),
    CONSTRAINT fk_sys_role_grant_rule_operator FOREIGN KEY (operator_role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_role_grant_rule_target FOREIGN KEY (target_role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色授予规则表(动态配置谁能创建/赋予哪些角色)';

-- RBAC默认角色
INSERT INTO sys_role (id, role_code, role_name, status, is_system, allow_self_register, remark)
VALUES
    (1, 'SUPER_ADMIN', '系统管理员', 1, 1, 0, '系统内置超级管理员'),
    (2, 'ADMIN', '管理员', 1, 1, 0, '系统内置管理员'),
    (3, 'USER', '普通用户', 1, 1, 1, '默认自注册角色')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status),
    is_system = VALUES(is_system),
    allow_self_register = VALUES(allow_self_register),
    remark = VALUES(remark);

-- RBAC默认权限（可按需扩展）
INSERT INTO sys_permission (id, perm_code, perm_name, module_group, status, remark)
VALUES
    (1001, 'iam:user:create', '创建账号', 'IAM-用户', 1, '管理员创建账号'),
    (1002, 'iam:user:update', '修改账号基础信息', 'IAM-用户', 1, '修改用户名/邮箱/手机号等'),
    (1003, 'iam:user:status:update', '修改账号状态', 'IAM-用户', 1, '启用/禁用账号'),
    (1004, 'iam:user:role:assign', '赋予用户角色', 'IAM-用户', 1, '给用户增加角色'),
    (1005, 'iam:user:role:revoke', '撤销用户角色', 'IAM-用户', 1, '移除用户角色'),
    (1006, 'iam:user:read', '查看用户信息', 'IAM-用户', 1, '查询用户/角色/权限'),
    (1101, 'iam:role:create', '创建角色', 'IAM-角色', 1, '创建角色'),
    (1102, 'iam:role:update', '修改角色', 'IAM-角色', 1, '修改角色名称/状态/自注册策略'),
    (1103, 'iam:role:read', '查看角色', 'IAM-角色', 1, '查看角色列表和详情'),
    (1201, 'iam:permission:create', '创建权限', 'IAM-权限', 1, '创建权限点'),
    (1202, 'iam:permission:update', '修改权限', 'IAM-权限', 1, '修改权限点'),
    (1203, 'iam:permission:read', '查看权限', 'IAM-权限', 1, '查看权限列表'),
    (1301, 'iam:role:permission:bind', '角色绑定权限', 'IAM-角色权限', 1, '设置角色权限'),
    (1302, 'iam:grant-rule:read', '查看角色授予规则', 'IAM-授权规则', 1, '查看动态角色授予规则'),
    (1303, 'iam:grant-rule:update', '修改角色授予规则', 'IAM-授权规则', 1, '修改动态角色授予规则'),
    (1304, 'iam:endpoint:permission:read', '查看接口权限绑定', 'IAM-接口权限', 1, '查看接口与权限绑定关系'),
    (1305, 'iam:endpoint:permission:bind', '维护接口权限绑定', 'IAM-接口权限', 1, '配置接口绑定哪些权限'),
    (1501, 'api:endpoint:read', '查看API接口元数据', 'API接口扫描', 1, '查看api_endpoints扫描结果'),
    (1502, 'api:endpoint:manage', '管理API接口元数据', 'API接口扫描', 1, '更新/扫描/重扫API接口'),
    (1601, 'svc:app:create', '创建服务应用', '服务调用权限-应用', 1, '注册服务应用'),
    (1602, 'svc:app:read', '查看服务应用', '服务调用权限-应用', 1, '查看服务应用信息'),
    (1603, 'svc:app:update', '修改服务应用', '服务调用权限-应用', 1, '更新服务应用信息'),
    (1604, 'svc:app:status:update', '修改服务应用状态', '服务调用权限-应用', 1, '启用/禁用服务应用'),
    (1605, 'svc:app:delete', '删除服务应用', '服务调用权限-应用', 1, '删除服务应用'),
    (1606, 'svc:app:validate', '校验服务应用', '服务调用权限-应用', 1, '校验appId与authCode'),
    (1607, 'svc:app:permission:check', '校验服务接口权限', '服务调用权限-应用', 1, '校验服务是否有接口访问权限'),
    (1701, 'svc:token:read', '查看服务Token', '服务调用权限-Token', 1, '查询/验证服务Token'),
    (1702, 'svc:token:manage', '管理服务Token', '服务调用权限-Token', 1, '失效/重发服务Token'),
    (1801, 'svc:permission-cache:read', '查看权限缓存', '服务调用权限-缓存', 1, '查看缓存内容与状态'),
    (1802, 'svc:permission-cache:manage', '管理权限缓存', '服务调用权限-缓存', 1, '初始化/刷新/清空缓存'),
    (1901, 'test:admin', '测试接口-管理员', '测试接口', 1, '访问/test/b'),
    (1902, 'test:user', '测试接口-用户', '测试接口', 1, '访问/test/c'),
    (1401, 'threat:admin:read', '查看安全异常识别数据', '安全异常识别', 1, '查看威胁检测后台数据'),
    (1402, 'threat:admin:manage', '管理安全异常识别配置', '安全异常识别', 1, '黑白名单/监控开关管理')
ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    module_group = VALUES(module_group),
    status = VALUES(status),
    remark = VALUES(remark);

-- 角色-权限默认绑定
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES
    -- SUPER_ADMIN 拥有全部默认权限
    (1, 1001), (1, 1002), (1, 1003), (1, 1004), (1, 1005), (1, 1006),
    (1, 1101), (1, 1102), (1, 1103),
    (1, 1201), (1, 1202), (1, 1203),
    (1, 1301), (1, 1302), (1, 1303), (1, 1304), (1, 1305),
    (1, 1501), (1, 1502),
    (1, 1601), (1, 1602), (1, 1603), (1, 1604), (1, 1605), (1, 1606), (1, 1607),
    (1, 1701), (1, 1702),
    (1, 1801), (1, 1802),
    (1, 1901), (1, 1902),
    (1, 1401), (1, 1402),
    -- ADMIN 拥有常用管理权限（不包含角色授予规则修改）
    (2, 1001), (2, 1002), (2, 1003), (2, 1004), (2, 1005), (2, 1006),
    (2, 1103), (2, 1203), (2, 1304), (2, 1305),
    (2, 1501), (2, 1502),
    (2, 1601), (2, 1602), (2, 1603), (2, 1604), (2, 1605), (2, 1606), (2, 1607),
    (2, 1701), (2, 1702),
    (2, 1801), (2, 1802),
    (2, 1901),
    (2, 1401), (2, 1402),
    -- USER 仅可查看自身类信息（示例）
    (3, 1006),
    (3, 1902);

-- 角色授予规则默认配置（动态可改）
INSERT INTO sys_role_grant_rule
    (operator_role_id, target_role_id, can_create_user_with_role, can_assign_role, can_revoke_role, can_update_user_of_role, status, remark)
VALUES
    -- SUPER_ADMIN 可操作所有内置角色（含管理员）
    (1, 1, 1, 1, 1, 1, 1, '系统管理员可管理系统管理员（谨慎使用）'),
    (1, 2, 1, 1, 1, 1, 1, '系统管理员可管理管理员'),
    (1, 3, 1, 1, 1, 1, 1, '系统管理员可管理普通用户'),
    -- ADMIN 仅可管理普通用户
    (2, 3, 1, 1, 1, 1, 1, '管理员仅可管理普通用户')
ON DUPLICATE KEY UPDATE
    can_create_user_with_role = VALUES(can_create_user_with_role),
    can_assign_role = VALUES(can_assign_role),
    can_revoke_role = VALUES(can_revoke_role),
    can_update_user_of_role = VALUES(can_update_user_of_role),
    status = VALUES(status),
    remark = VALUES(remark);

-- 默认超级管理员账号（首次初始化）
INSERT INTO `user` (username, password, status, user_id)
SELECT 'superadmin', '{bcrypt}$2a$10$8vqzICJXGHzpOMO/5qYJ/eNObyhH2h80/OogWEr4FdaUx6mQG3ff.', 0, 1000001
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'superadmin');

-- 兼容旧库：将历史遗留的 superadmin 明文/{noop} 初始化密码升级为 Spring Security bcrypt
UPDATE `user`
SET password = '{bcrypt}$2a$10$8vqzICJXGHzpOMO/5qYJ/eNObyhH2h80/OogWEr4FdaUx6mQG3ff.'
WHERE username = 'superadmin'
  AND password IN ('{noop}ChangeMe123!', 'ChangeMe123!');

-- 将默认超级管理员账号绑定 SUPER_ADMIN 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id, source, status)
SELECT u.user_id, 1, 'SYSTEM', 1
FROM `user` u
WHERE u.username = 'superadmin';

-- 为兼容历史基于 hasRole('ADMIN') 的接口，默认同时授予 ADMIN 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id, source, status)
SELECT u.user_id, 2, 'SYSTEM', 1
FROM `user` u
WHERE u.username = 'superadmin';


-- 创建服务应用详情视图
CREATE OR REPLACE VIEW v_service_app_details AS
SELECT
    sa.id,
    sa.app_name,
    sa.app_id,
    sa.status,
    sa.create_time,
    sa.update_time,
    sa.remark,
    COUNT(ae.id) as api_count,
    st.token_type,
    st.issue_time as token_issue_time,
    st.last_used_time as token_last_used
FROM service_apps sa
         LEFT JOIN api_endpoints ae ON JSON_CONTAINS(sa.allowed_api_list, CONCAT('"', ae.path, '"'))
         LEFT JOIN service_tokens st ON sa.app_id = st.app_id AND st.is_valid = 1
GROUP BY sa.id, st.id;

-- 创建API接口统计视图
CREATE OR REPLACE VIEW v_api_endpoint_stats AS
SELECT
    module_group,
    COUNT(*) as total_count,
    SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as enabled_count,
    SUM(CASE WHEN require_auth = 1 THEN 1 ELSE 0 END) as auth_required_count,
    MIN(create_time) as first_created,
    MAX(update_time) as last_updated
FROM api_endpoints
GROUP BY module_group;

-- =================================================================
-- 6. 索引优化建议
-- =================================================================

-- 为高频查询字段添加复合索引
ALTER TABLE service_apps ADD INDEX idx_status_create_time (status, create_time);
ALTER TABLE service_tokens ADD INDEX idx_app_id_valid (app_id, is_valid);
ALTER TABLE api_endpoints ADD INDEX idx_module_status (module_group, status);
ALTER TABLE sys_user_role ADD INDEX idx_sys_user_role_user_role_status (user_id, role_id, status);
ALTER TABLE sys_role_permission ADD INDEX idx_sys_role_permission_role_perm (role_id, permission_id);
ALTER TABLE sys_permission_endpoint_rel ADD INDEX idx_sys_permission_endpoint_rel_ep_perm_status (endpoint_id, permission_id, status);

-- =================================================================
-- 数据库初始化完成
-- =================================================================

-- 显示创建结果
SELECT '=== 数据库初始化完成 ===' as message;
SELECT COUNT(*) as service_apps_count FROM service_apps;
SELECT COUNT(*) as service_tokens_count FROM service_tokens;
SELECT COUNT(*) as api_endpoints_count FROM api_endpoints;
SELECT '=== 建议执行权限缓存初始化 ===' as next_step;
