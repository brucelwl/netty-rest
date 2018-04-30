package com.lwl.init;

import com.alibaba.fastjson.JSON;
import com.lwl.annotation.RestProcessor;
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
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/**
 * @author liwenlong - 2018/3/11 16:12
 */
@ChannelHandler.Sharable
public class HttpRestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

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
        processor.invoke(ctx,request);
    }

    /**
     * 向客户端发送错误信息
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
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ByteBuf buffer;
        if (obj instanceof String) {
            buffer = Unpooled.copiedBuffer((String)obj, CharsetUtil.UTF_8);
        }else{
            byte[] bytes = JSON.toJSONBytes(obj);
            buffer = Unpooled.wrappedBuffer(bytes);
        }
        response.content().writeBytes(buffer);
        buffer.release();
        // 如果是非Keep-Alive，关闭连接
        ChannelFuture future = ctx.writeAndFlush(response);

        future.addListener(ChannelFutureListener.CLOSE);

    }

}
