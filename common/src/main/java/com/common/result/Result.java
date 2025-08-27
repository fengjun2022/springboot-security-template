package com.common.result;

import com.common.constant.HttpMessage;
import com.common.constant.HttpStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T>
 */
@Data
public class Result<T> implements Serializable {

    private Integer code; //编码：200成功，400和其它数字为失败
    private String msg; //错误信息
    private T data; //数据

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = HttpStatus.OK;
        result.msg = HttpMessage.Success;
        return result;
    }

    public static <T> Result<T> success(String msg) {
        Result<T> result = new Result<T>();
        result.code = HttpStatus.OK;
        result.msg = msg;
        return result;
    }


    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = HttpStatus.OK;
        result.msg = HttpMessage.Success;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = HttpStatus.BAD_REQUEST;
        return result;
    }

    public static <T> Result<T> error(String msg,int code) {
        Result result = new Result();
        result.msg = msg;
        result.code = code;
        return result;
    }


}
