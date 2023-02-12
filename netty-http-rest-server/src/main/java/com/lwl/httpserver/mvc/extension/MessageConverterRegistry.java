package com.lwl.httpserver.mvc.extension;

import java.lang.reflect.Type;

/**
 * Created by bruce on 2023/2/12 22:48
 */
public class MessageConverterRegistry {

    private static HttpMessageConverter defaultConverter = new JacksonHttpMessageConverter();

    public static void setHttpMessageConverter(HttpMessageConverter httpMessageConverter) {
        defaultConverter = httpMessageConverter;
    }

    public static Object parseObject(String body, Type type) {
        return defaultConverter.read(body, type);
    }

    public static byte[] writeObject(Object obj) {
        return defaultConverter.write(obj);
    }


}
