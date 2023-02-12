package com.lwl.httpserver.mvc.extension;


import com.lwl.httpserver.mvc.annotation.Rest;

import java.util.Map;

/**
 * Created by bruce on 2023/2/8 02:32
 */
public interface RestAnnotationScanner {


    Map<String, Object> getBeansWithAnnotation(Class<Rest> rest);


}
