package com.pojo.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/3
 * @email 3278440884@qq.com
 */
@Data
public class UserEntity implements Serializable {
     private long id;
     private String username;
     private String password;
     private String email;
     private String phone;
     private Integer status;
     private String token;
     private java.time.LocalDateTime createTime;
     private Collection<String> authorities;

     // 显式的setter方法（确保兼容性）
     public void setEmail(String email) {
          this.email = email;
     }

     public void setPhone(String phone) {
          this.phone = phone;
     }

     public void setStatus(Integer status) {
          this.status = status;
     }

     public void setStatus(int status) {
          this.status = status;
     }
}
