package com.ssy.annotation;

import java.lang.annotation.*;
/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/6
 * @email 3278440884@qq.com
 */


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AutoGenerateSnowflakeId {
    /**
     * 指定需要赋值的字段名称，默认为 "id"
     */
    String fieldName() default "id";
}