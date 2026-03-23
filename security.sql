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
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    constraint user_username_id_uindex
        unique (username, id)
)
    charset = utf8mb4;

create index user_user_id_index
    on user (user_id);

SET @ddl_add_user_create_time = (
SELECT IF(
    EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user'
          AND COLUMN_NAME = 'create_time'
    ),
    'SELECT 1',
    'ALTER TABLE `user` ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''注册时间'' AFTER user_id'
)
);
PREPARE stmt_add_user_create_time FROM @ddl_add_user_create_time;
EXECUTE stmt_add_user_create_time;
DEALLOCATE PREPARE stmt_add_user_create_time;

SET @ddl_add_user_update_time = (
SELECT IF(
    EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user'
          AND COLUMN_NAME = 'update_time'
    ),
    'SELECT 1',
    'ALTER TABLE `user` ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''修改时间'' AFTER create_time'
)
);
PREPARE stmt_add_user_update_time FROM @ddl_add_user_update_time;
EXECUTE stmt_add_user_update_time;
DEALLOCATE PREPARE stmt_add_user_update_time;




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
    country VARCHAR(64) NULL COMMENT '国家',
    region_name VARCHAR(64) NULL COMMENT '地区/省份',
    city VARCHAR(64) NULL COMMENT '城市',
    isp VARCHAR(128) NULL COMMENT '运营商',
    location_label VARCHAR(255) NULL COMMENT '归属地标签',
    attack_type VARCHAR(64) NOT NULL COMMENT '攻击类型',
    path VARCHAR(500) NOT NULL COMMENT '请求路径',
    method VARCHAR(16) NOT NULL COMMENT '请求方法',
    endpoint_id BIGINT NULL COMMENT '命中的api_endpoints主键',
    username VARCHAR(128) NULL COMMENT '登录用户名',
    app_id VARCHAR(128) NULL COMMENT '服务调用appId',
    client_tool VARCHAR(64) NULL COMMENT '可疑客户端工具',
    browser_fingerprint VARCHAR(128) NULL COMMENT '浏览器指纹',
    browser_trusted TINYINT DEFAULT 0 COMMENT '浏览器指纹可信(0否,1是)',
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

ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS country VARCHAR(64) NULL COMMENT '国家' AFTER ip;
ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS region_name VARCHAR(64) NULL COMMENT '地区/省份' AFTER country;
ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS city VARCHAR(64) NULL COMMENT '城市' AFTER region_name;
ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS isp VARCHAR(128) NULL COMMENT '运营商' AFTER city;
ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS location_label VARCHAR(255) NULL COMMENT '归属地标签' AFTER isp;
ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS client_tool VARCHAR(64) NULL COMMENT '可疑客户端工具' AFTER app_id;
ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS browser_fingerprint VARCHAR(128) NULL COMMENT '浏览器指纹' AFTER client_tool;
ALTER TABLE security_attack_event ADD COLUMN IF NOT EXISTS browser_trusted TINYINT DEFAULT 0 COMMENT '浏览器指纹可信(0否,1是)' AFTER browser_fingerprint;

