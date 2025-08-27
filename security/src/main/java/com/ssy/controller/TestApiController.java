package com.ssy.controller;

import com.common.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

/**
 * 测试API控制器
 * 用于测试API接口扫描功能
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Api(tags = "测试接口")
@RestController
@RequestMapping("/api/test")
public class TestApiController {

    @ApiOperation("测试GET接口")
    @GetMapping("/example")
    public Result<String> testGet() {
        return Result.success("这是一个测试GET接口");
    }

    @ApiOperation("测试POST接口")
    @PostMapping("/example")
    public Result<String> testPost(@RequestBody String data) {
        return Result.success("接收到数据: " + data);
    }

    @ApiOperation("测试带参数的接口")
    @GetMapping("/param/{id}")
    public Result<String> testWithParam(@PathVariable Long id) {
        return Result.success("参数ID: " + id);
    }

    @ApiOperation("测试查询参数接口")
    @GetMapping("/query")
    public Result<String> testQuery(@RequestParam String name, @RequestParam(required = false) Integer age) {
        return Result.success("姓名: " + name + ", 年龄: " + age);
    }
}

/**
 * 另一个测试控制器（无根路径）
 */
@Api(tags = "无根路径测试")
@RestController
class NoBasepathController {

    @ApiOperation("无根路径的接口")
    @GetMapping("/no-basepath")
    public Result<String> noBasepath() {
        return Result.success("无根路径的测试接口");
    }
}
