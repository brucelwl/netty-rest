package com.lwl.httpserver.boot.autoconfigure;

import com.lwl.httpserver.HttpServerConfig;
import com.lwl.httpserver.NettyHttpRestServer;
import com.lwl.httpserver.mvc.SpringRestAnnotationScanner;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Created by bruce on 2023/2/11 02:39
 */
public class HttpRestServerBean implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

    private ConfigurableApplicationContext applicationContext;
    private NettyHttpRestServer httpRestServer;
    private HttpServerConfig config = null;

    public HttpRestServerBean(HttpServerConfig config) {
        this.config = config;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) context;
    }

    //基于netty实现rest风格的http请求,仿造Spring依赖注入
    @Override
    public void afterSingletonsInstantiated() {
        SpringRestAnnotationScanner restAnnotationScanner = new SpringRestAnnotationScanner(applicationContext);
        httpRestServer = new NettyHttpRestServer(restAnnotationScanner, config);
        httpRestServer.startServer();
    }

    @Override
    public void destroy() throws Exception {
        if (httpRestServer != null) {
            httpRestServer.stopServer();
        }
    }

}
