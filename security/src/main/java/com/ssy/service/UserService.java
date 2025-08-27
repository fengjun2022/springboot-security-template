/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/5
 * @email 3278440884@qq.com
 */

package com.ssy.service;

import com.pojo.entity.UserEntity;
import java.util.List;

/**
 * 用户服务接口
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
public interface UserService {

    /**
     * 用户注册
     */
    void register(UserEntity user);

    /**
     * 创建用户
     */
    void createUser(UserEntity user);

    /**
     * 获取所有用户
     */
    List<UserEntity> getAllUsers();

    /**
     * 获取用户总数
     */
    int getUserCount();

    /**
     * 获取活跃用户数
     */
    int getActiveUserCount();

    /**
     * 更新用户角色
     */
    void updateUserRoles(Long userId, List<String> roles);

    /**
     * 根据ID获取用户
     */
    UserEntity getUserById(Long userId);
}
