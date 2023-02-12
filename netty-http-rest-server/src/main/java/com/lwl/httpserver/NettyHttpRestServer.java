package com.lwl.httpserver;

import com.lwl.httpserver.init.HttpRestPipelineInit;
import com.lwl.httpserver.mvc.extension.HttpMessageConverter;
import com.lwl.httpserver.mvc.MessageConverterRegistry;
import com.lwl.httpserver.mvc.extension.RestAnnotationScanner;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous networking with Netty
 * <pre>
 *  TCP消息粘包/拆包问题,解决策略:
 *  1/消息定长,例如每个报文的大小固定长度200字节,如果不够,空格不齐
 *  2/在尾部增加回车换行符(分割符)进行分割,例如FTP协议
 *  3/将消息分为消息头和消息体,消息头中包含表示消息总长度的字段,通常设计思路为
 *  消息头的第一个字段使用int32来表示消息的总长度
 *  4/自定义协议
 * </pre>
 * <pre>
 *  ChannelOption.SO_BACKLOG:指定了内核为此套接口排队的最大连接数,对于给定的监听套接口
 *  内核维护两个队列:未连接队列和已连接队列,三次握手完成后,将会从未完成队列移动到已完成
 *  队列的尾部,当进程调用accept时,从已完成队列的头部取出一个给进程.
 *  ChannelOption.SO_BACKLOG,被规定为两个队列总和的最大值,大多数实现
 *  默认值为5,在高并发的情况下明显不够,netty,默认设置为windows200,其他为128
 * </pre>
 *
 * @author bruce - 2018/4/30 19:17
 */
public class NettyHttpRestServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttpRestServer.class);

    private static final List<NioEventLoopGroup> nioEventLoopGroups = new ArrayList<>(2);
    private final HttpServerConfig config;
    private final ChannelInitializer<Channel> channelInitializer;

    public NettyHttpRestServer(RestAnnotationScanner restAnnotationScanner, HttpServerConfig config) {
        this.channelInitializer = new HttpRestPipelineInit(restAnnotationScanner, config);
        this.config = config;
    }

    public void setHttpMessageConverter(HttpMessageConverter converter) {
        MessageConverterRegistry.setHttpMessageConverter(converter);
    }

    public void startServer() {
        NioEventLoopGroup acceptGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup handlerGroup = new NioEventLoopGroup();
        nioEventLoopGroups.add(acceptGroup);
        nioEventLoopGroups.add(handlerGroup);

        //handlerGroup.setIoRatio(60); //调整I/O执行时间比例

        //netty对Selector的selectedKeys进行了优化,默认没有打开
		/*System.setProperty("io.netty.noKeySetOptimization", "true");
		boolean disable_keyset_optimization =
				SystemPropertyUtil.getBoolean("io.netty.noKeySetOptimization", false);
		System.out.println(disable_keyset_optimization);*/

        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(acceptGroup, handlerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 200) //指定了内核为此套接口排队的最大连接数
                .option(ChannelOption.SO_RCVBUF, 256)  //接收缓冲区
                //.option(ChannelOption.SO_SNDBUF,1024)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000) //设置连接超时时间
                //.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT) //使用对象池重用缓冲区
                .childOption(ChannelOption.SO_SNDBUF, 1024) //发送缓冲区
                .localAddress(config.getPort())
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(channelInitializer);

        ChannelFuture future = bootstrap.bind().syncUninterruptibly();
        try {
            future.get(10, TimeUnit.SECONDS);
            logPrint("HttpRestServer start success on port: " + config.getPort());
        } catch (Exception ex) {
            logPrint("HttpRestServer start failed:" + ex.getMessage());
        }

        //future.addListener((ChannelFutureListener) future1 -> {
        //    if (future1.isSuccess()) {
        //        logPrint("HttpRestServer start success on port: " + port);
        //    } else {
        //        logPrint("HttpRestServer start failed:" + future1.cause().getMessage());
        //    }
        //});

    }

    public void stopServer() {
        logPrint("HttpRestServer stopping...");
        List<Future<?>> futures = new ArrayList<>(nioEventLoopGroups.size());
        for (NioEventLoopGroup nioEventLoopGroup : nioEventLoopGroups) {
            Future<?> future = nioEventLoopGroup.shutdownGracefully();
            future.addListener((f) -> logPrint("HttpRestServer stopped : " + future.isSuccess()));
            futures.add(future);
        }
        futures.forEach(future -> {
            try {
                future.sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

    }

    private void logPrint(String msg) {
        if (logger instanceof NOPLogger) {
            System.out.println(msg);
        } else {
            logger.info(msg);
        }
    }

}
