package com.ssy.controller;

import com.ssy.dto.UserEntity;
import com.ssy.entity.Result;
import com.ssy.service.impl.RbacIdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/5
 * @email 3278440884@qq.com
 */

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    RbacIdentityService rbacIdentityService;
    /**
     * 用户注册接口
     * 
     * @param user
     * @return
     */
    @PostMapping("/register")
    public Result<UserEntity> register(@RequestBody UserEntity user) {
        try {
            RbacIdentityService.SelfRegisterCommand cmd = new RbacIdentityService.SelfRegisterCommand();
            cmd.setUsername(user.getUsername());
            cmd.setPassword(user.getPassword());
            cmd.setRoleCodes(user.getRoles() == null ? null : new java.util.ArrayList<>(user.getRoles()));
            cmd.setStatus(0);
            UserEntity created = rbacIdentityService.selfRegister(cmd);
            return Result.success(created);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }


    @GetMapping("/info")
    public Result<UserEntity> userInfo(@RequestParam("userId") Long userId){
        try {
            UserEntity info = rbacIdentityService.getUserByUserId(userId);
            if (info == null) {
                return Result.error("用户不存在");
            }
            return Result.success(rbacIdentityService.sanitizeUser(info));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }


}
