package com.lwl.httpserver.boot.autoconfigure;

import com.lwl.httpserver.NettyHttpRestServer;
import com.lwl.httpserver.mvc.SpringRestAnnotationScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;


/**
 * @author bruce - 2018/4/30 19:16
 */
@Configuration
public class HttpRestServerAutoConfiguration implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(HttpRestServerAutoConfiguration.class);

    @Value("${netty.servr.port:8568}")
    private int nettyPort;

    private ConfigurableApplicationContext applicationContext;
    private NettyHttpRestServer httpRestServer;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) context;
    }

    //基于netty实现rest风格的http请求,仿造Spring依赖注入
    @Override
    public void afterSingletonsInstantiated() {
        SpringRestAnnotationScanner restAnnotationScanner = new SpringRestAnnotationScanner(applicationContext);

        httpRestServer = new NettyHttpRestServer(restAnnotationScanner);
        httpRestServer.startServer(nettyPort);
    }


    @Override
    public void destroy() throws Exception {
        httpRestServer.stopServer();
    }
}
