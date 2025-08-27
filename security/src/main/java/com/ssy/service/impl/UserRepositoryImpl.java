package com.ssy.service.impl;

import com.ssy.dto.UserEntity;
import com.ssy.mapper.UserMapper;
import com.ssy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/3
 * @email 3278440884@qq.com
 */
@Service
public class UserRepositoryImpl implements UserRepository {
    @Autowired
    UserMapper userMapper;
    @Override
    public UserEntity queryUser(String username) {
        UserEntity userEntity = userMapper.queryUser(username);

        return userEntity;
    }
}
