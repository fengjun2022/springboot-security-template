package com.ssy.interceptor;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/6
 * @email 3278440884@qq.com
 */

import com.ssy.annotation.AutoGenerateSnowflakeId;
import com.ssy.utils.IdGenerator;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Properties;

@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
@Component
public class SnowflakeIdInterceptor implements Interceptor {

    @Autowired
    private IdGenerator idGenerator;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 获取 MyBatis 的 MappedStatement 对象
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        // 拦截器拦截到 mapper 方法调用时，mappedStatement 的 id 包含了全限定方法名
        String mapperMethod = ms.getId();
        // 利用反射获取该方法的注解信息（这里需要解析全限定名来获取对应的类和方法）
        try {
            int lastDot = mapperMethod.lastIndexOf(".");
            String className = mapperMethod.substring(0, lastDot);
            String methodName = mapperMethod.substring(lastDot + 1);
            Class<?> mapperClass = Class.forName(className);
            Method[] methods = mapperClass.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    // 如果找到方法且有我们的自定义注解
                    AutoGenerateSnowflakeId annotation = method.getAnnotation(AutoGenerateSnowflakeId.class);
                    if (annotation != null) {
                        // 根据注解指定的字段名称
                        String fieldName = annotation.fieldName();
                        Object parameter = invocation.getArgs()[1];
                        // 支持单个对象和集合的情况
                        if (parameter instanceof Collection) {
                            Collection<?> collection = (Collection<?>) parameter;
                            for (Object obj : collection) {
                                setIdIfNull(obj, fieldName);
                            }
                        } else {
                            setIdIfNull(parameter, fieldName);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // 出现异常时，可以记录日志，但不阻断业务流程
            e.printStackTrace();
        }
        return invocation.proceed();
    }

    private void setIdIfNull(Object obj, String fieldName) {
        if (obj == null) {
            return;
        }
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            // 增加对 0 的判断，适用于数值类型
            if (value == null
                    || (value instanceof String && ((String) value).trim().isEmpty())
                    || (value instanceof Number && ((Number) value).longValue() == 0L)) {
                long generatedId = idGenerator.nextId();
                // 如果字段类型是 long 或 Long，则直接赋值
                if (field.getType().equals(Long.class) || field.getType().equals(long.class)) {
                    field.set(obj, generatedId);
                } else {
                    // 如果字段类型是其他类型，比如 String，可根据需求转换
                    field.set(obj, String.valueOf(generatedId));
                }
            }
        } catch (NoSuchFieldException e) {
            // 如果目标对象中不存在该字段，可选择记录日志
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可通过配置文件传入属性
    }
}