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

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
-- auto-generated definition
create table user
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

-- =================================================================
-- 数据库初始化完成
-- =================================================================

-- 显示创建结果
SELECT '=== 数据库初始化完成 ===' as message;
SELECT COUNT(*) as service_apps_count FROM service_apps;
SELECT COUNT(*) as service_tokens_count FROM service_tokens;
SELECT COUNT(*) as api_endpoints_count FROM api_endpoints;
SELECT '=== 建议执行权限缓存初始化 ===' as next_step;
