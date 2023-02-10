package com.lwl.httpserver.init;

import com.lwl.httpserver.mvc.RestAnnotationScanner;
import com.lwl.httpserver.mvc.RestProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;


/**
 * @author bruce - 2018/2/5 13:32
 */
public class HttpRestPipelineInit extends ChannelInitializer<Channel> {

    private final boolean sslClientMode;
    private final SslContext sslContext;
    private boolean gzipOrDeflate;
    private final HttpRestHandler httpRestHandler;

    public HttpRestPipelineInit(RestAnnotationScanner scanner) {
        this(scanner, false, false, null);
    }

    public HttpRestPipelineInit(RestAnnotationScanner scanner, boolean sslClientMode, SslContext sslContext) {
        this(scanner, sslClientMode, false, sslContext);
    }

    public HttpRestPipelineInit(RestAnnotationScanner scanner, boolean sslClientMode, boolean gzipOrDeflate) {
        this(scanner, sslClientMode, gzipOrDeflate, null);
    }

    public HttpRestPipelineInit(RestAnnotationScanner scanner, boolean sslClientMode, boolean gzipOrDeflate, SslContext sslContext) {
        this.sslContext = sslContext;
        this.sslClientMode = sslClientMode;
        this.gzipOrDeflate = gzipOrDeflate;
        RestProcessor processor = new RestProcessor(scanner);
        httpRestHandler = new HttpRestHandler(processor);
        try {
            processor.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslContext != null) {
            SSLEngine sslEngine = sslContext.newEngine(ch.alloc());
            sslEngine.setUseClientMode(sslClientMode);
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

        if (gzipOrDeflate) {
            //启数据压缩,必须保证HttpContentCompressor#decode方法在向客户端返回数据之前执行,因此必须放在httpRestHandler之前
            //当返回的数据超过1024字节时压缩数据
            pipeline.addLast("httpContentCompressor", new HttpContentCompressor(6, 15, 8, 1024));
        }

        //pipeline.addLast("httpChunked", new ChunkedWriteHandler());

        pipeline.addLast("httpRestHandler", httpRestHandler);

    }
}
