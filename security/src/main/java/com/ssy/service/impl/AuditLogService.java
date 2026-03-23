package com.ssy.service.impl;

import com.ssy.entity.AuditLogConfigEntity;
import com.ssy.entity.AuditLogRecordEntity;
import com.ssy.properties.AuditLogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^security_audit_(global|security|business)_\\d{4}$");
    private static final List<String> CATEGORIES = Arrays.asList("GLOBAL", "SECURITY", "BUSINESS");
    private static final RowMapper<AuditLogRecordEntity> AUDIT_ROW_MAPPER = new AuditLogRowMapper();

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogProperties properties;

    public AuditLogService(JdbcTemplate jdbcTemplate, AuditLogProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public AuditLogConfigEntity getConfig() {
        if (!tableExists("security_audit_config")) {
            return buildFallbackConfig();
        }
        List<AuditLogConfigEntity> records = jdbcTemplate.query(
                "SELECT id, enabled, retention_days, max_rows_per_table, queue_capacity, create_time, update_time " +
                        "FROM security_audit_config WHERE id = 1",
                (rs, rowNum) -> {
                    AuditLogConfigEntity entity = new AuditLogConfigEntity();
                    entity.setId(rs.getLong("id"));
                    entity.setEnabled(rs.getInt("enabled"));
                    entity.setRetentionDays(rs.getInt("retention_days"));
                    entity.setMaxRowsPerTable(rs.getLong("max_rows_per_table"));
                    entity.setQueueCapacity(rs.getInt("queue_capacity"));
                    entity.setCreateTime(toLocalDateTime(rs, "create_time"));
                    entity.setUpdateTime(toLocalDateTime(rs, "update_time"));
                    return entity;
                }
        );
        if (!records.isEmpty()) {
            return records.get(0);
        }
        return buildFallbackConfig();
    }

    public AuditLogConfigEntity updateConfig(AuditLogConfigEntity payload) {
        AuditLogConfigEntity current = getConfig();
        int enabled = payload.getEnabled() == null ? current.getEnabled() : (payload.getEnabled() == 0 ? 0 : 1);
        int retentionDays = payload.getRetentionDays() == null
                ? current.getRetentionDays()
                : Math.max(payload.getRetentionDays(), 1);
        long maxRowsPerTable = payload.getMaxRowsPerTable() == null
                ? current.getMaxRowsPerTable()
                : Math.max(payload.getMaxRowsPerTable(), 10_000L);
        int queueCapacity = payload.getQueueCapacity() == null
                ? current.getQueueCapacity()
                : Math.max(payload.getQueueCapacity(), 256);

        jdbcTemplate.update(
                "INSERT INTO security_audit_config (id, enabled, retention_days, max_rows_per_table, queue_capacity, create_time, update_time) " +
                        "VALUES (1, ?, ?, ?, ?, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), retention_days = VALUES(retention_days), " +
                        "max_rows_per_table = VALUES(max_rows_per_table), queue_capacity = VALUES(queue_capacity), update_time = NOW()",
                enabled,
                retentionDays,
                maxRowsPerTable,
                queueCapacity
        );
        return getConfig();
    }

    public void record(AuditLogRecordEntity entity) {
        if (entity == null) {
            return;
        }
        AuditLogConfigEntity config = getConfig();
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            return;
        }

        synchronized (this) {
            String category = normalizeCategory(entity.getCategory());
            ensureCategoryMeta(category);
            MetaRow meta = loadMeta(category);
            if (meta == null) {
                throw new IllegalStateException("审计表元数据不存在: " + category);
            }

            String tableName = meta.getCurrentTableName();
            validateTableName(tableName);
            LocalDateTime now = entity.getCreateTime() == null ? LocalDateTime.now() : entity.getCreateTime();

            jdbcTemplate.update(
                    "INSERT INTO " + tableName + " (" +
                            "category, event_type, module_name, operation_name, resource_type, resource_id, success, detail_text, " +
                            "request_method, request_uri, client_ip, username, user_id, login_type, response_code, trace_id, ext_json, api_description, create_time" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    category,
                    trim(entity.getEventType(), 64),
                    trim(entity.getModuleName(), 128),
                    trim(entity.getOperationName(), 128),
                    trim(entity.getResourceType(), 64),
                    trim(entity.getResourceId(), 128),
                    entity.getSuccess() == null ? 1 : entity.getSuccess(),
                    trim(entity.getDetailText(), 2000),
                    trim(entity.getRequestMethod(), 16),
                    trim(entity.getRequestUri(), 500),
                    trim(entity.getClientIp(), 64),
                    trim(entity.getUsername(), 128),
                    entity.getUserId(),
                    trim(entity.getLoginType(), 64),
                    entity.getResponseCode(),
                    trim(entity.getTraceId(), 128),
                    entity.getExtJson(),
                    trim(entity.getApiDescription(), 500),
                    now
            );

            Long insertedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            long nextRowCount = meta.getRowCount() + 1;
            jdbcTemplate.update(
                    "UPDATE security_audit_table_meta SET row_count = ?, next_start_id = ?, update_time = NOW() WHERE category = ?",
                    nextRowCount,
                    insertedId == null ? meta.getNextStartId() : insertedId + 1,
                    category
            );

            if (nextRowCount >= resolveMaxRowsPerTable(config)) {
                rotateTable(category, meta, insertedId == null ? meta.getNextStartId() : insertedId + 1);
            }
        }
    }

    public Map<String, Object> getStats() {
        AuditLogConfigEntity config = getConfig();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", config);
        List<Map<String, Object>> categories = new ArrayList<>();
        for (String category : CATEGORIES) {
            ensureCategoryMeta(category);
            MetaRow meta = loadMeta(category);
            if (meta == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", category);
            item.put("currentTableName", meta.getCurrentTableName());
            item.put("currentSuffix", meta.getCurrentSuffix());
            item.put("rowCount", meta.getRowCount());
            item.put("tableCount", listCategoryTables(category).size());
            item.put("nextStartId", meta.getNextStartId());
            categories.add(item);
        }
        result.put("categories", categories);
        return result;
    }

    public AuditLogPage queryLogs(String category, int page, int size, String keyword) {
        String normalizedCategory = normalizeCategory(category);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = (safePage - 1) * safeSize;
        List<String> tables = listCategoryTables(normalizedCategory);
        if (tables.isEmpty()) {
            return new AuditLogPage(new ArrayList<>(), 0, safePage, safeSize);
        }

        String filterSql = "";
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            filterSql = " WHERE l.username LIKE ? OR l.request_uri LIKE ? OR l.detail_text LIKE ? OR l.operation_name LIKE ? ";
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        final String finalFilterSql = filterSql;

        long total = 0;
        for (String table : tables) {
            validateTableName(table);
            String countSql = "SELECT COUNT(*) FROM " + table + " l " + finalFilterSql;
            Long count = jdbcTemplate.queryForObject(countSql, args.toArray(), Long.class);
            total += count == null ? 0 : count;
        }

        String unionSql = tables.stream()
                .map(table -> {
                    validateTableName(table);
                    return "SELECT id, category, event_type, module_name, operation_name, resource_type, resource_id, success, detail_text, " +
                            "request_method, request_uri, client_ip, username, user_id, login_type, response_code, trace_id, ext_json, " +
                            "COALESCE(api_description, operation_name) AS api_description, create_time " +
                            "FROM " + table + " l " +
                            finalFilterSql;
                })
                .collect(Collectors.joining(" UNION ALL "));

        String pageSql = "SELECT * FROM (" + unionSql + ") audit_logs ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args.size() * tables.size() + 2);
        for (int i = 0; i < tables.size(); i++) {
            pageArgs.addAll(args);
        }
        pageArgs.add(safeSize);
        pageArgs.add(offset);

        List<AuditLogRecordEntity> records = jdbcTemplate.query(pageSql, AUDIT_ROW_MAPPER, pageArgs.toArray());
        return new AuditLogPage(records, total, safePage, safeSize);
    }

    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanupExpiredLogs() {
        AuditLogConfigEntity config = getConfig();
        int retentionDays = Math.max(config.getRetentionDays() == null ? properties.getRetentionDays() : config.getRetentionDays(), 1);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        for (String category : CATEGORIES) {
            List<String> tables = listCategoryTables(category);
            MetaRow current = loadMeta(category);
            for (String table : tables) {
                validateTableName(table);
                try {
                    jdbcTemplate.update("DELETE FROM " + table + " WHERE create_time < ?", cutoff);
                    if (current != null && !table.equalsIgnoreCase(current.getCurrentTableName())) {
                        Long remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
                        if (remaining != null && remaining == 0) {
                            jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
                        }
                    }
                } catch (Exception e) {
                    log.warn("清理审计日志失败 table={}: {}", table, e.getMessage());
                }
            }
            refreshCurrentRowCount(category);
        }
    }

    private long resolveMaxRowsPerTable(AuditLogConfigEntity config) {
        long configured = config.getMaxRowsPerTable() == null ? properties.getMaxRowsPerTable() : config.getMaxRowsPerTable();
        return Math.max(configured, 10_000L);
    }

    private void rotateTable(String category, MetaRow current, long nextStartId) {
        int nextSuffix = current.getCurrentSuffix() + 1;
        String nextTable = buildTableName(category, nextSuffix);
        validateTableName(nextTable);
        createAuditTable(nextTable, nextStartId);
        jdbcTemplate.update(
                "UPDATE security_audit_table_meta SET current_suffix = ?, current_table_name = ?, row_count = 0, next_start_id = ?, update_time = NOW() WHERE category = ?",
                nextSuffix,
                nextTable,
                Math.max(nextStartId, 1L),
                category
        );
        log.info("审计日志已自动分表: category={}, newTable={}, nextStartId={}", category, nextTable, nextStartId);
    }

    private void ensureCategoryMeta(String category) {
        String normalized = normalizeCategory(category);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security_audit_table_meta WHERE category = ?",
                Integer.class,
                normalized
        );
        if (count != null && count > 0) {
            return;
        }
        String tableName = buildTableName(normalized, 1);
        createAuditTable(tableName, 1L);
        jdbcTemplate.update(
                "INSERT INTO security_audit_table_meta (category, current_suffix, current_table_name, next_start_id, row_count, create_time, update_time) " +
                        "VALUES (?, 1, ?, 1, 0, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE current_table_name = VALUES(current_table_name), update_time = NOW()",
                normalized,
                tableName
        );
    }

    private void refreshCurrentRowCount(String category) {
        MetaRow current = loadMeta(category);
        if (current == null) {
            return;
        }
        validateTableName(current.getCurrentTableName());
        Long rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + current.getCurrentTableName(), Long.class);
        jdbcTemplate.update(
                "UPDATE security_audit_table_meta SET row_count = ?, update_time = NOW() WHERE category = ?",
                rowCount == null ? 0 : rowCount,
                normalizeCategory(category)
        );
    }

    private MetaRow loadMeta(String category) {
        List<MetaRow> records = jdbcTemplate.query(
                "SELECT category, current_suffix, current_table_name, next_start_id, row_count, create_time, update_time " +
                        "FROM security_audit_table_meta WHERE category = ?",
                (rs, rowNum) -> {
                    MetaRow meta = new MetaRow();
                    meta.setCategory(rs.getString("category"));
                    meta.setCurrentSuffix(rs.getInt("current_suffix"));
                    meta.setCurrentTableName(rs.getString("current_table_name"));
                    meta.setNextStartId(rs.getLong("next_start_id"));
                    meta.setRowCount(rs.getLong("row_count"));
                    meta.setCreateTime(toLocalDateTime(rs, "create_time"));
                    meta.setUpdateTime(toLocalDateTime(rs, "update_time"));
                    return meta;
                },
                normalizeCategory(category)
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private List<String> listCategoryTables(String category) {
        String prefix = "security_audit_" + normalizeCategory(category).toLowerCase(Locale.ROOT) + "_";
        return jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name LIKE ? ORDER BY table_name DESC",
                String.class,
                prefix + "%"
        ).stream().filter(this::isAllowedTableName).collect(Collectors.toList());
    }

    private void createAuditTable(String tableName, long autoIncrementStart) {
        validateTableName(tableName);
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                        "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                        "category VARCHAR(32) NOT NULL COMMENT '审计分类'," +
                        "event_type VARCHAR(64) NULL COMMENT '事件类型'," +
                        "module_name VARCHAR(128) NULL COMMENT '模块名称'," +
                        "operation_name VARCHAR(128) NULL COMMENT '操作名称'," +
                        "resource_type VARCHAR(64) NULL COMMENT '资源类型'," +
                        "resource_id VARCHAR(128) NULL COMMENT '资源标识'," +
                        "success TINYINT DEFAULT 1 COMMENT '是否成功'," +
                        "detail_text VARCHAR(2000) NULL COMMENT '详情'," +
                        "request_method VARCHAR(16) NULL COMMENT '请求方法'," +
                        "request_uri VARCHAR(500) NULL COMMENT '请求路径'," +
                        "client_ip VARCHAR(64) NULL COMMENT '客户端IP'," +
                        "username VARCHAR(128) NULL COMMENT '用户名'," +
                        "user_id BIGINT NULL COMMENT '用户ID'," +
                        "login_type VARCHAR(64) NULL COMMENT '登录类型'," +
                        "response_code INT NULL COMMENT '响应状态码'," +
                        "trace_id VARCHAR(128) NULL COMMENT '链路ID'," +
                        "ext_json TEXT NULL COMMENT '扩展数据'," +
                        "api_description VARCHAR(500) NULL COMMENT '接口描述(来自api_endpoints表)'," +
                        "create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                        "PRIMARY KEY (id)," +
                        "INDEX idx_audit_create_time (create_time)," +
                        "INDEX idx_audit_user_time (user_id, create_time)," +
                        "INDEX idx_audit_uri_time (request_uri(255), create_time)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志分表'"
        );
        if (autoIncrementStart > 1) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " AUTO_INCREMENT = " + autoIncrementStart);
        }
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "GLOBAL";
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(normalized) ? normalized : "GLOBAL";
    }

    private void validateTableName(String tableName) {
        if (!isAllowedTableName(tableName)) {
            throw new IllegalArgumentException("非法审计表名: " + tableName);
        }
    }

    private boolean isAllowedTableName(String tableName) {
        return StringUtils.hasText(tableName) && TABLE_NAME_PATTERN.matcher(tableName).matches();
    }

    private AuditLogConfigEntity buildFallbackConfig() {
        AuditLogConfigEntity fallback = new AuditLogConfigEntity();
        fallback.setId(1L);
        fallback.setEnabled(properties.isEnabled() ? 1 : 0);
        fallback.setRetentionDays(properties.getRetentionDays());
        fallback.setMaxRowsPerTable(properties.getMaxRowsPerTable());
        fallback.setQueueCapacity(properties.getQueueCapacity());
        return fallback;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private String buildTableName(String category, int suffix) {
        return "security_audit_" + normalizeCategory(category).toLowerCase(Locale.ROOT) + "_" + String.format("%04d", suffix);
    }

    private String trim(String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public static class AuditLogPage {
        private List<AuditLogRecordEntity> records;
        private long total;
        private int page;
        private int size;
        private int totalPages;

        public AuditLogPage(List<AuditLogRecordEntity> records, long total, int page, int size) {
            this.records = records;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil(size == 0 ? 0 : (double) total / size);
        }

        public List<AuditLogRecordEntity> getRecords() {
            return records;
        }

        public long getTotal() {
            return total;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    private static class MetaRow {
        private String category;
        private Integer currentSuffix;
        private String currentTableName;
        private Long nextStartId;
        private Long rowCount;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Integer getCurrentSuffix() {
            return currentSuffix;
        }

        public void setCurrentSuffix(Integer currentSuffix) {
            this.currentSuffix = currentSuffix;
        }

        public String getCurrentTableName() {
            return currentTableName;
        }

        public void setCurrentTableName(String currentTableName) {
            this.currentTableName = currentTableName;
        }

        public Long getNextStartId() {
            return nextStartId == null ? 1L : nextStartId;
        }

        public void setNextStartId(Long nextStartId) {
            this.nextStartId = nextStartId;
        }

        public Long getRowCount() {
            return rowCount == null ? 0L : rowCount;
        }

        public void setRowCount(Long rowCount) {
            this.rowCount = rowCount;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }

    private static class AuditLogRowMapper implements RowMapper<AuditLogRecordEntity> {
        @Override
        public AuditLogRecordEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AuditLogRecordEntity entity = new AuditLogRecordEntity();
            entity.setId(rs.getLong("id"));
            entity.setCategory(rs.getString("category"));
            entity.setEventType(rs.getString("event_type"));
            entity.setModuleName(rs.getString("module_name"));
            entity.setOperationName(rs.getString("operation_name"));
            entity.setResourceType(rs.getString("resource_type"));
            entity.setResourceId(rs.getString("resource_id"));
            entity.setSuccess(rs.getInt("success"));
            entity.setDetailText(rs.getString("detail_text"));
            entity.setRequestMethod(rs.getString("request_method"));
            entity.setRequestUri(rs.getString("request_uri"));
            entity.setClientIp(rs.getString("client_ip"));
            entity.setUsername(rs.getString("username"));
            long userId = rs.getLong("user_id");
            entity.setUserId(rs.wasNull() ? null : userId);
            entity.setLoginType(rs.getString("login_type"));
            int responseCode = rs.getInt("response_code");
            entity.setResponseCode(rs.wasNull() ? null : responseCode);
            entity.setTraceId(rs.getString("trace_id"));
            entity.setExtJson(rs.getString("ext_json"));
            entity.setApiDescription(rs.getString("api_description"));
            entity.setCreateTime(toLocalDateTime(rs, "create_time"));
            return entity;
        }
    }
}
