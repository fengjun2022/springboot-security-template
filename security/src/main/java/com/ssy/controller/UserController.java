package com.ssy.controller;

import com.ssy.dto.UserEntity;
import com.ssy.entity.Result;
import com.ssy.repository.UserRepository;
import com.ssy.service.UserService;
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
    UserService userService;
    @Autowired

    UserRepository userRepository;
    /**
     * 用户注册接口
     * 
     * @param user
     * @return
     */
    @PostMapping("/register")
    public Result<UserEntity> register(@RequestBody UserEntity user) {
        userService.register(user);

        return Result.success("注册成功");
    }


    @GetMapping("/info")
    public Result<UserEntity> userInfo(@RequestParam("userId") Long userId){

        UserEntity  info =userService.userInfo(userId);

        return Result.success(info);
    }


}
