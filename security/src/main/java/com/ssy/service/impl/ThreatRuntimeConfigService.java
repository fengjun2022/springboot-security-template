package com.ssy.service.impl;

import com.ssy.properties.ThreatDetectionProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ThreatRuntimeConfigService implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;
    private final ThreatDetectionProperties properties;

    public ThreatRuntimeConfigService(JdbcTemplate jdbcTemplate, ThreatDetectionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        loadFromDatabase();
    }

    public ThreatDetectionProperties getCurrentConfig() {
        return properties;
    }

    public ThreatDetectionProperties update(ThreatDetectionProperties payload) {
        if (payload == null) {
            return properties;
        }
        properties.setEnabled(payload.isEnabled());
        properties.setMonitorUnknownEndpoints(payload.isMonitorUnknownEndpoints());
        properties.setTrustForwardHeaders(payload.isTrustForwardHeaders());
        properties.setCaptureBodySample(payload.isCaptureBodySample());
        properties.setMaxInspectBodyBytes(Math.max(payload.getMaxInspectBodyBytes(), 512));
        properties.setGlobalWindowMs(Math.max(payload.getGlobalWindowMs(), 1000L));
        properties.setGlobalWindowLimit(Math.max(payload.getGlobalWindowLimit(), 1));
        properties.setEndpointWindowMs(Math.max(payload.getEndpointWindowMs(), 1000L));
        properties.setEndpointWindowLimit(Math.max(payload.getEndpointWindowLimit(), 1));
        properties.setAutoBlockSeconds(Math.max(payload.getAutoBlockSeconds(), 0));
        properties.setAutoBlockMultiplier(Math.max(payload.getAutoBlockMultiplier(), 1));
        properties.setAuthFeedbackWindowMs(Math.max(payload.getAuthFeedbackWindowMs(), 1000L));
        properties.setAuth401FeedbackThreshold(Math.max(payload.getAuth401FeedbackThreshold(), 1));
        properties.setAuth403FeedbackThreshold(Math.max(payload.getAuth403FeedbackThreshold(), 1));
        properties.setAuth403AutoBlockThreshold(Math.max(payload.getAuth403AutoBlockThreshold(), 1));
        properties.setEventQueueCapacity(Math.max(payload.getEventQueueCapacity(), 256));
        properties.setBlacklistQueueCapacity(Math.max(payload.getBlacklistQueueCapacity(), 256));
        properties.setDeviceRiskEnabled(payload.isDeviceRiskEnabled());
        properties.setDeviceRiskCaptchaScoreThreshold(Math.max(payload.getDeviceRiskCaptchaScoreThreshold(), 1));
        properties.setDeviceRiskBlockScoreThreshold(Math.max(payload.getDeviceRiskBlockScoreThreshold(), 1));
        properties.setDeviceRiskNewDeviceScore(Math.max(payload.getDeviceRiskNewDeviceScore(), 0));
        properties.setDeviceRiskIpDriftScore(Math.max(payload.getDeviceRiskIpDriftScore(), 0));
        properties.setDeviceRiskUaDriftScore(Math.max(payload.getDeviceRiskUaDriftScore(), 0));
        properties.setDeviceRiskMultiAccountScore(Math.max(payload.getDeviceRiskMultiAccountScore(), 0));
        properties.setDeviceRiskFailurePenalty(Math.max(payload.getDeviceRiskFailurePenalty(), 1));
        properties.setDeviceRiskAccountSwitchWindowMs(Math.max(payload.getDeviceRiskAccountSwitchWindowMs(), 60_000L));
        properties.setDeviceRiskAccountSwitchThreshold(Math.max(payload.getDeviceRiskAccountSwitchThreshold(), 1));

        jdbcTemplate.update(
                "INSERT INTO security_threat_config (" +
                        "id, enabled, monitor_unknown_endpoints, trust_forward_headers, capture_body_sample, max_inspect_body_bytes, " +
                        "global_window_ms, global_window_limit, endpoint_window_ms, endpoint_window_limit, auto_block_seconds, auto_block_multiplier, " +
                        "auth_feedback_window_ms, auth401_feedback_threshold, auth403_feedback_threshold, auth403_auto_block_threshold, " +
                        "event_queue_capacity, blacklist_queue_capacity, device_risk_enabled, device_risk_captcha_score_threshold, " +
                        "device_risk_block_score_threshold, device_risk_new_device_score, device_risk_ip_drift_score, " +
                        "device_risk_ua_drift_score, device_risk_multi_account_score, device_risk_failure_penalty, " +
                        "device_risk_account_switch_window_ms, device_risk_account_switch_threshold, create_time, update_time" +
                        ") VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), " +
                        "monitor_unknown_endpoints = VALUES(monitor_unknown_endpoints), " +
                        "trust_forward_headers = VALUES(trust_forward_headers), " +
                        "capture_body_sample = VALUES(capture_body_sample), " +
                        "max_inspect_body_bytes = VALUES(max_inspect_body_bytes), " +
                        "global_window_ms = VALUES(global_window_ms), " +
                        "global_window_limit = VALUES(global_window_limit), " +
                        "endpoint_window_ms = VALUES(endpoint_window_ms), " +
                        "endpoint_window_limit = VALUES(endpoint_window_limit), " +
                        "auto_block_seconds = VALUES(auto_block_seconds), " +
                        "auto_block_multiplier = VALUES(auto_block_multiplier), " +
                        "auth_feedback_window_ms = VALUES(auth_feedback_window_ms), " +
                        "auth401_feedback_threshold = VALUES(auth401_feedback_threshold), " +
                        "auth403_feedback_threshold = VALUES(auth403_feedback_threshold), " +
                        "auth403_auto_block_threshold = VALUES(auth403_auto_block_threshold), " +
                        "event_queue_capacity = VALUES(event_queue_capacity), " +
                        "blacklist_queue_capacity = VALUES(blacklist_queue_capacity), " +
                        "device_risk_enabled = VALUES(device_risk_enabled), " +
                        "device_risk_captcha_score_threshold = VALUES(device_risk_captcha_score_threshold), " +
                        "device_risk_block_score_threshold = VALUES(device_risk_block_score_threshold), " +
                        "device_risk_new_device_score = VALUES(device_risk_new_device_score), " +
                        "device_risk_ip_drift_score = VALUES(device_risk_ip_drift_score), " +
                        "device_risk_ua_drift_score = VALUES(device_risk_ua_drift_score), " +
                        "device_risk_multi_account_score = VALUES(device_risk_multi_account_score), " +
                        "device_risk_failure_penalty = VALUES(device_risk_failure_penalty), " +
                        "device_risk_account_switch_window_ms = VALUES(device_risk_account_switch_window_ms), " +
                        "device_risk_account_switch_threshold = VALUES(device_risk_account_switch_threshold), update_time = NOW()",
                properties.isEnabled() ? 1 : 0,
                properties.isMonitorUnknownEndpoints() ? 1 : 0,
                properties.isTrustForwardHeaders() ? 1 : 0,
                properties.isCaptureBodySample() ? 1 : 0,
                properties.getMaxInspectBodyBytes(),
                properties.getGlobalWindowMs(),
                properties.getGlobalWindowLimit(),
                properties.getEndpointWindowMs(),
                properties.getEndpointWindowLimit(),
                properties.getAutoBlockSeconds(),
                properties.getAutoBlockMultiplier(),
                properties.getAuthFeedbackWindowMs(),
                properties.getAuth401FeedbackThreshold(),
                properties.getAuth403FeedbackThreshold(),
                properties.getAuth403AutoBlockThreshold(),
                properties.getEventQueueCapacity(),
                properties.getBlacklistQueueCapacity(),
                properties.isDeviceRiskEnabled() ? 1 : 0,
                properties.getDeviceRiskCaptchaScoreThreshold(),
                properties.getDeviceRiskBlockScoreThreshold(),
                properties.getDeviceRiskNewDeviceScore(),
                properties.getDeviceRiskIpDriftScore(),
                properties.getDeviceRiskUaDriftScore(),
                properties.getDeviceRiskMultiAccountScore(),
                properties.getDeviceRiskFailurePenalty(),
                properties.getDeviceRiskAccountSwitchWindowMs(),
                properties.getDeviceRiskAccountSwitchThreshold()
        );
        return properties;
    }

    private void loadFromDatabase() {
        if (!tableExists("security_threat_config")) {
            return;
        }
        List<ThreatDetectionProperties> list = jdbcTemplate.query(
                "SELECT enabled, monitor_unknown_endpoints, trust_forward_headers, capture_body_sample, max_inspect_body_bytes, " +
                        "global_window_ms, global_window_limit, endpoint_window_ms, endpoint_window_limit, auto_block_seconds, auto_block_multiplier, " +
                        "auth_feedback_window_ms, auth401_feedback_threshold, auth403_feedback_threshold, auth403_auto_block_threshold, " +
                        "event_queue_capacity, blacklist_queue_capacity, device_risk_enabled, device_risk_captcha_score_threshold, " +
                        "device_risk_block_score_threshold, device_risk_new_device_score, device_risk_ip_drift_score, " +
                        "device_risk_ua_drift_score, device_risk_multi_account_score, device_risk_failure_penalty, " +
                        "device_risk_account_switch_window_ms, device_risk_account_switch_threshold FROM security_threat_config WHERE id = 1",
                (rs, rowNum) -> {
                    ThreatDetectionProperties entity = new ThreatDetectionProperties();
                    entity.setEnabled(rs.getInt("enabled") == 1);
                    entity.setMonitorUnknownEndpoints(rs.getInt("monitor_unknown_endpoints") == 1);
                    entity.setTrustForwardHeaders(rs.getInt("trust_forward_headers") == 1);
                    entity.setCaptureBodySample(rs.getInt("capture_body_sample") == 1);
                    entity.setMaxInspectBodyBytes(rs.getInt("max_inspect_body_bytes"));
                    entity.setGlobalWindowMs(rs.getLong("global_window_ms"));
                    entity.setGlobalWindowLimit(rs.getInt("global_window_limit"));
                    entity.setEndpointWindowMs(rs.getLong("endpoint_window_ms"));
                    entity.setEndpointWindowLimit(rs.getInt("endpoint_window_limit"));
                    entity.setAutoBlockSeconds(rs.getInt("auto_block_seconds"));
                    entity.setAutoBlockMultiplier(rs.getInt("auto_block_multiplier"));
                    entity.setAuthFeedbackWindowMs(rs.getLong("auth_feedback_window_ms"));
                    entity.setAuth401FeedbackThreshold(rs.getInt("auth401_feedback_threshold"));
                    entity.setAuth403FeedbackThreshold(rs.getInt("auth403_feedback_threshold"));
                    entity.setAuth403AutoBlockThreshold(rs.getInt("auth403_auto_block_threshold"));
                    entity.setEventQueueCapacity(rs.getInt("event_queue_capacity"));
                    entity.setBlacklistQueueCapacity(rs.getInt("blacklist_queue_capacity"));
                    entity.setDeviceRiskEnabled(rs.getInt("device_risk_enabled") == 1);
                    entity.setDeviceRiskCaptchaScoreThreshold(rs.getInt("device_risk_captcha_score_threshold"));
                    entity.setDeviceRiskBlockScoreThreshold(rs.getInt("device_risk_block_score_threshold"));
                    entity.setDeviceRiskNewDeviceScore(rs.getInt("device_risk_new_device_score"));
                    entity.setDeviceRiskIpDriftScore(rs.getInt("device_risk_ip_drift_score"));
                    entity.setDeviceRiskUaDriftScore(rs.getInt("device_risk_ua_drift_score"));
                    entity.setDeviceRiskMultiAccountScore(rs.getInt("device_risk_multi_account_score"));
                    entity.setDeviceRiskFailurePenalty(rs.getInt("device_risk_failure_penalty"));
                    entity.setDeviceRiskAccountSwitchWindowMs(rs.getLong("device_risk_account_switch_window_ms"));
                    entity.setDeviceRiskAccountSwitchThreshold(rs.getInt("device_risk_account_switch_threshold"));
                    return entity;
                }
        );
        if (list.isEmpty()) {
            return;
        }
        update(list.get(0));
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
