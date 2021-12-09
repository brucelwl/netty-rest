package com.lwl.config;

import com.lwl.entity.UserInfo;
import com.lwl.init.HttpRestPipelineInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;


/**
 * @author bruce - 2018/4/30 19:16
 */
@Component
public class NettyConfig implements ApplicationContextAware, SmartInitializingSingleton {
    private static final Logger logger = LoggerFactory.getLogger(NettyConfig.class);

    @Value("${netty.servr.port:8568}")
    private int nettyPort;

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) context;
    }

    @Autowired
    private Environment environment;

    //基于netty实现rest风格的http请求,仿造Spring依赖注入
    @Override
    public void afterSingletonsInstantiated() {
        NettyNioServer nettyNio = new NettyNioServer(new HttpRestPipelineInit(applicationContext));
        nettyNio.startServer(nettyPort);

        logger.info("netty http server start on port {}", nettyPort);

        BindResult<UserInfo> bind = Binder.get(environment).bind("", UserInfo.class);

    }


}
