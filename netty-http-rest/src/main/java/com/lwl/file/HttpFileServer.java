package com.lwl.file;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * Created by bruce in 2020/3/5 21:31
 */
public class HttpFileServer {
    private static final String DEFAULT_URL = "/sources/";

    public void run(int port, String url) {
        NioEventLoopGroup acceptGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(acceptGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("httpServerCodec", new HttpServerCodec());
                        //把多个消息转换为单一的FullHttpRequest或者FullHttpResponse
                        pipeline.addLast("http-aggregator", new HttpObjectAggregator(65535));
                        //加入chunked 主要作用是支持异步发送的码流（大文件传输），但不占用过多的内存，防止java内存溢出
                        pipeline.addLast("chunkedWriteHandler", new ChunkedWriteHandler());
                        // 加入自定义处理文件服务器的业务逻辑handler
                        pipeline.addLast("fileServerHandler", new HttpFileServerHandler(url));
                    }
                });
        try {
            ChannelFuture startChannelFuture = serverBootstrap.bind(port).sync();
            startChannelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    System.out.println("HTTP文件目录服务器启动，网址是 : " + "http://127.0.0.1:" + port + url);
                } else {
                    future.cause().printStackTrace();
                }
            });

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8765;
        String url = DEFAULT_URL;
        new HttpFileServer().run(port, url);
    }


}
