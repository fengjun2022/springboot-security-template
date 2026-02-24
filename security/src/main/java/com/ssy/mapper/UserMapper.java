package com.ssy.mapper;

import com.ssy.annotation.AutoGenerateSnowflakeId;
import com.ssy.dto.UserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
    // 在 #{authorities} 中指定自定义 TypeHandler，告诉 MyBatis 如何转换
    @AutoGenerateSnowflakeId(fieldName = "userId")
    @Insert("INSERT INTO user (id, user_id, username, password, authorities) " +
            "VALUES (#{id}, #{userId}, #{username}, #{password}, #{authorities, typeHandler=com.ssy.handler.CollectionTypeHandler})")
    Integer register(UserEntity user);


    @Select("select id, user_id,username, password, authorities,status from user where username = #{username}")
    UserEntity queryUser(String username);

    @Select("select id, user_id,username,status from user where user_id = #{userId}")
    UserEntity userInfo(Long userId);
}
