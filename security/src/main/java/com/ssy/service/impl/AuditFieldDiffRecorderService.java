package com.ssy.service.impl;

import com.alibaba.fastjson.JSON;
import com.ssy.context.RequestUserContext;
import com.ssy.entity.AuditLogRecordEntity;
import com.ssy.holder.RequestUserContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class AuditFieldDiffRecorderService {

    private final AuditLogAsyncRecorderService auditLogAsyncRecorderService;

    public AuditFieldDiffRecorderService(AuditLogAsyncRecorderService auditLogAsyncRecorderService) {
        this.auditLogAsyncRecorderService = auditLogAsyncRecorderService;
    }

    public void recordBusinessDiff(String moduleName,
                                   String operationName,
                                   String resourceType,
                                   String resourceId,
                                   Object before,
                                   Object after) {
        record("BUSINESS", moduleName, operationName, resourceType, resourceId, before, after);
    }

    public void recordSecurityDiff(String moduleName,
                                   String operationName,
                                   String resourceType,
                                   String resourceId,
                                   Object before,
                                   Object after) {
        record("SECURITY", moduleName, operationName, resourceType, resourceId, before, after);
    }

    private void record(String category,
                        String moduleName,
                        String operationName,
                        String resourceType,
                        String resourceId,
                        Object before,
                        Object after) {
        Map<String, Object> diff = buildDiff(before, after);
        if (diff.isEmpty()) {
            return;
        }
        RequestUserContext context = RequestUserContextHolder.get();
        AuditLogRecordEntity entity = new AuditLogRecordEntity();
        entity.setCategory(category);
        entity.setEventType("FIELD_DIFF");
        entity.setModuleName(moduleName);
        entity.setOperationName(operationName);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setSuccess(1);
        entity.setDetailText("字段变更数=" + diff.size());
        entity.setRequestMethod(context == null ? null : context.getRequestMethod());
        entity.setRequestUri(context == null ? null : context.getRequestUri());
        entity.setClientIp(context == null ? null : context.getClientIp());
        entity.setUsername(context == null ? null : context.getUsername());
        entity.setUserId(context == null ? null : context.getUserId());
        entity.setLoginType(context == null ? null : context.getLoginType());
        entity.setExtJson(JSON.toJSONString(diff));
        entity.setCreateTime(LocalDateTime.now());
        auditLogAsyncRecorderService.record(entity);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildDiff(Object before, Object after) {
        Map<String, Object> beforeMap = sanitize(JSON.parseObject(JSON.toJSONString(before == null ? new LinkedHashMap<>() : before), LinkedHashMap.class));
        Map<String, Object> afterMap = sanitize(JSON.parseObject(JSON.toJSONString(after == null ? new LinkedHashMap<>() : after), LinkedHashMap.class));
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String key : unionKeys(beforeMap, afterMap).keySet()) {
            Object beforeValue = beforeMap.get(key);
            Object afterValue = afterMap.get(key);
            if (!Objects.equals(beforeValue, afterValue)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("before", beforeValue);
                item.put("after", afterValue);
                diff.put(key, item);
            }
        }
        return diff;
    }

    private Map<String, Object> sanitize(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if ("password".equalsIgnoreCase(key)) {
                result.put(key, mask(entry.getValue()));
                continue;
            }
            if ("authorities".equalsIgnoreCase(key)) {
                continue;
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private Object mask(Object value) {
        return value == null ? null : "***";
    }

    private Map<String, Boolean> unionKeys(Map<String, Object> beforeMap, Map<String, Object> afterMap) {
        Map<String, Boolean> keys = new LinkedHashMap<>();
        beforeMap.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
        afterMap.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
        return keys;
    }
}
