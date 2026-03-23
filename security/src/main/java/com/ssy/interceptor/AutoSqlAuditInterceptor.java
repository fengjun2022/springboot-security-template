package com.ssy.interceptor;

import com.alibaba.fastjson.JSON;
import com.ssy.context.AuditTraceContext;
import com.ssy.context.RequestUserContext;
import com.ssy.entity.AuditLogRecordEntity;
import com.ssy.holder.AuditTraceContextHolder;
import com.ssy.holder.RequestUserContextHolder;
import com.ssy.service.impl.AuditLogAsyncRecorderService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
@Component
public class AutoSqlAuditInterceptor implements Interceptor {

    private static final Pattern INSERT_PATTERN = Pattern.compile("(?i)\\binsert\\s+into\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("(?i)\\bupdate\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern DELETE_PATTERN = Pattern.compile("(?i)\\bdelete\\s+from\\s+`?([a-zA-Z0-9_]+)`?");

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogAsyncRecorderService auditLogAsyncRecorderService;

    public AutoSqlAuditInterceptor(JdbcTemplate jdbcTemplate,
                                   AuditLogAsyncRecorderService auditLogAsyncRecorderService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogAsyncRecorderService = auditLogAsyncRecorderService;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameterObject = invocation.getArgs()[1];
        SqlCommandType commandType = mappedStatement.getSqlCommandType();
        if (!isAuditableCommand(commandType)) {
            return invocation.proceed();
        }

        // 没有 HTTP 请求上下文（定时任务、内部服务等程序自动操作）不记录审计日志
        if (RequestUserContextHolder.get() == null) {
            return invocation.proceed();
        }

        BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
        String rawSql = normalizeSql(boundSql.getSql());
        String tableName = extractTableName(rawSql, commandType);
        if (!StringUtils.hasText(tableName) || tableName.startsWith("security_audit_")) {
            return invocation.proceed();
        }

        IdentifierKey identifierKey = resolveIdentifier(tableName, parameterObject);
        Map<String, Object> beforeRow = fetchRowSnapshot(tableName, identifierKey);

        Object result = invocation.proceed();

        Map<String, Object> afterRow = commandType == SqlCommandType.DELETE
                ? null
                : fetchRowSnapshot(tableName, identifierKey);

        recordAudit(mappedStatement, commandType, tableName, rawSql, parameterObject, beforeRow, afterRow, result, identifierKey);
        return result;
    }

    private void recordAudit(MappedStatement mappedStatement,
                             SqlCommandType commandType,
                             String tableName,
                             String rawSql,
                             Object parameterObject,
                             Map<String, Object> beforeRow,
                             Map<String, Object> afterRow,
                             Object result,
                             IdentifierKey identifierKey) {
        AuditTraceContext traceContext = AuditTraceContextHolder.get();
        RequestUserContext userContext = RequestUserContextHolder.get();

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("mode", "AUTO_SQL_AUDIT");
        ext.put("mappedStatementId", mappedStatement.getId());
        ext.put("sqlCommandType", commandType.name());
        ext.put("tableName", tableName);
        ext.put("sql", rawSql);
        ext.put("params", sanitize(flattenParams(parameterObject)));
        ext.put("identifier", identifierKey == null ? null : identifierKey.toMap());
        ext.put("before", sanitize(beforeRow));
        ext.put("after", sanitize(afterRow));
        ext.put("affectedRows", result);
        if (traceContext != null) {
          ext.put("requestBodySample", traceContext.getRequestBodySample());
          ext.put("responseBodySample", traceContext.getResponseBodySample());
          ext.put("endpointId", traceContext.getEndpointId());
          ext.put("moduleGroup", traceContext.getModuleGroup());
          ext.put("permissionCodes", traceContext.getPermissionCodes());
        }

        AuditLogRecordEntity entity = new AuditLogRecordEntity();
        entity.setCategory(resolveCategory(tableName));
        entity.setEventType(commandType.name());
        entity.setModuleName(traceContext == null ? extractModuleFromStatement(mappedStatement.getId()) : traceContext.getModuleGroup());
        entity.setOperationName(mappedStatement.getId());
        entity.setResourceType(tableName);
        entity.setResourceId(identifierKey == null ? null : String.valueOf(identifierKey.value));
        entity.setSuccess(1);
        entity.setDetailText(buildDetailText(beforeRow, afterRow));
        entity.setRequestMethod(traceContext == null ? (userContext == null ? null : userContext.getRequestMethod()) : traceContext.getRequestMethod());
        entity.setRequestUri(traceContext == null ? (userContext == null ? null : userContext.getRequestUri()) : traceContext.getRequestUri());
        entity.setClientIp(traceContext == null ? (userContext == null ? null : userContext.getClientIp()) : traceContext.getClientIp());
        entity.setUsername(userContext == null ? null : userContext.getUsername());
        entity.setUserId(userContext == null ? null : userContext.getUserId());
        entity.setLoginType(userContext == null ? null : userContext.getLoginType());
        entity.setTraceId(traceContext == null ? null : traceContext.getTraceId());
        entity.setExtJson(JSON.toJSONString(ext));
        entity.setCreateTime(LocalDateTime.now());
        auditLogAsyncRecorderService.record(entity);
    }

    private String buildDetailText(Map<String, Object> beforeRow, Map<String, Object> afterRow) {
        if (beforeRow == null && afterRow != null) {
            return "自动审计: 新增";
        }
        if (beforeRow != null && afterRow == null) {
            return "自动审计: 删除";
        }
        if (beforeRow != null) {
            return "自动审计: 更新";
        }
        return "自动审计: SQL";
    }

    private String resolveCategory(String tableName) {
        if (tableName.startsWith("security_") || tableName.startsWith("sys_permission") || tableName.startsWith("api_endpoints")) {
            return "SECURITY";
        }
        return "BUSINESS";
    }

    private String extractModuleFromStatement(String statementId) {
        if (!StringUtils.hasText(statementId)) {
            return "MYBATIS";
        }
        int lastDot = statementId.lastIndexOf('.');
        return lastDot > 0 ? statementId.substring(0, lastDot) : statementId;
    }

    private Map<String, Object> fetchRowSnapshot(String tableName, IdentifierKey identifierKey) {
        if (identifierKey == null || !StringUtils.hasText(identifierKey.columnName)) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM " + tableName + " WHERE " + identifierKey.columnName + " = ? LIMIT 1",
                    identifierKey.value
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private IdentifierKey resolveIdentifier(String tableName, Object parameterObject) {
        Map<String, Object> params = flattenParams(parameterObject);
        List<IdentifierCandidate> candidates = Arrays.asList(
                new IdentifierCandidate("id", "id"),
                new IdentifierCandidate("routeId", "id"),
                new IdentifierCandidate("roleId", "id"),
                new IdentifierCandidate("permissionId", "id"),
                new IdentifierCandidate("endpointId", "id"),
                new IdentifierCandidate("userId", "user_id"),
                new IdentifierCandidate("appId", "app_id"),
                new IdentifierCandidate("ip", "ip"),
                new IdentifierCandidate("ipOrCidr", "ip_or_cidr")
        );
        for (IdentifierCandidate candidate : candidates) {
            if (params.containsKey(candidate.paramKey) && params.get(candidate.paramKey) != null) {
                return new IdentifierKey(candidate.columnName, params.get(candidate.paramKey));
            }
        }
        if ("user".equalsIgnoreCase(tableName) && params.containsKey("username")) {
            return new IdentifierKey("username", params.get("username"));
        }
        return null;
    }

    private Map<String, Object> flattenParams(Object parameterObject) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (parameterObject == null) {
            return result;
        }
        if (parameterObject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) parameterObject;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                Object value = entry.getValue();
                result.put(String.valueOf(entry.getKey()), simpleValueOrMap(value));
                if (value != null && !isSimpleValue(value)) {
                    extractFields(value, result);
                }
            }
            return result;
        }
        extractFields(parameterObject, result);
        return result;
    }

    private void extractFields(Object source, Map<String, Object> bucket) {
        if (source == null) {
            return;
        }
        ReflectionUtils.doWithFields(source.getClass(), field -> {
            ReflectionUtils.makeAccessible(field);
            Object value = field.get(source);
            if (value == null || isSyntheticField(field)) {
                return;
            }
            bucket.putIfAbsent(field.getName(), simpleValueOrMap(value));
        });
    }

    private boolean isSyntheticField(Field field) {
        return java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getName().startsWith("$");
    }

    private Object simpleValueOrMap(Object value) {
        if (value == null || isSimpleValue(value)) {
            return value;
        }
        return sanitize(JSON.parseObject(JSON.toJSONString(value), LinkedHashMap.class));
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof java.util.Date
                || value instanceof java.time.temporal.Temporal;
    }

    private Map<String, Object> sanitize(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if ("password".equalsIgnoreCase(key)) {
                sanitized.put(key, "***");
                return;
            }
            sanitized.put(key, value);
        });
        return sanitized;
    }

    private String extractTableName(String sql, SqlCommandType commandType) {
        if (!StringUtils.hasText(sql)) {
            return null;
        }
        Pattern pattern = commandType == SqlCommandType.INSERT
                ? INSERT_PATTERN
                : commandType == SqlCommandType.UPDATE
                ? UPDATE_PATTERN
                : DELETE_PATTERN;
        Matcher matcher = pattern.matcher(sql);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isAuditableCommand(SqlCommandType commandType) {
        return commandType == SqlCommandType.INSERT
                || commandType == SqlCommandType.UPDATE
                || commandType == SqlCommandType.DELETE;
    }

    private String normalizeSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private static class IdentifierCandidate {
        private final String paramKey;
        private final String columnName;

        private IdentifierCandidate(String paramKey, String columnName) {
            this.paramKey = paramKey;
            this.columnName = columnName;
        }
    }

    private static class IdentifierKey {
        private final String columnName;
        private final Object value;

        private IdentifierKey(String columnName, Object value) {
            this.columnName = columnName;
            this.value = value;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("column", columnName);
            map.put("value", value);
            return map;
        }
    }
}
