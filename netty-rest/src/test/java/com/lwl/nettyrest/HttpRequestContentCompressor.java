package com.lwl.nettyrest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;

/**
 * Created by bruce on 2019/11/19 11:01
 */
public class HttpRequestContentCompressor extends ChannelOutboundHandlerAdapter {

    private int contentSizeThreshold;

    public HttpRequestContentCompressor() {
        this.contentSizeThreshold = 1024;
    }

    public HttpRequestContentCompressor(int contentSizeThreshold) {
        this.contentSizeThreshold = contentSizeThreshold > 0 ? contentSizeThreshold : 1024;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        DefaultFullHttpRequest fullHttpRequest = (DefaultFullHttpRequest) msg;

        ByteBuf content = fullHttpRequest.content();
        int readableBytes = content.readableBytes();
        if (readableBytes > contentSizeThreshold) {
            GzipEncoder gzipEncoder = new GzipEncoder(6);

            ByteBuf compressed = Unpooled.buffer(content.readableBytes());

            gzipEncoder.encode(ctx, content, compressed);
            content.resetReaderIndex();
            content.resetWriterIndex();
            content.writeBytes(compressed);

            fullHttpRequest.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);
        }
        if (readableBytes > 0) {
            fullHttpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }


        super.write(ctx, fullHttpRequest, promise);

    }


}