CREATE TABLE IF NOT EXISTS security_threat_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    enabled TINYINT DEFAULT 1 COMMENT '总开关',
    monitor_unknown_endpoints TINYINT DEFAULT 1 COMMENT '是否监控未知接口',
    trust_forward_headers TINYINT DEFAULT 1 COMMENT '是否信任转发头',
    capture_body_sample TINYINT DEFAULT 1 COMMENT '是否抓取请求体样本',
    max_inspect_body_bytes INT DEFAULT 4096 COMMENT '最大请求体检测字节数',
    global_window_ms BIGINT DEFAULT 10000 COMMENT '全局窗口毫秒数',
    global_window_limit INT DEFAULT 300 COMMENT '全局窗口限制',
    endpoint_window_ms BIGINT DEFAULT 10000 COMMENT '接口窗口毫秒数',
    endpoint_window_limit INT DEFAULT 120 COMMENT '接口窗口限制',
    auto_block_seconds INT DEFAULT 600 COMMENT '自动拉黑秒数',
    auto_block_multiplier INT DEFAULT 3 COMMENT '自动拉黑倍数',
    auth_feedback_window_ms BIGINT DEFAULT 60000 COMMENT '鉴权回流窗口毫秒数',
    auth401_feedback_threshold INT DEFAULT 8 COMMENT '401阈值',
    auth403_feedback_threshold INT DEFAULT 5 COMMENT '403阈值',
    auth403_auto_block_threshold INT DEFAULT 12 COMMENT '403自动拉黑阈值',
    event_queue_capacity INT DEFAULT 4096 COMMENT '异常事件队列容量',
    blacklist_queue_capacity INT DEFAULT 1024 COMMENT '黑名单队列容量',
    device_risk_enabled TINYINT DEFAULT 1 COMMENT '是否启用设备信誉引擎',
    device_risk_captcha_score_threshold INT DEFAULT 45 COMMENT '触发图片验证码的风险阈值',
    device_risk_block_score_threshold INT DEFAULT 90 COMMENT '拒绝登录的设备风险阈值',
    device_risk_new_device_score INT DEFAULT 8 COMMENT '新设备附加分',
    device_risk_ip_drift_score INT DEFAULT 24 COMMENT '同设备切换IP附加分',
    device_risk_ua_drift_score INT DEFAULT 24 COMMENT '同设备切换UA附加分',
    device_risk_multi_account_score INT DEFAULT 32 COMMENT '多账号切换附加分',
    device_risk_failure_penalty INT DEFAULT 8 COMMENT '单次失败附加分',
    device_risk_account_switch_window_ms BIGINT DEFAULT 900000 COMMENT '设备切换账号窗口毫秒数',
    device_risk_account_switch_threshold INT DEFAULT 2 COMMENT '设备切换账号阈值',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='威胁检测运行时配置表';

ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_enabled TINYINT DEFAULT 1 COMMENT '是否启用设备信誉引擎' AFTER blacklist_queue_capacity;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_captcha_score_threshold INT DEFAULT 45 COMMENT '触发图片验证码的风险阈值' AFTER device_risk_enabled;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_block_score_threshold INT DEFAULT 90 COMMENT '拒绝登录的设备风险阈值' AFTER device_risk_captcha_score_threshold;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_new_device_score INT DEFAULT 8 COMMENT '新设备附加分' AFTER device_risk_block_score_threshold;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_ip_drift_score INT DEFAULT 24 COMMENT '同设备切换IP附加分' AFTER device_risk_new_device_score;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_ua_drift_score INT DEFAULT 24 COMMENT '同设备切换UA附加分' AFTER device_risk_ip_drift_score;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_multi_account_score INT DEFAULT 32 COMMENT '多账号切换附加分' AFTER device_risk_ua_drift_score;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_failure_penalty INT DEFAULT 8 COMMENT '单次失败附加分' AFTER device_risk_multi_account_score;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_account_switch_window_ms BIGINT DEFAULT 900000 COMMENT '设备切换账号窗口毫秒数' AFTER device_risk_failure_penalty;
ALTER TABLE security_threat_config ADD COLUMN IF NOT EXISTS device_risk_account_switch_threshold INT DEFAULT 2 COMMENT '设备切换账号阈值' AFTER device_risk_account_switch_window_ms;

CREATE TABLE IF NOT EXISTS security_audit_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用审计',
    retention_days INT DEFAULT 90 COMMENT '保留天数',
    max_rows_per_table BIGINT DEFAULT 20000000 COMMENT '单表最大行数',
    queue_capacity INT DEFAULT 4096 COMMENT '异步队列容量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志配置表';

