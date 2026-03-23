package com.ssy.utils;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AttackTypeLabelUtils {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("AUTH_PROBE_401", "认证探测(401)");
        LABELS.put("PRIVILEGE_PROBE_403", "权限探测(403)");
        LABELS.put("PRIVILEGE_PROBE", "权限路径探测");
        LABELS.put("VERTICAL_PRIVILEGE_ESCALATION", "纵向越权攻击");
        LABELS.put("HORIZONTAL_PRIVILEGE_ESCALATION", "横向越权攻击");
        LABELS.put("SQL_INJECTION", "SQL注入攻击");
        LABELS.put("XSS_ATTACK", "XSS跨站脚本攻击");
        LABELS.put("JS_INJECTION", "前端脚本注入");
        LABELS.put("PATH_TRAVERSAL", "路径穿越攻击");
        LABELS.put("SCANNER_PROBE", "接口扫描探测");
        LABELS.put("DEPENDENCY_PROBE", "依赖组件探测");
        LABELS.put("AUTOMATION_TOOL", "自动化工具攻击");
        LABELS.put("BRUTE_FORCE_LOGIN", "登录爆破攻击");
        LABELS.put("LOGIN_FAILURE", "登录失败异常");
        LABELS.put("WEAK_PASSWORD_ATTACK", "弱口令攻击");
        LABELS.put("HIGH_RISK_DEVICE_LOGIN", "高风险设备登录");
    }

    private AttackTypeLabelUtils() {
    }

    public static String resolve(String attackType) {
        if (!StringUtils.hasText(attackType)) {
            return "-";
        }
        return LABELS.getOrDefault(attackType.trim(), attackType.trim());
    }
}
