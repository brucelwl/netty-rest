package com.lwl.init;

import com.lwl.mvc.annotation.Rest;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * Created by bruce on 2023/2/8 02:32
 */
public class SpringRestBeanScanStrategy implements RestBeanScanStrategy {

    private ConfigurableApplicationContext applicationContext;

    public SpringRestBeanScanStrategy(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Map<String, Object> getBeansWithAnnotation(Class<Rest> rest) {
        return applicationContext.getBeansWithAnnotation(rest);
    }


}
