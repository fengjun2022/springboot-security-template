package com.ssy.service.impl;

import com.pojo.entity.UserEntity;
import com.ssy.mapper.UserMapper;
import com.ssy.service.UserService;
import com.ssy.utils.SecurePasswordEncryptorUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/5
 * @email 3278440884@qq.com
 */

@Service
public class UserServiceimpl implements UserService {

    @Autowired
    SecurePasswordEncryptorUtil securePasswordEncryptorUtil;
    @Autowired
    UserMapper userMapper;

    @Override
    public void register(UserEntity user) {
        CompletableFuture<String> stringCompletableFuture = securePasswordEncryptorUtil
                .encryptPassword(user.getPassword());
        String password = stringCompletableFuture.join();
        user.setPassword(password);
        // 暂时简化实现
        // userMapper.register(user);
    }

    @Override
    public void createUser(UserEntity user) {
        register(user);
    }

    @Override
    public List<UserEntity> getAllUsers() {
        // 暂时返回空列表
        return new java.util.ArrayList<>();
    }

    @Override
    public int getUserCount() {
        return 10; // 示例值
    }

    @Override
    public int getActiveUserCount() {
        return 8; // 示例值
    }

    @Override
    public void updateUserRoles(Long userId, List<String> roles) {
        // 简单实现
        // userMapper.updateRoles(userId, roles);
    }

    @Override
    public UserEntity getUserById(Long userId) {
        // 暂时返回null
        return null;
    }
}
