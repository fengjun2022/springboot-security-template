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
            "sys_role",
            "sys_permission",
            "sys_permission_endpoint_rel",
            "sys_user_role",
            "sys_role_permission",
            "sys_role_grant_rule"
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
                ensureApiEndpointsAuthColumn();
                ensureApiEndpointsThreatMonitorColumn();
                log.info("数据库初始化脚本执行完成");
                return;
            }

            ensureApiEndpointsAuthColumn();
            ensureApiEndpointsThreatMonitorColumn();
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
