package com.lwl.init;

import com.lwl.mvc.annotation.Rest;

import java.util.Map;

/**
 * Created by bruce on 2023/2/8 02:32
 */
public interface RestBeanScanStrategy {


    Map<String, Object> getBeansWithAnnotation(Class<Rest> rest);


}
