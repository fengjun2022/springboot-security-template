package com.ssy.controller;

import com.ssy.dto.UserEntity;
import com.ssy.entity.Result;
import com.ssy.service.impl.RbacIdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/auth")
public class AuthAccountController {

    @Autowired
    private RbacIdentityService rbacIdentityService;

    @PostMapping("/register")
    public Result<UserEntity> register(@RequestBody UserEntity user) {
        try {
            RbacIdentityService.SelfRegisterCommand cmd = new RbacIdentityService.SelfRegisterCommand();
            cmd.setUsername(user.getUsername());
            cmd.setPassword(user.getPassword());
            cmd.setRoleCodes(user.getRoles() == null ? null : new ArrayList<>(user.getRoles()));
            cmd.setStatus(0);
            return Result.success(rbacIdentityService.selfRegister(cmd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
