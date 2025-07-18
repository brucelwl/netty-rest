package com.lwl.httpserver.init;

import com.lwl.httpserver.HttpServerConfig;
import com.lwl.httpserver.mvc.RestProcessor;
import com.lwl.httpserver.mvc.extension.RestAnnotationScanner;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;


/**
 * @author bruce - 2018/2/5 13:32
 */
public class HttpRestPipelineInit extends ChannelInitializer<Channel> {

    private HttpServerConfig config;
    private final HttpRestHandler httpRestHandler;

    public HttpRestPipelineInit(RestAnnotationScanner scanner, HttpServerConfig config) {
        this.config = config;
        this.httpRestHandler = new HttpRestHandler(new RestProcessor(scanner), config);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (config.getSslContext() != null) {
            SSLEngine sslEngine = config.getSslContext().newEngine(ch.alloc());
            sslEngine.setUseClientMode(config.isSslClientMode());
            pipeline.addFirst("ssl", new SslHandler(sslEngine));
        }
        //pipeline.addLast("decoder", new HttpRequestDecoder());
        //pipeline.addLast("encoder", new HttpResponseEncoder());
        //等同于以上解码器和编码器
        pipeline.addLast("httpServerCodec", new HttpServerCodec());
        //解压请求数据
        pipeline.addLast("httpContentDecompressor", new HttpContentDecompressor());
        //请求数据聚合
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(65535));

        //启数据压缩,必须保证HttpContentCompressor#decode方法在向客户端返回数据之前执行,因此必须放在httpRestHandler之前
        //当返回的数据超过1024字节时压缩数据
        if (config.isCompressionEnabled()) {
            pipeline.addLast("httpContentCompressor",
                    new HttpContentCompressor(6, 15, 8, config.getCompressionThreshold()));
        }

        pipeline.addLast("httpChunked", new ChunkedWriteHandler());
        // pipeline.addLast("httpKeepaliveHandler", new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS));

        pipeline.addLast("httpRestHandler", httpRestHandler);

    }
}
