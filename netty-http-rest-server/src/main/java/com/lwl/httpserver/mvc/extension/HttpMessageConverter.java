package com.lwl.httpserver.mvc.extension;

import java.lang.reflect.Type;

/**
 * Created by bruce on 2023/2/12 22:36
 */
public interface HttpMessageConverter {


    Object read(String body, Type type);

    byte[] write(Object obj);

}
