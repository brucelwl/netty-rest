package com.lwl.nettyrest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.util.CharsetUtil;

/**
 * Created by bruce on 2019/11/18 19:23
 */
public class HttpClientHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        FullHttpResponse fullHttpResponse = (FullHttpResponse) msg;
        String response = fullHttpResponse.content().toString(CharsetUtil.UTF_8);

        System.out.println(response);

    }
}
