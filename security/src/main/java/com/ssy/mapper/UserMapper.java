package com.ssy.mapper;

import com.ssy.annotation.AutoGenerateSnowflakeId;
import com.ssy.dto.UserEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/5
 * @email 3278440884@qq.com
 */

@Mapper
public interface UserMapper {
    // 旧接口（保留，避免历史调用编译失败）
    @AutoGenerateSnowflakeId(fieldName = "userId")
    @Insert("INSERT INTO `user` (id, user_id, username, password, authorities) " +
            "VALUES (#{id}, #{userId}, #{username}, #{password}, #{authorities, typeHandler=com.ssy.handler.CollectionTypeHandler})")
    Integer register(UserEntity user);


    @Select("SELECT id, user_id, username, password, authorities, status FROM `user` WHERE username = #{username}")
    UserEntity queryUser(String username);

    @Select("SELECT id, user_id, username, status FROM `user` WHERE user_id = #{userId}")
    UserEntity userInfo(Long userId);

    // 新RBAC账号创建（仅负责账号基础信息，角色关系单独维护）
    @AutoGenerateSnowflakeId(fieldName = "userId")
    @Insert("INSERT INTO `user` (user_id, username, password, status) " +
            "VALUES (#{userId}, #{username}, #{password}, #{status})")
    int insertBaseUser(UserEntity user);

    @Select("SELECT COUNT(1) FROM `user` WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    @Select("SELECT id, user_id, username, password, status FROM `user` WHERE user_id = #{userId}")
    UserEntity selectByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id, username, password, status FROM `user` WHERE id = #{id}")
    UserEntity selectById(@Param("id") Long id);

    @Select("SELECT id, user_id, username, status FROM `user` ORDER BY id DESC LIMIT #{limit}")
    List<UserEntity> selectRecent(@Param("limit") int limit);

    @Update("<script>" +
            "UPDATE `user` <set>" +
            "<if test='username != null'>username = #{username},</if>" +
            "<if test='password != null'>password = #{password},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            // user表当前没有 update_time 字段，暂不写时间列
            "</set> WHERE user_id = #{userId}" +
            "</script>")
    int updateBaseUser(UserEntity user);

    @Update("UPDATE `user` SET status = #{status} WHERE user_id = #{userId}")
    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);
}
