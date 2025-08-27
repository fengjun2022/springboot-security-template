package com.main.handler;



import com.common.exception.GlobalException;
import com.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
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
    public  Result handlerSqlException(BadSqlGrammarException e){

        logger.error(e.getMessage(), e);
        return Result.error("数据库查询异常,缺少查询关键词，请稍后重试");
    }


    /**
     * 处理自定义异常
     * @param e 异常
     * @return 处理结果
     */
    @ExceptionHandler(GlobalException.class)
    public Result handlerGlobalException(GlobalException e) {
        logger.error(e.getMessage(), e);
        return Result.error(e.getMessage(), e.getCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result handlerGlobalException(AccessDeniedException e) {
        throw e;
    }


    /**
     * 处理 Exception 异常
     * @param e 异常
     * @return 处理结果
     */
    @ExceptionHandler(Exception.class)
    public Result handlerException(Exception e) {
        logger.error(e.getMessage(), e);
        return Result.error(e.getMessage()!=null? e.getMessage() : "系统异常");
    }




}
