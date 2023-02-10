package com.lwl.httpserver.mvc;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Created by bruce on 2023/2/11 00:30
 * <p>
 * 方法调用参数,如果status为HttpResponseStatus.BAD_REQUEST;提示客户端请求错误信息
 */
public class MethodInvokeArgs {

    private Object[] methodInvokeArgs;
    private HttpResponseStatus status;

    public Object[] getMethodInvokeArgs() {
        return methodInvokeArgs;
    }

    public void setMethodInvokeArgs(Object[] methodInvokeArgs) {
        this.methodInvokeArgs = methodInvokeArgs;
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public void setStatus(HttpResponseStatus status) {
        this.status = status;
    }
}
