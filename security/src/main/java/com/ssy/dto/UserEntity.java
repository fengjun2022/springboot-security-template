package com.ssy.dto;

import lombok.Data;
import java.util.Collection;

/**
 * 用户实体DTO（兼容旧代码）
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
public class UserEntity {
     private long id;
     private String username;
     private String password;
     private String email;
     private String phone;
     private int status;
     private String token;
     private long userId;
     private Collection<String> authorities;
}