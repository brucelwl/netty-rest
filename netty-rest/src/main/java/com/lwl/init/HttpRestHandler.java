package com.lwl.init;

import com.alibaba.fastjson.JSON;
import com.lwl.mvc.RestProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author liwenlong - 2018/3/11 16:12
 */
@ChannelHandler.Sharable
public class HttpRestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(HttpRestHandler.class);

    private RestProcessor processor;

    public HttpRestHandler(RestProcessor processor) {
        this.processor = processor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        logger.info("request content:{}", request.content().toString(CharsetUtil.UTF_8));

        processor.invoke(ctx, request);
    }

    /**
     * 向客户端发送错误信息
     *
     * @param ctx
     * @param status
     */
    public static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }


    /**
     * 向客户端发送错误信息
     */
    public static void sendResp(ChannelHandlerContext ctx, Object obj, FullHttpRequest req) {
        ByteBuf content;
        if (obj instanceof String) {
            content = Unpooled.copiedBuffer((String) obj, CharsetUtil.UTF_8);
        } else {
            byte[] bytes = JSON.toJSONBytes(obj);
            content = Unpooled.wrappedBuffer(bytes);
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        //必须要写返回数据的长度,否则在服务端不关闭Channel的情况下,客户端可能无法读取到数据
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        //如果使用了HttpContentCompressor,不应该设置该头,否则HttpContentCompressor中的gzip和deflate算法不会生效
        //如果自定义压缩算法,返回头需要添加压缩算法
        //response.headers().set(HttpHeaderNames.CONTENT_ENCODING, "gzip");

        System.out.println("原始字节数:" + content.readableBytes());

        response.content().writeBytes(content);
        content.release();

        //ChannelFuture future = ctx.writeAndFlush(response);
        ChannelFuture future = ctx.channel().writeAndFlush(response);

        // 如果是非Keep-Alive，关闭连接
        if (!HttpUtil.isKeepAlive(req)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }

        future.addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                if (!future.isSuccess()) {
                    Throwable cause = future.cause();
                    cause.printStackTrace();
                } else {
                    logger.info("response success!");
                }
            }
        });

    }

}
