package com.lwl.nettyrest;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by bruce on 2019/11/17 21:39
 */
public class NettyHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttpClient.class);

    public void req(String url) {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                //.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                //.option(ChannelOption.RCVBUF_ALLOCATOR, new MyAdaptiveRecvByteBufAllocator())
                //.handler(new ClientHandlerChannelInitializer());
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast("httpClientCodec", new HttpClientCodec());

                        //压缩请求数据
                        pipeline.addLast("HttpRequestContentCompressor", new HttpRequestContentCompressor(1024));

                        //解压返回数据
                        pipeline.addLast("httpContentDecompressor", new HttpContentDecompressor());

                        //返回数据聚合
                        pipeline.addLast("httpAggregator", new HttpObjectAggregator(1024 * 1024));

                        pipeline.addLast("HttpClientHandler", new HttpClientHandler());

                    }
                });

        try {
            URL reqUrl = new URL(url);

            int port = reqUrl.getPort();
            if (port == -1) {
                port = reqUrl.getProtocol().equals("http") ? 80 : 443;
            }
            URI pathUrl = new URI(reqUrl.getPath() + "?" + reqUrl.getQuery());

            ChannelFuture channelFuture = bootstrap.connect(reqUrl.getHost(), port).sync();

            //测试返回数据gzip压缩
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                stringBuilder.append("" + i);
            }

            ByteBuf content = Unpooled.wrappedBuffer(stringBuilder.toString().getBytes());

            System.out.println("readableBytes:" + content.readableBytes());

            DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, pathUrl.toASCIIString(), content);

            HttpHeaders headers = req.headers();
            headers.set(HttpHeaderNames.HOST, reqUrl.getHost() + ":" + reqUrl.getPort());
            headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            headers.set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE);
            headers.set(HttpHeaderNames.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
            headers.set(HttpHeaderNames.USER_AGENT, "nett_http_client");

            Channel channel = channelFuture.channel();

            ChannelFuture channelFuture1 = channel.writeAndFlush(req);
            channelFuture1.addListener(future -> {
                if (!future.isSuccess()) {
                    Throwable cause = future.cause();
                    cause.printStackTrace();
                }
            });

            channelFuture.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {

        String url = "http://localhost:8568/index/say?address=aaa&age=15";

        //String url = "http://www.imooc.com/course/list";

        NettyHttpClient nettyHttpClient = new NettyHttpClient();

        nettyHttpClient.req(url);

    }


}
