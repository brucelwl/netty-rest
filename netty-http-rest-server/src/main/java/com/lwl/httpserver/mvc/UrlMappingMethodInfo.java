package com.lwl.httpserver.mvc;

import com.lwl.httpserver.mvc.annotation.ReqParam;
import io.netty.handler.codec.http.HttpMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Created by bruce on 2023/2/11 00:29
 * <p>
 * url映射的方法信息
 */
public class UrlMappingMethodInfo {

    /**
     * @param obj    控制器实例对象
     * @param method 请求对应的方法
     */
    public UrlMappingMethodInfo(Object obj, Method method) {
        this.obj = obj;
        this.objMethod = method;
    }

    private final Object obj;    //method所在实体类
    private final Method objMethod;

    private ReqMethod[] supportMethod; //该方法支持的http请求方式

    private String[] parameterNames; //方法参数名
    private Type[] genericParamTypes; //方法参数类型
    private ReqParam[] reqParamAnnotations; //方法参数对应的注解

    public boolean isSupportMethod(HttpMethod reqMethod) {
        return supportMethod != null && supportMethod.length > 0
                && Arrays.stream(supportMethod).anyMatch(support -> support.name().equalsIgnoreCase(reqMethod.name()));
    }

    public Object getObj() {
        return obj;
    }

    public Method getObjMethod() {
        return objMethod;
    }

    public ReqMethod[] getSupportMethod() {
        return supportMethod;
    }

    public void setSupportMethod(ReqMethod[] supportMethod) {
        this.supportMethod = supportMethod;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    public void setParameterNames(String[] parameterNames) {
        this.parameterNames = parameterNames;
    }

    public Type[] getGenericParamTypes() {
        return genericParamTypes;
    }

    public void setGenericParamTypes(Type[] genericParamTypes) {
        this.genericParamTypes = genericParamTypes;
    }

    public ReqParam[] getReqParamAnnotations() {
        return reqParamAnnotations;
    }

    public void setReqParamAnnotations(ReqParam[] reqParamAnnotations) {
        this.reqParamAnnotations = reqParamAnnotations;
    }

    @Override
    public String toString() {
        return "UrlMappingMethodInfo{" +
                "obj=" + obj +
                ", objMethod=" + objMethod +
                ", supportMethod=" + Arrays.toString(supportMethod) +
                ", parameterNames=" + Arrays.toString(parameterNames) +
                ", genericParamTypes=" + Arrays.toString(genericParamTypes) +
                ", reqParamAnnotations=" + Arrays.toString(reqParamAnnotations) +
                '}';
    }

}
