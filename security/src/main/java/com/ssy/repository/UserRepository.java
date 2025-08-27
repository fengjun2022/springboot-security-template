package com.ssy.repository;

import com.ssy.dto.UserEntity;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/3
 * @email 3278440884@qq.com
 */

public interface  UserRepository {
    UserEntity queryUser(String username);
}
