package com.lwl.httpserver.mvc;


import com.lwl.httpserver.mvc.annotation.Rest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by bruce on 2023/2/10 00:31
 */
public class DefaultRestAnnotationScanner implements RestAnnotationScanner {

    private String[] basePackages;

    public DefaultRestAnnotationScanner(String... basePackages) {
        this.basePackages = basePackages;
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<Rest> rest) {
        ClassScanner classScanner = new ClassScanner();

        classScanner.addIncludeAnnotation(Rest.class);
        Set<Class<?>> restClasses = classScanner.scan(basePackages);

        HashMap<String, Object> beans = new HashMap<>();
        try {
            for (Class<?> restClass : restClasses) {
                Object obj = restClass.getDeclaredConstructor().newInstance();
                beans.put(restClass.getSimpleName(), obj);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return beans;
    }

}
