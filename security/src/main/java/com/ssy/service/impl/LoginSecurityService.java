package com.ssy.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginSecurityService {

    private static final int CAPTCHA_THRESHOLD = 5;
    private static final long CAPTCHA_EXPIRE_SECONDS = 300L;
    private static final long FAILURE_EXPIRE_SECONDS = 1800L;
    private static final String[] WEAK_PASSWORDS = {
            "123456", "12345678", "123456789", "1234567890", "password",
            "qwerty", "admin", "admin123", "root", "root123", "111111",
            "abc123", "888888", "000000", "changeme"
    };

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, FailureState> failureStateMap = new ConcurrentHashMap<>();
    private final Map<String, CaptchaState> captchaStateMap = new ConcurrentHashMap<>();
    private final DeviceRiskEngineService deviceRiskEngineService;
    public LoginSecurityService(DeviceRiskEngineService deviceRiskEngineService) {
        this.deviceRiskEngineService = deviceRiskEngineService;
    }

    public CaptchaPayload getCaptcha(String ip,
                                     String username,
                                     String browserFingerprint,
                                     String userAgent) {
        cleanupExpired();
        // 每次获取验证码都递增刷新计数
        FailureState failureState = getFailureState(ip, username, true);
        failureState.captchaRefreshCount++;
        int failureCount = failureState.failureCount;
        DeviceRiskEngineService.DeviceRiskAssessment assessment = deviceRiskEngineService.assess(
                ip,
                username,
                browserFingerprint,
                userAgent,
                failureCount,
                failureState.captchaRefreshCount,
                failureState.captchaFailCount
        );
        return buildPayload(ip, username, failureCount, assessment, true, null);
    }

    public ValidationResult validateBeforeLogin(String ip,
                                                String username,
                                                String captchaToken,
                                                String captchaCode,
                                                String browserFingerprint,
                                                String userAgent) {
        cleanupExpired();
        FailureState failureState = getFailureState(ip, username, false);
        int failureCount = failureState == null ? 0 : failureState.failureCount;
        int captchaRefreshCount = failureState == null ? 0 : failureState.captchaRefreshCount;
        int captchaFailCount = failureState == null ? 0 : failureState.captchaFailCount;
        DeviceRiskEngineService.DeviceRiskAssessment assessment = deviceRiskEngineService.assess(
                ip,
                username,
                browserFingerprint,
                userAgent,
                failureCount,
                captchaRefreshCount,
                captchaFailCount
        );
        if (assessment.isBlocked()) {
            return ValidationResult.fail("DEVICE_RISK_BLOCKED",
                    buildPayload(ip, username, failureCount, assessment, true, "当前设备风险过高，请联系管理员"));
        }

        boolean captchaRequired = failureCount >= CAPTCHA_THRESHOLD || assessment.isCaptchaChallengeRequired();
        if (!captchaRequired) {
            return ValidationResult.pass();
        }
        if (!StringUtils.hasText(captchaToken) || !StringUtils.hasText(captchaCode)) {
            return ValidationResult.fail("CAPTCHA_REQUIRED",
                    buildPayload(ip, username, failureCount, assessment, true, "连续失败次数过多，请先完成验证码校验"));
        }
        CaptchaState captchaState = captchaStateMap.get(captchaToken.trim());
        if (captchaState == null || captchaState.expireAt.isBefore(LocalDateTime.now())
                || !captchaState.key.equals(buildKey(ip, username))
                || !captchaState.code.equalsIgnoreCase(captchaCode.trim())) {
            // 验证码输入错误：递增失败计数，并重新评估风险
            if (failureState != null) {
                failureState.captchaFailCount++;
            } else {
                FailureState newState = getFailureState(ip, username, true);
                newState.captchaFailCount++;
            }
            // 重新评估（带上更新后的 captchaFailCount）
            int updatedCaptchaFail = failureState != null ? failureState.captchaFailCount : 1;
            DeviceRiskEngineService.DeviceRiskAssessment updatedAssessment = deviceRiskEngineService.assess(
                    ip, username, browserFingerprint, userAgent,
                    failureCount, captchaRefreshCount, updatedCaptchaFail
            );
            return ValidationResult.fail("CAPTCHA_INVALID",
                    buildPayload(ip, username, failureCount, updatedAssessment, true, "验证码错误或已过期，请重新输入"));
        }
        captchaStateMap.remove(captchaToken.trim());
        return ValidationResult.pass();
    }

    public FailureResult onLoginFailure(String ip,
                                        String username,
                                        String password,
                                        String browserFingerprint,
                                        String userAgent) {
        cleanupExpired();
        FailureState failureState = getFailureState(ip, username, true);
        failureState.failureCount++;
        failureState.lastFailureAt = LocalDateTime.now();

        deviceRiskEngineService.onLoginFailure(ip, username, browserFingerprint, userAgent);
        DeviceRiskEngineService.DeviceRiskAssessment assessment = deviceRiskEngineService.assess(
                ip,
                username,
                browserFingerprint,
                userAgent,
                failureState.failureCount,
                failureState.captchaRefreshCount,
                failureState.captchaFailCount
        );

        boolean weakPassword = isWeakPassword(username, password);
        String attackType = failureState.failureCount >= CAPTCHA_THRESHOLD ? "BRUTE_FORCE_LOGIN" : "LOGIN_FAILURE";
        if (weakPassword) {
            attackType = "WEAK_PASSWORD_ATTACK";
        } else if (assessment.isBlocked()) {
            attackType = "HIGH_RISK_DEVICE_LOGIN";
        }

        CaptchaPayload captchaPayload = buildPayload(
                ip,
                username,
                failureState.failureCount,
                assessment,
                true,
                weakPassword ? "检测到弱口令尝试" : null
        );
        return new FailureResult(failureState.failureCount, weakPassword, attackType, captchaPayload, assessment);
    }

    public void onLoginSuccess(String ip,
                               String username,
                               String browserFingerprint,
                               String userAgent) {
        // 登录成功：清除所有失败状态（含验证码刷新/失败计数）
        failureStateMap.remove(buildKey(ip, username));
        deviceRiskEngineService.onLoginSuccess(ip, username, browserFingerprint, userAgent);
    }

    private FailureState getFailureState(String ip, String username, boolean create) {
        String key = buildKey(ip, username);
        FailureState state = failureStateMap.get(key);
        if (state == null && create) {
            state = new FailureState();
            state.failureCount = 0;
            state.lastFailureAt = LocalDateTime.now();
            failureStateMap.put(key, state);
        }
        return state;
    }

    private CaptchaPayload buildPayload(String ip,
                                        String username,
                                        int failureCount,
                                        DeviceRiskEngineService.DeviceRiskAssessment assessment,
                                        boolean issueCaptchaWhenNeeded,
                                        String challengeMessage) {
        boolean captchaRequired = failureCount >= CAPTCHA_THRESHOLD || assessment.isCaptchaChallengeRequired();
        CaptchaPayload payload = new CaptchaPayload();
        payload.setCaptchaRequired(captchaRequired);
        payload.setFailureCount(failureCount);
        if (captchaRequired && issueCaptchaWhenNeeded) {
            CaptchaPayload captchaOnlyPayload = issueCaptcha(ip, username, failureCount, assessment.getRiskScore());
            payload.setCaptchaToken(captchaOnlyPayload.getCaptchaToken());
            payload.setCaptchaSvg(captchaOnlyPayload.getCaptchaSvg());
        }
        payload.setDeviceRiskEnabled(assessment.isEnabled());
        payload.setDeviceRiskScore(assessment.getRiskScore());
        payload.setRiskLevel(assessment.getRiskLevel());
        payload.setRiskReasons(new ArrayList<>(assessment.getRiskReasons()));
        payload.setBlockLogin(assessment.isBlocked());
        if (!StringUtils.hasText(challengeMessage) && assessment.isBlocked()) {
            challengeMessage = "当前设备风险过高，已拒绝本次登录";
        }
        if (!StringUtils.hasText(challengeMessage) && captchaRequired) {
            challengeMessage = "当前登录行为风险较高，请完成验证码校验";
        }
        payload.setChallengeMessage(challengeMessage);
        payload.setTrustedBrowser(assessment.isTrustedBrowser());
        return payload;
    }

    private CaptchaPayload issueCaptcha(String ip, String username, int failureCount, int riskScore) {
        String key = buildKey(ip, username);
        String code = randomCode(4);
        String token = UUID.randomUUID().toString().replace("-", "");
        CaptchaState captchaState = new CaptchaState();
        captchaState.code = code;
        captchaState.key = key;
        captchaState.expireAt = LocalDateTime.now().plusSeconds(CAPTCHA_EXPIRE_SECONDS);
        captchaStateMap.put(token, captchaState);

        CaptchaPayload payload = new CaptchaPayload();
        payload.setCaptchaRequired(true);
        payload.setCaptchaToken(token);
        payload.setCaptchaSvg(buildSvg(code, riskScore));
        payload.setFailureCount(failureCount);
        return payload;
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        captchaStateMap.entrySet().removeIf(entry -> entry.getValue().expireAt.isBefore(now));
        failureStateMap.entrySet().removeIf(entry -> entry.getValue().lastFailureAt.plusSeconds(FAILURE_EXPIRE_SECONDS).isBefore(now));
    }

    private String buildKey(String ip, String username) {
        return (ip == null ? "-" : ip.trim()) + "#" + (username == null ? "-" : username.trim().toLowerCase());
    }

    private boolean isWeakPassword(String username, String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        String normalized = password.trim().toLowerCase();
        for (String weak : WEAK_PASSWORDS) {
            if (weak.equals(normalized)) {
                return true;
            }
        }
        return StringUtils.hasText(username) && normalized.equals(username.trim().toLowerCase());
    }

    private String randomCode(int length) {
        // 包含视觉相近字符（0/O, 1/I/L, 5/S）增加人眼识别难度
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return builder.toString();
    }

    // ====== 验证码字符集：包含全部视觉相近字符对 ======
    private static final String CAPTCHA_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // ====== 用于生成假字符诱饵的完整字符集 ======
    private static final String DECOY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz";

    // ====== 验证码文字色板——深色且多彩，干扰线和文字共用，使干扰线与字形无法通过颜色区分 ======
    private static final String[] INK_COLORS = {
            "#0d2a6b","#0a3d0f","#6b0a0a","#4a0069","#005260",
            "#1a2060","#7a3000","#003850","#3a0050","#004020",
            "#2e1a47","#6a1b0d","#003d26","#46002e","#1c3b00"
    };

    // ====== 字体池 ======
    private static final String[] FONT_POOL = {
            "Impact,sans-serif",
            "Arial Black,sans-serif",
            "Georgia,serif",
            "Courier New,monospace",
            "Verdana,sans-serif",
            "Times New Roman,serif",
            "Trebuchet MS,sans-serif"
    };

    /** 线性插值辅助：在 [low, high] 之间按难度因子 d (0~1) 插值 */
    private static int lerp(double d, int low, int high) {
        return (int) Math.round(low + (high - low) * d);
    }
    private static double lerp(double d, double low, double high) {
        return low + (high - low) * d;
    }

    /**
     * 生成【动态难度】验证码 SVG（高基线版）。
     *
     * 难度由 riskScore (0~100) 驱动，归一化为 d = riskScore / 100.0。
     * 基线已大幅提高：d=0 时就已包含同色干扰线、诱饵字符、桥线、窄间距，
     * 人类需要仔细辨认才能看清；d=1 时完全无法机器识别。
     *
     * 所有层从 d=0 即激活，数值范围：
     *  ① 每字符波形扭曲（位移 6~19px）          ← 基线已有明显扭曲
     *  ② 全局二次扭曲（位移 3~8px）              ← 始终开启
     *  ③ 字符旋转（±15° ~ ±35°）                ← 基线已有明显旋转
     *  ④ 背景噪点（80~220 个，始终同色系）        ← 基线已大量
     *  ⑤ 背景干扰曲线（8~18 条，始终同色）        ← 基线已多条
     *  ⑥ 假字符诱饵（每位置 1~2 个，0.20~0.55 不透明度）← 始终有诱饵
     *  ⑦ 前景穿透干扰线（4~15 条）               ← 基线已有
     *  ⑧ 字符断裂镂空（0.10~0.50 概率）          ← 基线小概率
     *  ⑨ 连接桥线（始终启用）                     ← 始终有
     *  ⑩ 网格雾层（d>0.3 启用）                  ← 提前启用
     *  ⑪ 顶层散点（30~80 个，同色）              ← 基线已多
     *  ⑫ 短划痕碎片（8~25 条）                   ← 基线已有
     *  ⑬ 深色色块（10~30 个）                    ← 始终有
     *  ⑭ 额外随机诱饵（d>0.2 出现 2~6 个）       ← 很快出现
     */
    private String buildSvg(String code, int riskScore) {
        final double d = Math.max(0.0, Math.min(1.0, riskScore / 100.0));
        final int W = 220;
        final int H = 70;
        StringBuilder sb = new StringBuilder(8192);

        sb.append(String.format(
                "<svg xmlns='http://www.w3.org/2000/svg' width='%d' height='%d'>", W, H));

        char[] chars = code.toCharArray();
        int len = chars.length;

        // ─────────── defs: 滤镜（始终生成） ───────────
        sb.append("<defs>");

        // 每字符独立波形滤镜：基线位移 6px，满分 19px
        for (int i = 0; i < len; i++) {
            int seed = secureRandom.nextInt(99999) + 1;
            double bfx = lerp(d, 0.018, 0.060) + secureRandom.nextDouble() * 0.010;
            double bfy = lerp(d, 0.022, 0.070) + secureRandom.nextDouble() * 0.010;
            int numOctaves = d < 0.4 ? 3 : (d < 0.7 ? 4 : 5);
            int scale = lerp(d, 6, 19);
            sb.append(String.format(
                    "<filter id='c%d' x='-40%%' y='-40%%' width='180%%' height='180%%'>" +
                    "<feTurbulence type='fractalNoise' baseFrequency='%.4f %.4f' " +
                    "numOctaves='%d' seed='%d' result='n'/>" +
                    "<feDisplacementMap in='SourceGraphic' in2='n' scale='%d' " +
                    "xChannelSelector='R' yChannelSelector='G'/>" +
                    "</filter>",
                    i, bfx, bfy, numOctaves, seed, scale));
        }

        // 全局二次扭曲滤镜（始终启用，基线位移 3px）
        int globalSeed = secureRandom.nextInt(99999) + 1;
        int globalScale = lerp(d, 3, 8);
        sb.append(String.format(
                "<filter id='gw' x='-20%%' y='-20%%' width='140%%' height='140%%'>" +
                "<feTurbulence type='turbulence' baseFrequency='0.012 0.018' " +
                "numOctaves='3' seed='%d' result='gn'/>" +
                "<feDisplacementMap in='SourceGraphic' in2='gn' scale='%d' " +
                "xChannelSelector='G' yChannelSelector='R'/>" +
                "</filter>", globalSeed, globalScale));

        // 高斯模糊滤镜（始终可用，用于诱饵）
        sb.append("<filter id='blur1'><feGaussianBlur stdDeviation='0.8'/></filter>");
        sb.append("<filter id='blur2'><feGaussianBlur stdDeviation='1.2'/></filter>");
        sb.append("</defs>");

        // ─── 全局扭曲包裹层（始终启用） ───
        sb.append("<g filter='url(#gw)'>");

        // ── Layer 1: 背景 ──
        sb.append(String.format(
                "<rect width='%d' height='%d' rx='6' fill='#e8ecf2'/>", W, H));

        // 深色色块（始终存在，10~30 个）
        int numColorBlocks = lerp(d, 10, 30);
        for (int i = 0; i < numColorBlocks; i++) {
            int bx = secureRandom.nextInt(W);
            int by = secureRandom.nextInt(H);
            int bw = 3 + secureRandom.nextInt(12);
            int bh = 2 + secureRandom.nextInt(8);
            String bc = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            double bo = lerp(d, 0.03, 0.08) + secureRandom.nextDouble() * 0.04;
            sb.append(String.format(
                    "<rect x='%d' y='%d' width='%d' height='%d' fill='%s' opacity='%.2f' rx='1'/>",
                    bx, by, bw, bh, bc, bo));
        }

        // ── Layer 2: 背景噪点（80~220 个，始终使用与文字同色系） ──
        int numBgDots = lerp(d, 80, 220);
        for (int i = 0; i < numBgDots; i++) {
            int x = secureRandom.nextInt(W);
            int y = secureRandom.nextInt(H);
            int r = d > 0.3 ? secureRandom.nextInt(2) + 1 : 1;
            String c = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            double op = lerp(d, 0.05, 0.12) + secureRandom.nextDouble() * 0.06;
            sb.append(String.format(
                    "<circle cx='%d' cy='%d' r='%d' fill='%s' opacity='%.2f'/>", x, y, r, c, op));
        }

        // ── Layer 3: 背景干扰曲线（8~18 条，始终同色） ──
        int numBgCurves = lerp(d, 8, 18);
        for (int i = 0; i < numBgCurves; i++) {
            sb.append(buildNoiseCurve(W, H, true, d));
        }

        // ── 字符布局参数：始终窄间距 ──
        int padding = lerp(d, 30, 20);
        int charZone = (W - padding) / len;
        int startX = padding / 2 + charZone / 2;

        // ── Layer 4: 假字符诱饵（始终存在） ──
        int decoysPerChar = d > 0.4 ? 2 : 1;
        for (int i = 0; i < len; i++) {
            int cx = startX + i * charZone;
            for (int dd = 0; dd < decoysPerChar; dd++) {
                char decoyChar = DECOY_CHARS.charAt(secureRandom.nextInt(DECOY_CHARS.length()));
                while (Character.toUpperCase(decoyChar) == chars[i]) {
                    decoyChar = DECOY_CHARS.charAt(secureRandom.nextInt(DECOY_CHARS.length()));
                }
                int dx = cx + secureRandom.nextInt(20) - 10;
                int dy = 42 + secureRandom.nextInt(16) - 8;
                int da = secureRandom.nextInt(70) - 35;
                int dfs = 14 + secureRandom.nextInt(10);
                String dc = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
                double dOp = lerp(d, 0.20, 0.55);
                String dFont = FONT_POOL[secureRandom.nextInt(FONT_POOL.length)];
                String filterAttr = dd == 0 ? "filter='url(#blur1)'" : "";
                sb.append(String.format(
                        "<text x='%d' y='%d' font-size='%d' font-family='%s' font-weight='bold' " +
                        "fill='%s' opacity='%.2f' %s " +
                        "transform='rotate(%d,%d,%d)'>%c</text>",
                        dx, dy, dfs, dFont, dc, dOp, filterAttr,
                        da, dx, dy, decoyChar));
            }
        }
        // 额外随机位置诱饵（d > 0.2 出现 2~6 个）
        int extraDecoys = d > 0.2 ? lerp(d, 2, 6) : 0;
        for (int i = 0; i < extraDecoys; i++) {
            char ec = DECOY_CHARS.charAt(secureRandom.nextInt(DECOY_CHARS.length()));
            int ex = secureRandom.nextInt(W);
            int ey = 20 + secureRandom.nextInt(40);
            int ea = secureRandom.nextInt(90) - 45;
            int efs = 14 + secureRandom.nextInt(12);
            String eColor = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            double eOp = lerp(d, 0.15, 0.35);
            sb.append(String.format(
                    "<text x='%d' y='%d' font-size='%d' font-family='%s' " +
                    "fill='%s' opacity='%.2f' filter='url(#blur2)' " +
                    "transform='rotate(%d,%d,%d)'>%c</text>",
                    ex, ey, efs, FONT_POOL[secureRandom.nextInt(FONT_POOL.length)],
                    eColor, eOp, ea, ex, ey, ec));
        }

        // ── Layer 5: 真实字符渲染 ──
        int maxAngle = lerp(d, 15, 35);
        double hollowProb = lerp(d, 0.10, 0.50);
        for (int i = 0; i < len; i++) {
            int cx    = startX + i * charZone;
            int cy    = 42 + secureRandom.nextInt(lerp(d, 8, 18)) - lerp(d, 4, 9);
            int angle = secureRandom.nextInt(maxAngle * 2 + 1) - maxAngle;
            int fs    = lerp(d, 26, 24) + secureRandom.nextInt(lerp(d, 4, 10));
            String fill   = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            String stroke = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            String font   = FONT_POOL[secureRandom.nextInt(FONT_POOL.length)];
            int jitter = secureRandom.nextInt(lerp(d, 4, 9)) - lerp(d, 2, 4);

            boolean doHollow = secureRandom.nextDouble() < hollowProb;
            if (doHollow) {
                sb.append(String.format(
                        "<text x='%d' y='%d' font-size='%d' font-family='%s' font-weight='bold' " +
                        "fill='none' stroke='%s' stroke-width='%.1f' " +
                        "transform='rotate(%d,%d,%d)' filter='url(#c%d)'>%c</text>",
                        cx + jitter, cy, fs, font,
                        fill, 1.2 + secureRandom.nextDouble() * 0.8,
                        angle, cx + jitter, cy, i, chars[i]));
                String fill2 = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
                sb.append(String.format(
                        "<text x='%d' y='%d' font-size='%d' font-family='%s' font-weight='bold' " +
                        "fill='%s' opacity='0.50' " +
                        "transform='rotate(%d,%d,%d)' filter='url(#c%d)'>%c</text>",
                        cx + jitter + 1, cy + 1, fs, font,
                        fill2, angle, cx + jitter + 1, cy + 1, i, chars[i]));
            } else {
                sb.append(String.format(
                        "<text x='%d' y='%d' font-size='%d' font-family='%s' font-weight='bold' " +
                        "fill='%s' stroke='%s' stroke-width='0.7' " +
                        "transform='rotate(%d,%d,%d)' filter='url(#c%d)'>%c</text>",
                        cx + jitter, cy, fs, font,
                        fill, stroke,
                        angle, cx + jitter, cy, i, chars[i]));
            }
        }

        // ── Layer 6: 连接桥线段（始终启用） ──
        for (int i = 0; i < len - 1; i++) {
            int x1 = startX + i * charZone + secureRandom.nextInt(10);
            int x2 = startX + (i + 1) * charZone - secureRandom.nextInt(10);
            int y1 = 30 + secureRandom.nextInt(20);
            int y2 = 30 + secureRandom.nextInt(20);
            String lc = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            double lw = lerp(d, 0.6, 1.5) + secureRandom.nextDouble() * 0.5;
            double lOp = lerp(d, 0.35, 0.65);
            sb.append(String.format(
                    "<line x1='%d' y1='%d' x2='%d' y2='%d' stroke='%s' stroke-width='%.1f' opacity='%.2f'/>",
                    x1, y1, x2, y2, lc, lw, lOp));
        }

        // ── Layer 7: 前景穿透干扰线（4~15 条） ──
        int numFgCurves = lerp(d, 4, 15);
        for (int i = 0; i < numFgCurves; i++) {
            sb.append(buildNoiseCurve(W, H, false, d));
        }

        // ── Layer 8: 网格雾层（d > 0.3 启用） ──
        if (d > 0.3) {
            int gridStep = lerp(d, 12, 6);
            for (int gy = 5; gy < H; gy += gridStep + secureRandom.nextInt(5)) {
                String gc = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
                double gOp = lerp(d, 0.02, 0.06) + secureRandom.nextDouble() * 0.03;
                sb.append(String.format(
                        "<line x1='0' y1='%d' x2='%d' y2='%d' stroke='%s' stroke-width='0.5' opacity='%.2f'/>",
                        gy, W, gy + secureRandom.nextInt(5) - 2, gc, gOp));
            }
            for (int gx = 5; gx < W; gx += gridStep + secureRandom.nextInt(6)) {
                String gc = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
                double gOp = lerp(d, 0.02, 0.05) + secureRandom.nextDouble() * 0.02;
                sb.append(String.format(
                        "<line x1='%d' y1='0' x2='%d' y2='%d' stroke='%s' stroke-width='0.5' opacity='%.2f'/>",
                        gx, gx + secureRandom.nextInt(4) - 2, H, gc, gOp));
            }
        }

        // ── Layer 9: 顶层散点（30~80 个，始终同色） ──
        int numTopDots = lerp(d, 30, 80);
        for (int i = 0; i < numTopDots; i++) {
            int x = secureRandom.nextInt(W);
            int y = secureRandom.nextInt(H);
            String c = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            double op = lerp(d, 0.12, 0.35) + secureRandom.nextDouble() * 0.15;
            int r = d > 0.3 ? secureRandom.nextInt(2) + 1 : 1;
            sb.append(String.format(
                    "<circle cx='%d' cy='%d' r='%d' fill='%s' opacity='%.2f'/>", x, y, r, c, op));
        }

        // ── Layer 10: 短划痕（8~25 条，始终存在） ──
        int numScratches = lerp(d, 8, 25);
        for (int i = 0; i < numScratches; i++) {
            int sx = secureRandom.nextInt(W);
            int sy = secureRandom.nextInt(H);
            int ex = sx + secureRandom.nextInt(16) - 8;
            int ey = sy + secureRandom.nextInt(10) - 5;
            String sc = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];
            double sOp = lerp(d, 0.10, 0.30) + secureRandom.nextDouble() * 0.10;
            double sw = lerp(d, 0.4, 1.0) + secureRandom.nextDouble() * 0.5;
            sb.append(String.format(
                    "<line x1='%d' y1='%d' x2='%d' y2='%d' stroke='%s' stroke-width='%.1f' " +
                    "opacity='%.2f' stroke-linecap='round'/>",
                    sx, sy, ex, ey, sc, sw, sOp));
        }

        sb.append("</g>"); // 关闭全局扭曲 group
        sb.append("</svg>");
        return sb.toString();
    }

    /**
     * 生成一条随机贝塞尔干扰曲线（始终使用与文字同色）。
     */
    private String buildNoiseCurve(int W, int H, boolean isBehind, double d) {
        int x1 = secureRandom.nextInt(W);
        int y1 = 10 + secureRandom.nextInt(H - 20);
        int x2 = secureRandom.nextInt(W);
        int y2 = 10 + secureRandom.nextInt(H - 20);
        int cx1 = secureRandom.nextInt(W);
        int cy1 = secureRandom.nextInt(H);
        int cx2 = secureRandom.nextInt(W);
        int cy2 = secureRandom.nextInt(H);

        double sw, opacity;
        if (isBehind) {
            sw      = lerp(d, 0.6, 1.5) + secureRandom.nextDouble() * 0.5;
            opacity = lerp(d, 0.12, 0.30) + secureRandom.nextDouble() * 0.15;
        } else {
            sw      = lerp(d, 0.8, 2.0) + secureRandom.nextDouble() * 0.5;
            opacity = lerp(d, 0.25, 0.55) + secureRandom.nextDouble() * 0.20;
        }

        // 始终使用与文字完全相同的颜色池
        String color = INK_COLORS[secureRandom.nextInt(INK_COLORS.length)];

        return String.format(
                "<path d='M%d,%d C%d,%d %d,%d %d,%d' stroke='%s' stroke-width='%.1f' " +
                "fill='none' opacity='%.2f' stroke-linecap='round'/>",
                x1, y1, cx1, cy1, cx2, cy2, x2, y2, color, sw, opacity);
    }

    private static class FailureState {
        private int failureCount;
        private int captchaRefreshCount;
        private int captchaFailCount;
        private LocalDateTime lastFailureAt;
    }

    private static class CaptchaState {
        private String key;
        private String code;
        private LocalDateTime expireAt;
    }

    public static class CaptchaPayload {
        private boolean captchaRequired;
        private String captchaToken;
        private String captchaSvg;
        private int failureCount;
        private boolean deviceRiskEnabled;
        private int deviceRiskScore;
        private String riskLevel;
        private List<String> riskReasons = new ArrayList<>();
        private boolean blockLogin;
        private String challengeMessage;
        private boolean trustedBrowser;

        public boolean isCaptchaRequired() {
            return captchaRequired;
        }

        public void setCaptchaRequired(boolean captchaRequired) {
            this.captchaRequired = captchaRequired;
        }

        public String getCaptchaToken() {
            return captchaToken;
        }

        public void setCaptchaToken(String captchaToken) {
            this.captchaToken = captchaToken;
        }

        public String getCaptchaSvg() {
            return captchaSvg;
        }

        public void setCaptchaSvg(String captchaSvg) {
            this.captchaSvg = captchaSvg;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public void setFailureCount(int failureCount) {
            this.failureCount = failureCount;
        }

        public boolean isDeviceRiskEnabled() {
            return deviceRiskEnabled;
        }

        public void setDeviceRiskEnabled(boolean deviceRiskEnabled) {
            this.deviceRiskEnabled = deviceRiskEnabled;
        }

        public int getDeviceRiskScore() {
            return deviceRiskScore;
        }

        public void setDeviceRiskScore(int deviceRiskScore) {
            this.deviceRiskScore = deviceRiskScore;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public List<String> getRiskReasons() {
            return riskReasons;
        }

        public void setRiskReasons(List<String> riskReasons) {
            this.riskReasons = riskReasons;
        }

        public boolean isBlockLogin() {
            return blockLogin;
        }

        public void setBlockLogin(boolean blockLogin) {
            this.blockLogin = blockLogin;
        }

        public String getChallengeMessage() {
            return challengeMessage;
        }

        public void setChallengeMessage(String challengeMessage) {
            this.challengeMessage = challengeMessage;
        }

        public boolean isTrustedBrowser() {
            return trustedBrowser;
        }

        public void setTrustedBrowser(boolean trustedBrowser) {
            this.trustedBrowser = trustedBrowser;
        }
    }

    public static class ValidationResult {
        private final boolean pass;
        private final String reason;
        private final CaptchaPayload captchaPayload;

        private ValidationResult(boolean pass, String reason, CaptchaPayload captchaPayload) {
            this.pass = pass;
            this.reason = reason;
            this.captchaPayload = captchaPayload;
        }

        public static ValidationResult pass() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult fail(String reason, CaptchaPayload captchaPayload) {
            return new ValidationResult(false, reason, captchaPayload);
        }

        public boolean isPass() {
            return pass;
        }

        public String getReason() {
            return reason;
        }

        public CaptchaPayload getCaptchaPayload() {
            return captchaPayload;
        }
    }

    public static class FailureResult {
        private final int failureCount;
        private final boolean weakPassword;
        private final String attackType;
        private final CaptchaPayload captchaPayload;
        private final DeviceRiskEngineService.DeviceRiskAssessment assessment;

        public FailureResult(int failureCount,
                             boolean weakPassword,
                             String attackType,
                             CaptchaPayload captchaPayload,
                             DeviceRiskEngineService.DeviceRiskAssessment assessment) {
            this.failureCount = failureCount;
            this.weakPassword = weakPassword;
            this.attackType = attackType;
            this.captchaPayload = captchaPayload;
            this.assessment = assessment;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public boolean isWeakPassword() {
            return weakPassword;
        }

        public String getAttackType() {
            return attackType;
        }

        public CaptchaPayload getCaptchaPayload() {
            return captchaPayload;
        }

        public DeviceRiskEngineService.DeviceRiskAssessment getAssessment() {
            return assessment;
        }
    }
}
