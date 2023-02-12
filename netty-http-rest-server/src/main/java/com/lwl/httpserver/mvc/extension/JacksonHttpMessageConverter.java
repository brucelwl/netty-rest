package com.lwl.httpserver.mvc.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;

/**
 * Created by bruce on 2023/2/12 22:44
 */
public class JacksonHttpMessageConverter implements HttpMessageConverter {

    private final ObjectMapper objectMapper;

    public JacksonHttpMessageConverter() {
        objectMapper = new ObjectMapper();
    }

    @Override
    public Object read(String body, Type type) {
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(type);
            return objectMapper.readValue(body, javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] write(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
