package com.lwl.httpserver.mvc.extension;

import com.lwl.httpserver.mvc.annotation.Rest;
import com.lwl.httpserver.mvc.extension.RestAnnotationScanner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * Created by bruce on 2023/2/8 02:32
 */
public class SpringRestAnnotationScanner implements RestAnnotationScanner {

    private ConfigurableApplicationContext applicationContext;

    public SpringRestAnnotationScanner(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Map<String, Object> getBeansWithAnnotation(Class<Rest> rest) {
        return applicationContext.getBeansWithAnnotation(rest);
    }


}
