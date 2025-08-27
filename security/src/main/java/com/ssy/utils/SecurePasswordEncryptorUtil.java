package com.ssy.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2024/4/28
 * @email 3278440884@qq.com
 */
@Component
public class SecurePasswordEncryptorUtil {
    private final PasswordEncoder passwordEncoder;

    @Qualifier("taskExecutor")
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;


    public SecurePasswordEncryptorUtil(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 使用指定算法加密密码。
     *
     * @param input 需要加密的原始密码
     * @param encoderId 加密算法的ID（如 bcrypt, pbkdf2）
     * @return 加密后的密码
     */
    @Async
    public CompletableFuture<String> encryptPassword(String input, String encoderId) {
        // 根据 encoderId 来动态选择密码编码器
        return CompletableFuture.supplyAsync(() -> {
            String prefix = "{" + encoderId + "}";
            return prefix + passwordEncoder.encode(prefix + input);
        }, taskExecutor);
    }

    @Async
    public CompletableFuture<String> encryptPassword(String input) {
        // 创建并配置线程池
        // 使用指定的线程池来异步执行密码加密
        return CompletableFuture.supplyAsync(() -> passwordEncoder.encode(input), taskExecutor);
    }

    /**
     * 检查提供的原始密码和加密后的密码是否匹配。
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 如果两者匹配返回 true，否则返回 false
     */
    @Async
    public CompletableFuture<Boolean> matches(String rawPassword, String encodedPassword) {
        return CompletableFuture.supplyAsync(() -> passwordEncoder.matches(rawPassword, encodedPassword), taskExecutor);
    }


}
