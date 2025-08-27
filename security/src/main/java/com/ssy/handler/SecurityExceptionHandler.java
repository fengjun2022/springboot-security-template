package com.ssy.handler;



import com.ssy.entity.Result;
import com.ssy.exception.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class SecurityExceptionHandler {
    private Logger logger = LoggerFactory.getLogger(getClass());



    /**
     * 处理空指针异常
     * @param e 异常
     * @return 处理结果
     */
    @ExceptionHandler(NullPointerException.class)
    public Result handlerNullPointerException(NullPointerException e) {
        logger.error(e.getMessage(), e);
        return Result.error("空指针异常");
    }


    @ExceptionHandler(BadSqlGrammarException.class)
    public Result handlerSqlException(BadSqlGrammarException e){

        logger.error(e.getMessage(), e);
        return Result.error("数据库查询异常,缺少查询关键词，请稍后重试");
    }






    /**
     * 处理自定义异常
     * @param e 异常
     * @return 处理结果
     */
    @ExceptionHandler(SecurityException.class)
    public Result handlerGlobalException(SecurityException e) {
        logger.error(e.getMessage(), e);
        return Result.error(e.getMessage(), e.getCode());
    }







}
