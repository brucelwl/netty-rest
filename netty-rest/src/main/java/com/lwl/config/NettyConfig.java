package com.lwl.config;

import com.lwl.init.HttpRestPipelineInit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author bruce - 2018/4/30 19:16
 */
@Component
public class NettyConfig {

    @Value("${netty.servr.port:8568}")
    private int nettyPort;

    @Value("${netty.rest.package:com.lwl.nettyrest}")
    private String scanPackage;

    //基于netty实现rest风格的http请求,仿造Spring依赖注入
    @PostConstruct
    public void initHttpRestServer() {
        NettyNioServer nettyNio = new NettyNioServer(new HttpRestPipelineInit(scanPackage));
        nettyNio.startServer(nettyPort);
        System.out.println("netty tcp server start on port " + nettyPort);
    }


}
