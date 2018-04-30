package com.lwl.config;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;

import java.util.ArrayList;
import java.util.List;

/** * Asynchronous networking with Netty
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
public class NettyNioServer {
    private static List<NioEventLoopGroup> nioEventLoopGroups = new ArrayList<>(2);

    private final ChannelHandler handlers ;

    public NettyNioServer(ChannelHandler hannelInitializer) {
        this.handlers = hannelInitializer;
    }

    public void startServer(int port) {
        NioEventLoopGroup acceptGroup = new NioEventLoopGroup();
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

        bootstrap.group(acceptGroup,handlerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 200) //指定了内核为此套接口排队的最大连接数
                .option(ChannelOption.SO_RCVBUF,256)
                .option(ChannelOption.SO_TIMEOUT, 15_000)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,30_000) //设置连接超时时间
                //.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT) //使用对象池重用缓冲区
                .localAddress(port)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(handlers);

        ChannelFuture future = bootstrap.bind();
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.isSuccess()){
                    System.out.println("Server bound success on port: "+port);
                }else{
                    System.err.println("Bound attempt failed");
                    future.cause().printStackTrace();
                }
            }
        });

    }

    public static void stopServer() {
        System.out.println("netty server stop...");
        List<Future<?>> futures = new ArrayList<>(nioEventLoopGroups.size());
        for (NioEventLoopGroup nioEventLoopGroup : nioEventLoopGroups) {
            Future<?> future = nioEventLoopGroup.shutdownGracefully();
            future.addListener((f) -> System.out.println("netty server stopped : " + future.isSuccess()));
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

}
