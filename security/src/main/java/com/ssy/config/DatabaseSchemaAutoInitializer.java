package com.ssy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 启动时自动检查并初始化安全模块相关表结构。
 * 优先处理缺表场景，其次兼容旧版本 api_endpoints 缺少 auth 列的问题。
 */
@Component
public class DatabaseSchemaAutoInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaAutoInitializer.class);

    private static final List<String> REQUIRED_TABLES = Arrays.asList(
            "user",
            "service_apps",
            "service_tokens",
            "api_endpoints",
            "security_ip_blacklist",
            "security_ip_whitelist",
            "security_attack_event",
            "security_threat_config",
            "security_audit_config",
            "security_audit_table_meta",
            "security_audit_global_0001",
            "security_audit_security_0001",
            "security_audit_business_0001",
            "sys_role",
            "sys_permission",
            "sys_permission_endpoint_rel",
            "sys_user_role",
            "sys_role_permission",
            "sys_role_grant_rule",
            "sys_frontend_route"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DatabaseSchemaAutoInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            List<String> missingTables = REQUIRED_TABLES.stream()
                    .filter(table -> !tableExists(table))
                    .collect(Collectors.toList());

            if (!missingTables.isEmpty()) {
                log.warn("检测到缺失表，准备执行 security.sql 初始化。缺失表: {}", missingTables);
                executeSecuritySql();
                validateRequiredTables();
                ensureUserTimeColumns();
                ensureApiEndpointsAuthColumn();
                ensureApiEndpointsThreatMonitorColumn();
                ensureSecurityAttackEventColumns();
                ensureSecurityThreatConfigColumns();
                ensureAuditTableApiDescriptionColumn();
                log.info("数据库初始化脚本执行完成");
                return;
            }

            ensureUserTimeColumns();
            ensureApiEndpointsAuthColumn();
            ensureApiEndpointsThreatMonitorColumn();
            ensureSecurityAttackEventColumns();
            ensureSecurityThreatConfigColumns();
            ensureAuditTableApiDescriptionColumn();
            ensureFrontendRouteSeeds();
        } catch (Exception e) {
            log.error("数据库表结构自动初始化失败", e);
            throw new IllegalStateException("数据库表结构自动初始化失败", e);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private void ensureApiEndpointsAuthColumn() {
        if (!tableExists("api_endpoints")) {
            return;
        }
        if (columnExists("api_endpoints", "auth")) {
            return;
        }
        log.warn("检测到 api_endpoints 表缺少 auth 列，自动补齐");
        jdbcTemplate.execute("ALTER TABLE api_endpoints " +
                "ADD COLUMN auth VARCHAR(1000) NULL COMMENT '权限表达式(如@PreAuthorize/@Secured等)' AFTER description");
        log.info("已补齐 api_endpoints.auth 列");
    }

    private void ensureUserTimeColumns() {
        if (!tableExists("user")) {
            return;
        }
        ensureColumn("user", "create_time",
                "ALTER TABLE `user` ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间' AFTER user_id");
        ensureColumn("user", "update_time",
                "ALTER TABLE `user` ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间' AFTER create_time");
    }

    private void ensureApiEndpointsThreatMonitorColumn() {
        if (!tableExists("api_endpoints")) {
            return;
        }
        if (columnExists("api_endpoints", "threat_monitor_enabled")) {
            return;
        }
        log.warn("检测到 api_endpoints 表缺少 threat_monitor_enabled 列，自动补齐");
        jdbcTemplate.execute("ALTER TABLE api_endpoints " +
                "ADD COLUMN threat_monitor_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用异常识别监控(1启用,0白名单放行)' AFTER auth");
        log.info("已补齐 api_endpoints.threat_monitor_enabled 列");
    }

    private void validateRequiredTables() {
        List<String> stillMissing = REQUIRED_TABLES.stream()
                .filter(table -> !tableExists(table))
                .collect(Collectors.toList());
        if (!stillMissing.isEmpty()) {
            throw new IllegalStateException("执行 security.sql 后仍缺少表: " + stillMissing);
        }
    }

    private void ensureSecurityAttackEventColumns() {
        if (!tableExists("security_attack_event")) {
            return;
        }
        ensureColumn("security_attack_event", "country",
                "ALTER TABLE security_attack_event ADD COLUMN country VARCHAR(64) NULL COMMENT '国家' AFTER ip");
        ensureColumn("security_attack_event", "region_name",
                "ALTER TABLE security_attack_event ADD COLUMN region_name VARCHAR(64) NULL COMMENT '地区/省份' AFTER country");
        ensureColumn("security_attack_event", "city",
                "ALTER TABLE security_attack_event ADD COLUMN city VARCHAR(64) NULL COMMENT '城市' AFTER region_name");
        ensureColumn("security_attack_event", "isp",
                "ALTER TABLE security_attack_event ADD COLUMN isp VARCHAR(128) NULL COMMENT '运营商' AFTER city");
        ensureColumn("security_attack_event", "location_label",
                "ALTER TABLE security_attack_event ADD COLUMN location_label VARCHAR(255) NULL COMMENT '归属地标签' AFTER isp");
        ensureColumn("security_attack_event", "client_tool",
                "ALTER TABLE security_attack_event ADD COLUMN client_tool VARCHAR(64) NULL COMMENT '可疑客户端工具' AFTER app_id");
        ensureColumn("security_attack_event", "browser_fingerprint",
                "ALTER TABLE security_attack_event ADD COLUMN browser_fingerprint VARCHAR(128) NULL COMMENT '浏览器指纹' AFTER client_tool");
        ensureColumn("security_attack_event", "browser_trusted",
                "ALTER TABLE security_attack_event ADD COLUMN browser_trusted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '浏览器指纹可信(0否,1是)' AFTER browser_fingerprint");
    }

    private void ensureSecurityThreatConfigColumns() {
        if (!tableExists("security_threat_config")) {
            return;
        }
        ensureColumn("security_threat_config", "device_risk_enabled",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用设备信誉引擎' AFTER blacklist_queue_capacity");
        ensureColumn("security_threat_config", "device_risk_captcha_score_threshold",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_captcha_score_threshold INT NOT NULL DEFAULT 45 COMMENT '触发图片验证码的风险阈值' AFTER device_risk_enabled");
        ensureColumn("security_threat_config", "device_risk_block_score_threshold",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_block_score_threshold INT NOT NULL DEFAULT 90 COMMENT '拒绝登录的设备风险阈值' AFTER device_risk_captcha_score_threshold");
        ensureColumn("security_threat_config", "device_risk_new_device_score",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_new_device_score INT NOT NULL DEFAULT 8 COMMENT '新设备附加分' AFTER device_risk_block_score_threshold");
        ensureColumn("security_threat_config", "device_risk_ip_drift_score",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_ip_drift_score INT NOT NULL DEFAULT 24 COMMENT '同设备切换IP附加分' AFTER device_risk_new_device_score");
        ensureColumn("security_threat_config", "device_risk_ua_drift_score",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_ua_drift_score INT NOT NULL DEFAULT 24 COMMENT '同设备切换UA附加分' AFTER device_risk_ip_drift_score");
        ensureColumn("security_threat_config", "device_risk_multi_account_score",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_multi_account_score INT NOT NULL DEFAULT 32 COMMENT '多账号切换附加分' AFTER device_risk_ua_drift_score");
        ensureColumn("security_threat_config", "device_risk_failure_penalty",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_failure_penalty INT NOT NULL DEFAULT 8 COMMENT '单次失败附加分' AFTER device_risk_multi_account_score");
        ensureColumn("security_threat_config", "device_risk_account_switch_window_ms",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_account_switch_window_ms BIGINT NOT NULL DEFAULT 900000 COMMENT '设备切换账号窗口毫秒数' AFTER device_risk_failure_penalty");
        ensureColumn("security_threat_config", "device_risk_account_switch_threshold",
                "ALTER TABLE security_threat_config ADD COLUMN device_risk_account_switch_threshold INT NOT NULL DEFAULT 2 COMMENT '设备切换账号阈值' AFTER device_risk_account_switch_window_ms");
    }

    /**
     * 为所有已存在的审计日志分表补齐 api_description 列（动态匹配 api_endpoints 接口描述用）。
     */
    private void ensureAuditTableApiDescriptionColumn() {
        List<String> auditTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name LIKE 'security_audit_%' " +
                        "AND table_name NOT IN ('security_audit_config', 'security_audit_table_meta')",
                String.class
        );
        for (String table : auditTables) {
            if (!columnExists(table, "api_description")) {
                log.warn("检测到审计日志表 {} 缺少 api_description 列，自动补齐", table);
                jdbcTemplate.execute("ALTER TABLE " + table +
                        " ADD COLUMN api_description VARCHAR(500) NULL COMMENT '接口描述(来自api_endpoints表)' AFTER ext_json");
            }
        }
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        log.warn("检测到 {} 表缺少 {} 列，自动补齐", tableName, columnName);
        jdbcTemplate.execute(ddl);
    }

    private void ensureFrontendRouteSeeds() {
        if (!tableExists("sys_frontend_route") || !tableExists("sys_permission")) {
            return;
        }
        Integer routeSeedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_frontend_route WHERE route_name IN (?, ?, ?, ?, ?, ?, ?, ?)",
                Integer.class,
                "system_frontend_routes",
                "system_frontend_routes_create_button",
                "system_frontend_routes_edit_button",
                "system_security_center",
                "system_security_settings",
                "system_security_logs",
                "system_audit_center",
                "system_audit_business_logs"
        );
        Integer permissionSeedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE perm_code IN (?, ?, ?, ?, ?, ?)",
                Integer.class,
                "iam:frontend-route:read",
                "iam:frontend-route:create",
                "iam:frontend-route:update",
                "security:settings:read",
                "audit:log:read",
                "audit:log:manage"
        );
        boolean routeSeedsMissing = routeSeedCount == null || routeSeedCount < 8;
        boolean permissionSeedsMissing = permissionSeedCount == null || permissionSeedCount < 6;
        if (!routeSeedsMissing && !permissionSeedsMissing) {
            return;
        }
        log.warn("检测到前端资源权限种子缺失，重新执行 security.sql 进行补齐");
        executeSecuritySql();
    }

    private void executeSecuritySql() {
        Resource resource = resolveSecuritySqlResource();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(resource);
        // 允许重复建表/索引等语句在部分初始化场景下继续执行，最终以后置校验兜底
        populator.setContinueOnError(true);
        populator.execute(dataSource);
    }

    private Resource resolveSecuritySqlResource() {
        Path projectRootSql = Paths.get(System.getProperty("user.dir"), "security.sql");
        if (Files.exists(projectRootSql)) {
            return new FileSystemResource(projectRootSql);
        }

        ClassPathResource classPathResource = new ClassPathResource("security.sql");
        if (classPathResource.exists()) {
            return classPathResource;
        }

        throw new IllegalStateException("未找到 security.sql，期望路径: " + projectRootSql);
    }
}