CREATE TABLE IF NOT EXISTS security_audit_table_meta (
    category VARCHAR(32) NOT NULL COMMENT '审计分类',
    current_suffix INT DEFAULT 1 COMMENT '当前分表后缀',
    current_table_name VARCHAR(64) NOT NULL COMMENT '当前写入表名',
    next_start_id BIGINT DEFAULT 1 COMMENT '下一张表起始ID',
    row_count BIGINT DEFAULT 0 COMMENT '当前表行数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志分表元数据';

CREATE TABLE IF NOT EXISTS security_audit_global_0001 (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    category VARCHAR(32) NOT NULL COMMENT '审计分类',
    event_type VARCHAR(64) NULL COMMENT '事件类型',
    module_name VARCHAR(128) NULL COMMENT '模块名称',
    operation_name VARCHAR(128) NULL COMMENT '操作名称',
    resource_type VARCHAR(64) NULL COMMENT '资源类型',
    resource_id VARCHAR(128) NULL COMMENT '资源ID',
    success TINYINT DEFAULT 1 COMMENT '是否成功',
    detail_text VARCHAR(2000) NULL COMMENT '详情',
    request_method VARCHAR(16) NULL COMMENT '请求方法',
    request_uri VARCHAR(500) NULL COMMENT '请求路径',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP',
    username VARCHAR(128) NULL COMMENT '用户名',
    user_id BIGINT NULL COMMENT '用户ID',
    login_type VARCHAR(64) NULL COMMENT '登录类型',
    response_code INT NULL COMMENT '响应状态码',
    trace_id VARCHAR(128) NULL COMMENT '链路ID',
    ext_json TEXT NULL COMMENT '扩展数据',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_security_audit_global_time (create_time),
    INDEX idx_security_audit_global_user (user_id, create_time),
    INDEX idx_security_audit_global_uri (request_uri(255), create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局审计日志分表';

CREATE TABLE IF NOT EXISTS security_audit_security_0001 (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    category VARCHAR(32) NOT NULL COMMENT '审计分类',
    event_type VARCHAR(64) NULL COMMENT '事件类型',
    module_name VARCHAR(128) NULL COMMENT '模块名称',
    operation_name VARCHAR(128) NULL COMMENT '操作名称',
    resource_type VARCHAR(64) NULL COMMENT '资源类型',
    resource_id VARCHAR(128) NULL COMMENT '资源ID',
    success TINYINT DEFAULT 1 COMMENT '是否成功',
    detail_text VARCHAR(2000) NULL COMMENT '详情',
    request_method VARCHAR(16) NULL COMMENT '请求方法',
    request_uri VARCHAR(500) NULL COMMENT '请求路径',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP',
    username VARCHAR(128) NULL COMMENT '用户名',
    user_id BIGINT NULL COMMENT '用户ID',
    login_type VARCHAR(64) NULL COMMENT '登录类型',
    response_code INT NULL COMMENT '响应状态码',
    trace_id VARCHAR(128) NULL COMMENT '链路ID',
    ext_json TEXT NULL COMMENT '扩展数据',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_security_audit_security_time (create_time),
    INDEX idx_security_audit_security_user (user_id, create_time),
    INDEX idx_security_audit_security_uri (request_uri(255), create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全审计日志分表';

CREATE TABLE IF NOT EXISTS security_audit_business_0001 (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    category VARCHAR(32) NOT NULL COMMENT '审计分类',
    event_type VARCHAR(64) NULL COMMENT '事件类型',
    module_name VARCHAR(128) NULL COMMENT '模块名称',
    operation_name VARCHAR(128) NULL COMMENT '操作名称',
    resource_type VARCHAR(64) NULL COMMENT '资源类型',
    resource_id VARCHAR(128) NULL COMMENT '资源ID',
    success TINYINT DEFAULT 1 COMMENT '是否成功',
    detail_text VARCHAR(2000) NULL COMMENT '详情',
    request_method VARCHAR(16) NULL COMMENT '请求方法',
    request_uri VARCHAR(500) NULL COMMENT '请求路径',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP',
    username VARCHAR(128) NULL COMMENT '用户名',
    user_id BIGINT NULL COMMENT '用户ID',
    login_type VARCHAR(64) NULL COMMENT '登录类型',
    response_code INT NULL COMMENT '响应状态码',
    trace_id VARCHAR(128) NULL COMMENT '链路ID',
    ext_json TEXT NULL COMMENT '扩展数据',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_security_audit_business_time (create_time),
    INDEX idx_security_audit_business_user (user_id, create_time),
    INDEX idx_security_audit_business_uri (request_uri(255), create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务审计日志分表';

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

CREATE TABLE IF NOT EXISTS sys_frontend_route (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    parent_id BIGINT NULL COMMENT '父级资源ID',
    route_name VARCHAR(128) NOT NULL COMMENT '前端路由名称',
    route_path VARCHAR(255) NULL COMMENT '前端路由路径(按钮资源可为空)',
    component VARCHAR(255) NULL COMMENT '前端组件标识，如 LAYOUT / views/system/users/index',
    redirect_path VARCHAR(255) NULL COMMENT '重定向路径',
    title VARCHAR(128) NOT NULL COMMENT '菜单/按钮标题',
    icon VARCHAR(128) NULL COMMENT '前端图标标识',
    resource_type VARCHAR(16) NOT NULL COMMENT '资源类型：DIRECTORY/MENU/BUTTON',
    permission_code VARCHAR(128) NULL COMMENT '关联权限编码(sys_permission.perm_code)',
    status TINYINT DEFAULT 1 COMMENT '状态(0-禁用,1-启用)',
    visible TINYINT DEFAULT 1 COMMENT '是否显示在菜单中(0-隐藏,1-显示)',
    sort INT DEFAULT 0 COMMENT '排序值',
    keep_alive TINYINT DEFAULT 0 COMMENT '是否缓存页面',
    always_show TINYINT DEFAULT 0 COMMENT '是否总是展示父级菜单',
    ignore_auth TINYINT DEFAULT 0 COMMENT '是否忽略权限校验',
    active_menu VARCHAR(128) NULL COMMENT '高亮菜单名称',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_frontend_route_name (route_name),
    INDEX idx_sys_frontend_route_parent (parent_id, status, sort),
    INDEX idx_sys_frontend_route_permission (permission_code, status),
    INDEX idx_sys_frontend_route_type (resource_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='前端路由/菜单/按钮资源表';

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
    (1251, 'iam:frontend-route:read', '查看前端资源', 'IAM-前端资源', 1, '查看前端菜单与按钮资源'),
    (1252, 'iam:frontend-route:create', '创建前端资源', 'IAM-前端资源', 1, '新增前端菜单与按钮资源'),
    (1253, 'iam:frontend-route:update', '修改前端资源', 'IAM-前端资源', 1, '修改前端菜单与按钮资源'),
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
    (1402, 'threat:admin:manage', '管理安全异常识别配置', '安全异常识别', 1, '黑白名单/监控开关管理'),
    (1451, 'security:settings:read', '查看安全设置', '安全设置', 1, '查看安全配置与封禁信息'),
    (1452, 'audit:log:read', '查看审计日志', '审计日志', 1, '查看全局/安全/业务审计日志'),
    (1453, 'audit:log:manage', '管理审计配置', '审计日志', 1, '修改审计日志保留与分表配置')
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
    (1, 1251), (1, 1252), (1, 1253),
    (1, 1301), (1, 1302), (1, 1303), (1, 1304), (1, 1305),
    (1, 1501), (1, 1502),
    (1, 1601), (1, 1602), (1, 1603), (1, 1604), (1, 1605), (1, 1606), (1, 1607),
    (1, 1701), (1, 1702),
    (1, 1801), (1, 1802),
    (1, 1901), (1, 1902),
    (1, 1401), (1, 1402), (1, 1451), (1, 1452), (1, 1453),
    -- ADMIN 拥有常用管理权限（不包含角色授予规则修改）
    (2, 1001), (2, 1002), (2, 1003), (2, 1004), (2, 1005), (2, 1006),
    (2, 1103), (2, 1203), (2, 1251), (2, 1304), (2, 1305),
    (2, 1501), (2, 1502),
    (2, 1601), (2, 1602), (2, 1603), (2, 1604), (2, 1605), (2, 1606), (2, 1607),
    (2, 1701), (2, 1702),
    (2, 1801), (2, 1802),
    (2, 1901),
    (2, 1401), (2, 1402), (2, 1451), (2, 1452),
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
-- 初始密码：123456（bcrypt加密，登录后请尽快修改为强密码）
INSERT INTO `user` (username, password, status, user_id)
SELECT 'superadmin', '{bcrypt}$2b$10$qBhD/fV2g0d.qPASv5U9EO1yARXAcBi9dE2B55PCthQOaG/9muyD.', 0, 1000001
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'superadmin');

-- 兼容旧库：将历史遗留的 superadmin 明文/{noop} 初始化密码升级为 Spring Security bcrypt（初始密码：123456）
UPDATE `user`
SET password = '{bcrypt}$2b$10$qBhD/fV2g0d.qPASv5U9EO1yARXAcBi9dE2B55PCthQOaG/9muyD.'
WHERE username = 'superadmin'
  AND password IN ('{noop}ChangeMe123!', 'ChangeMe123!',
                   '{bcrypt}$2a$10$8vqzICJXGHzpOMO/5qYJ/eNObyhH2h80/OogWEr4FdaUx6mQG3ff.');

-- 将默认超级管理员账号绑定 SUPER_ADMIN 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id, source, status)
SELECT u.user_id, 1, 'SYSTEM', 1
FROM `user` u
WHERE u.username = 'superadmin';

INSERT INTO sys_frontend_route
    (id, parent_id, route_name, route_path, component, redirect_path, title, icon, resource_type, permission_code, status, visible, sort, keep_alive, always_show, ignore_auth, active_menu, remark)
VALUES
    (1, NULL, 'dashboard', '/dashboard', 'LAYOUT', '/dashboard/home', '首页', 'DashboardOutlined', 'DIRECTORY', NULL, 1, 1, 0, 0, 0, 0, NULL, '后台首页'),
    (2, 1, 'dashboard_home', 'home', 'views/home/index', NULL, 'Home', 'DashboardOutlined', 'MENU', NULL, 1, 1, 0, 1, 0, 0, NULL, '首页工作台'),
    (10, NULL, 'system', '/system', 'LAYOUT', '/system/users', '权限中心', 'SafetyCertificateOutlined', 'DIRECTORY', NULL, 1, 1, 10, 0, 0, 0, NULL, '权限中心目录'),
    (11, 10, 'system_users', 'users', 'views/system/users/index', NULL, '用户管理', 'TeamOutlined', 'MENU', 'iam:user:read', 1, 1, 11, 1, 0, 0, NULL, '用户管理页面'),
    (12, 10, 'system_roles', 'roles', 'views/system/roles/index', NULL, '角色管理', 'SafetyCertificateOutlined', 'MENU', 'iam:role:read', 1, 1, 12, 1, 0, 0, NULL, '角色管理页面'),
    (13, 10, 'system_permissions', 'permissions', 'views/system/permissions/index', NULL, '权限管理', 'KeyOutlined', 'MENU', 'iam:permission:read', 1, 1, 13, 1, 0, 0, NULL, '权限管理页面'),
    (14, 10, 'system_frontend_routes', 'frontend-routes', 'views/system/frontend-routes/index', NULL, '前端资源', 'AppstoreOutlined', 'MENU', 'iam:frontend-route:read', 1, 1, 14, 1, 0, 0, NULL, '前端资源管理页面'),
    (15, NULL, 'system_security_center', '/security-center', 'LAYOUT', '/security-center/security-logs', '安全中心', 'SafetyCertificateOutlined', 'DIRECTORY', NULL, 1, 1, 20, 0, 0, 0, NULL, '安全中心目录'),
    (151, 15, 'system_security_settings', 'settings', 'views/system/security-center/index', NULL, '安全设置', 'SafetyCertificateOutlined', 'MENU', 'security:settings:read', 1, 1, 151, 1, 0, 0, 'system_security_center', '安全中心-安全设置页面'),
    (16, NULL, 'system_audit_center', '/audit-center', 'LAYOUT', '/audit-center/business-logs', '审计中心', 'KeyOutlined', 'DIRECTORY', NULL, 1, 1, 30, 0, 0, 0, NULL, '审计中心目录'),
    (161, 16, 'system_audit_business_logs', 'business-logs', 'views/system/audit-center/business-logs/index', NULL, '业务日志', 'KeyOutlined', 'MENU', 'audit:log:read', 1, 1, 161, 1, 0, 0, 'system_audit_center', '审计中心-业务日志页面'),
    (162, 15, 'system_security_logs', 'security-logs', 'views/system/security-center/security-logs/index', NULL, '安全日志', 'SafetyCertificateOutlined', 'MENU', 'threat:admin:read', 1, 1, 162, 1, 0, 0, 'system_security_center', '安全中心-安全日志页面'),
    (166, 15, 'system_security_banned_users', 'banned-users', 'views/system/security-center/banned-users/index', NULL, '封禁用户', 'StopOutlined', 'MENU', 'security:settings:read', 1, 1, 166, 1, 0, 0, 'system_security_center', '安全中心-封禁用户页面'),
    (167, 15, 'system_security_blacklist', 'blacklist', 'views/system/security-center/blacklist/index', NULL, '黑名单', 'StopOutlined', 'MENU', 'threat:admin:read', 1, 1, 167, 1, 0, 0, 'system_security_center', '安全中心-黑名单页面'),
    (168, 15, 'system_security_whitelist', 'whitelist', 'views/system/security-center/whitelist/index', NULL, '白名单', 'CheckCircleOutlined', 'MENU', 'threat:admin:read', 1, 1, 168, 1, 0, 0, 'system_security_center', '安全中心-白名单页面'),
    (101, 11, 'system_users_create_button', NULL, NULL, NULL, '新增用户', NULL, 'BUTTON', 'iam:user:create', 1, 0, 101, 0, 0, 0, 'system_users', '用户管理-新增按钮'),
    (102, 11, 'system_users_edit_button', NULL, NULL, NULL, '编辑用户', NULL, 'BUTTON', 'iam:user:update', 1, 0, 102, 0, 0, 0, 'system_users', '用户管理-编辑按钮'),
    (103, 11, 'system_users_role_assign_button', NULL, NULL, NULL, '分配角色', NULL, 'BUTTON', 'iam:user:role:assign', 1, 0, 103, 0, 0, 0, 'system_users', '用户管理-角色分配按钮'),
    (104, 11, 'system_users_status_button', NULL, NULL, NULL, '禁用/启用用户', NULL, 'BUTTON', 'iam:user:status:update', 1, 0, 104, 0, 0, 0, 'system_users', '用户管理-状态修改按钮'),
    (121, 12, 'system_roles_create_button', NULL, NULL, NULL, '新增角色', NULL, 'BUTTON', 'iam:role:create', 1, 0, 121, 0, 0, 0, 'system_roles', '角色管理-新增按钮'),
    (122, 12, 'system_roles_edit_button', NULL, NULL, NULL, '编辑角色', NULL, 'BUTTON', 'iam:role:update', 1, 0, 122, 0, 0, 0, 'system_roles', '角色管理-编辑按钮'),
    (123, 12, 'system_roles_bind_permission_button', NULL, NULL, NULL, '角色绑定权限', NULL, 'BUTTON', 'iam:role:permission:bind', 1, 0, 123, 0, 0, 0, 'system_roles', '角色管理-绑定权限按钮'),
    (131, 13, 'system_permissions_create_button', NULL, NULL, NULL, '新增权限', NULL, 'BUTTON', 'iam:permission:create', 1, 0, 131, 0, 0, 0, 'system_permissions', '权限管理-新增按钮'),
    (132, 13, 'system_permissions_edit_button', NULL, NULL, NULL, '编辑权限', NULL, 'BUTTON', 'iam:permission:update', 1, 0, 132, 0, 0, 0, 'system_permissions', '权限管理-编辑按钮'),
    (141, 14, 'system_frontend_routes_create_button', NULL, NULL, NULL, '新增前端资源', NULL, 'BUTTON', 'iam:frontend-route:create', 1, 0, 141, 0, 0, 0, 'system_frontend_routes', '前端资源管理-新增按钮'),
    (142, 14, 'system_frontend_routes_edit_button', NULL, NULL, NULL, '编辑前端资源', NULL, 'BUTTON', 'iam:frontend-route:update', 1, 0, 142, 0, 0, 0, 'system_frontend_routes', '前端资源管理-编辑按钮'),
    (152, 151, 'system_security_center_threat_manage_button', NULL, NULL, NULL, '管理黑白名单', NULL, 'BUTTON', 'threat:admin:manage', 1, 0, 152, 0, 0, 0, 'system_security_settings', '安全设置-黑白名单管理按钮'),
    (163, 161, 'system_audit_center_config_button', NULL, NULL, NULL, '审计配置', NULL, 'BUTTON', 'audit:log:manage', 1, 0, 163, 0, 0, 0, 'system_audit_center', '审计中心-配置按钮')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    route_name = VALUES(route_name),
    route_path = VALUES(route_path),
    component = VALUES(component),
    redirect_path = VALUES(redirect_path),
    title = VALUES(title),
    icon = VALUES(icon),
    resource_type = VALUES(resource_type),
    permission_code = VALUES(permission_code),
    status = VALUES(status),
    visible = VALUES(visible),
    sort = VALUES(sort),
    keep_alive = VALUES(keep_alive),
    always_show = VALUES(always_show),
    ignore_auth = VALUES(ignore_auth),
    active_menu = VALUES(active_menu),
    remark = VALUES(remark);

UPDATE sys_frontend_route
SET parent_id = NULL,
    route_path = '/security-center',
    component = 'LAYOUT',
    redirect_path = '/security-center/security-logs',
    active_menu = NULL
WHERE route_name = 'system_security_center';

UPDATE sys_frontend_route
SET parent_id = 15,
    active_menu = 'system_security_center'
WHERE route_name IN (
    'system_security_settings',
    'system_security_logs',
    'system_security_banned_users',
    'system_security_blacklist',
    'system_security_whitelist'
);

UPDATE sys_frontend_route
SET parent_id = NULL,
    route_path = '/audit-center',
    component = 'LAYOUT',
    redirect_path = '/audit-center/business-logs',
    active_menu = NULL
WHERE route_name = 'system_audit_center';

UPDATE sys_frontend_route
SET parent_id = 16,
    active_menu = 'system_audit_center'
WHERE route_name = 'system_audit_business_logs';

INSERT INTO security_audit_config (id, enabled, retention_days, max_rows_per_table, queue_capacity)
VALUES (1, 1, 90, 20000000, 4096)
ON DUPLICATE KEY UPDATE
    enabled = VALUES(enabled),
    retention_days = VALUES(retention_days),
    max_rows_per_table = VALUES(max_rows_per_table),
    queue_capacity = VALUES(queue_capacity);

INSERT INTO security_threat_config (
    id, enabled, monitor_unknown_endpoints, trust_forward_headers, capture_body_sample, max_inspect_body_bytes,
    global_window_ms, global_window_limit, endpoint_window_ms, endpoint_window_limit, auto_block_seconds, auto_block_multiplier,
    auth_feedback_window_ms, auth401_feedback_threshold, auth403_feedback_threshold, auth403_auto_block_threshold,
    event_queue_capacity, blacklist_queue_capacity, device_risk_enabled, device_risk_captcha_score_threshold,
    device_risk_block_score_threshold, device_risk_new_device_score, device_risk_ip_drift_score,
    device_risk_ua_drift_score, device_risk_multi_account_score, device_risk_failure_penalty,
    device_risk_account_switch_window_ms, device_risk_account_switch_threshold
)
VALUES
    (1, 1, 1, 1, 1, 4096, 10000, 300, 10000, 120, 600, 3, 60000, 8, 5, 12, 4096, 1024, 1, 45, 90, 8, 24, 24, 32, 8, 900000, 2)
ON DUPLICATE KEY UPDATE
    enabled = VALUES(enabled),
    monitor_unknown_endpoints = VALUES(monitor_unknown_endpoints),
    trust_forward_headers = VALUES(trust_forward_headers),
    capture_body_sample = VALUES(capture_body_sample),
    max_inspect_body_bytes = VALUES(max_inspect_body_bytes),
    global_window_ms = VALUES(global_window_ms),
    global_window_limit = VALUES(global_window_limit),
    endpoint_window_ms = VALUES(endpoint_window_ms),
    endpoint_window_limit = VALUES(endpoint_window_limit),
    auto_block_seconds = VALUES(auto_block_seconds),
    auto_block_multiplier = VALUES(auto_block_multiplier),
    auth_feedback_window_ms = VALUES(auth_feedback_window_ms),
    auth401_feedback_threshold = VALUES(auth401_feedback_threshold),
    auth403_feedback_threshold = VALUES(auth403_feedback_threshold),
    auth403_auto_block_threshold = VALUES(auth403_auto_block_threshold),
    event_queue_capacity = VALUES(event_queue_capacity),
    blacklist_queue_capacity = VALUES(blacklist_queue_capacity),
    device_risk_enabled = VALUES(device_risk_enabled),
    device_risk_captcha_score_threshold = VALUES(device_risk_captcha_score_threshold),
    device_risk_block_score_threshold = VALUES(device_risk_block_score_threshold),
    device_risk_new_device_score = VALUES(device_risk_new_device_score),
    device_risk_ip_drift_score = VALUES(device_risk_ip_drift_score),
    device_risk_ua_drift_score = VALUES(device_risk_ua_drift_score),
    device_risk_multi_account_score = VALUES(device_risk_multi_account_score),
    device_risk_failure_penalty = VALUES(device_risk_failure_penalty),
    device_risk_account_switch_window_ms = VALUES(device_risk_account_switch_window_ms),
    device_risk_account_switch_threshold = VALUES(device_risk_account_switch_threshold);

INSERT INTO security_audit_table_meta (category, current_suffix, current_table_name, next_start_id, row_count)
VALUES
    ('GLOBAL', 1, 'security_audit_global_0001', 1, 0),
    ('SECURITY', 1, 'security_audit_security_0001', 1, 0),
    ('BUSINESS', 1, 'security_audit_business_0001', 1, 0)
ON DUPLICATE KEY UPDATE
    current_suffix = VALUES(current_suffix),
    current_table_name = VALUES(current_table_name),
    update_time = CURRENT_TIMESTAMP;

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
ALTER TABLE sys_frontend_route ADD INDEX idx_sys_frontend_route_parent_visible (parent_id, visible, sort);

-- =================================================================
-- 数据库初始化完成
-- =================================================================

-- 显示创建结果
SELECT '=== 数据库初始化完成 ===' as message;
SELECT COUNT(*) as service_apps_count FROM service_apps;
SELECT COUNT(*) as service_tokens_count FROM service_tokens;
SELECT COUNT(*) as api_endpoints_count FROM api_endpoints;
SELECT '=== 建议执行权限缓存初始化 ===' as next_step;
