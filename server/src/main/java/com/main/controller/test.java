package com.main.controller;

import com.ssy.entity.Result;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/6
 * @email 3278440884@qq.com
 */

@RestController
@RequestMapping("/test")
public class test {
    @ApiOperation("测试A")
    @GetMapping("/a")
    public Result<String> test (){
        return Result.success("成功");
    }


    @GetMapping("/b")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> test1 (){
        return Result.success("权限接口测试成功");
    }
    @GetMapping("/c")
    @PreAuthorize("hasRole('USER')")
    public Result<String> test2 (){
        return Result.success("权限接口测试成功");
    }
}
