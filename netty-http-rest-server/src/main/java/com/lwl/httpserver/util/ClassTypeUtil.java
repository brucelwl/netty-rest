package com.lwl.httpserver.util;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * Created by bruce on 2023/2/11 00:16
 */
public class ClassTypeUtil {

    public static boolean isBasicType(Class<?> parameterType) {
        if (parameterType == String.class || parameterType.isPrimitive()) {
            return true;
        }
        Field type = ReflectionUtils.findField(parameterType, "TYPE");
        if (type == null) {
            return false;
        }
        ReflectionUtils.makeAccessible(type);
        Object field = ReflectionUtils.getField(type, parameterType);
        if (field instanceof Class) {
            return ((Class<?>) field).isPrimitive();
        }
        return false;
    }

}
