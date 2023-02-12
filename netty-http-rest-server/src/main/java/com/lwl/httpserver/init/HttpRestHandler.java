package com.lwl.httpserver.init;

import com.alibaba.fastjson.JSON;
import com.lwl.httpserver.HttpServerConfig;
import com.lwl.httpserver.mvc.RestProcessor;
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
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author liwenlong - 2018/3/11 16:12
 */
@ChannelHandler.Sharable
public class HttpRestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(HttpRestHandler.class);

    private final RestProcessor processor;
    private final HttpServerConfig config;
    private final ThreadPoolExecutor threadPoolExecutor;

    public HttpRestHandler(RestProcessor processor, HttpServerConfig httpServerConfig) {
        this.processor = processor;
        this.config = httpServerConfig;

        processor.prepare();

        threadPoolExecutor = new ThreadPoolExecutor(config.getMinThreads(), config.getMaxThreads(),
                config.getThreadKeepAliveMs(), TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                new NameThreadFactory("http-rest-handler-"));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        Map<String, List<String>> reqParams = null;
        String reqBody = null;
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (HttpHeaderValues.APPLICATION_JSON.toString().equals(contentType)) {
            reqBody = request.content().toString(CharsetUtil.UTF_8);
            if (logger.isDebugEnabled()) {
                logger.debug("request body:{}", reqBody);
            }
        } else {
            reqParams = parseReqParams(request);
        }

        Map<String, List<String>> finalReqParams = reqParams;
        String finalReqBody = reqBody;
        threadPoolExecutor.execute(() -> {
            try {
                processor.invoke(ctx, request, finalReqParams, finalReqBody);
            } catch (Exception ex) {
                logger.info("处理请求异常:", ex);
            }
        });
    }

    private Map<String, List<String>> parseReqParams(FullHttpRequest request) {
        String queryUri = request.uri();
        HttpMethod httpMethod = request.method();
        //解析请求参数
        Map<String, List<String>> reqParams = null;
        if (httpMethod == HttpMethod.GET) {
            //解析get请求参数
            reqParams = decodeGetParams(queryUri);
        } else if (request.method() == HttpMethod.POST) {
            // 处理POST请求
            reqParams = decodePostParams(request);
        }
        return reqParams;
    }

    /**
     * 解析get的请求参数
     *
     * @param queryUri 浏览器输入的url
     */
    private Map<String, List<String>> decodeGetParams(String queryUri) {
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(queryUri);
        return queryStringDecoder.parameters();
    }

    /**
     * 解析post请求参数
     */
    private Map<String, List<String>> decodePostParams(FullHttpRequest request) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                new DefaultHttpDataFactory(false), request);
        List<InterfaceHttpData> postData = decoder.getBodyHttpDatas(); //
        if (postData.size() == 0) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> params = new LinkedHashMap<>(postData.size());
        for (InterfaceHttpData data : postData) {
            if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                MemoryAttribute attribute = (MemoryAttribute) data;
                // Often there's only 1 value.
                List<String> values = params.computeIfAbsent(attribute.getName(), k -> new ArrayList<>(1));
                values.add(attribute.getValue());
            }
        }
        decoder.destroy();
        return params;
    }

    /**
     * 向客户端发送错误信息
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
                    logger.error("response error:", cause);
                } else {
                    logger.debug("response success!");
                }
            }
        });

    }

}
