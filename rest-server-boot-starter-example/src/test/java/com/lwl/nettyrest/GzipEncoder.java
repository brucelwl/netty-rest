package com.lwl.nettyrest;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * Created by bruce on 2019/11/19 14:08
 */
public class GzipEncoder extends JdkZlibEncoder {


    public GzipEncoder(int compressionLevel) {
        super(ZlibWrapper.GZIP, compressionLevel);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, ByteBuf uncompressed, ByteBuf out) throws Exception {
        super.encode(ctx, uncompressed, out);
    }
}
