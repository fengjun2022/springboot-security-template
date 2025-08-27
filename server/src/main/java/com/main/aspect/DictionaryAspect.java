package com.main.aspect;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class DictionaryAspect {

    @Pointcut("@annotation(com.main.annotation.Orientation)")
    public void dictionaryPointcut(){}





}

