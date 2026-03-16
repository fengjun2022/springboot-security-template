package com.ssy.tools;

import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 本地生成初始用户密码哈希的小工具（放在 test 目录，便于 IDE 直接运行）。
 *
 * 用法：
 * 1. 直接运行 main（默认密码 ChangeMe123!）
 * 2. 或者传参：java ... PasswordHashGeneratorTest MyStrongPass123
 *
 * 输出形如：{bcrypt}$2a$...
 * 可直接写入 security.sql / 数据库 user.password 列。
 */
public class PasswordHashGeneratorTest {

    public static void main(String[] args) {
        String rawPassword = (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty())
                ? args[0].trim()
                : "ChangeMe123!";
        encodeAndPrint(rawPassword);
    }

    public static String encodeAndPrint(String rawPassword) {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String encoded = passwordEncoder.encode(rawPassword);
        long userId = nextSnowflakeUserId();
        System.out.println("raw      = " + rawPassword);
        System.out.println("encoded  = " + encoded);
        System.out.println("user_id  = " + userId + "  (snowflake)");
        return encoded;
    }

    private static long nextSnowflakeUserId() {
        // 与项目默认 workerId=1 保持一致，方便生成初始化数据
        YitIdHelper.setIdGenerator(new IdGeneratorOptions((short) 1));
        return YitIdHelper.nextId();
    }
}
