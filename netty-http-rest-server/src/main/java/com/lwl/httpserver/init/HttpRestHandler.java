package com.lwl.httpserver.init;

import com.lwl.httpserver.HttpServerConfig;
import com.lwl.httpserver.mvc.MessageConverterRegistry;
import com.lwl.httpserver.mvc.RestProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;

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

        String prefix = config.getThreadPrefix() != null && config.getThreadPrefix().trim().length() > 0
                ? config.getThreadPrefix() : "http-rest-handler-";

        threadPoolExecutor = new ThreadPoolExecutor(config.getMinThreads(), config.getMaxThreads(),
                config.getThreadKeepAliveMs(), TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                new NameThreadFactory(prefix));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        logger.info("Received request {}, {}, url:{}", ctx.channel().remoteAddress(), ctx.channel().localAddress(), request.uri());
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
        // 解析请求参数
        Map<String, List<String>> reqParams = null;
        if (httpMethod == HttpMethod.GET) {
            // 解析get请求参数
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
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        ByteBuf content;
        if (obj instanceof String) {
            content = Unpooled.copiedBuffer((String) obj, CharsetUtil.UTF_8);
            headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        } else {
            byte[] bytes = MessageConverterRegistry.writeObject(obj);
            content = Unpooled.wrappedBuffer(bytes);
            headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        }

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        if (!keepAlive) {
            // Tell the client we're going to close the connection.
            headers.set(CONNECTION, CLOSE);
        } else {
            // 设置keepalive超时时间
            headers.set(KEEP_ALIVE, "timeout=60");
            headers.set(CONNECTION, KEEP_ALIVE);
        }

        // 不使用chunked传输
        ChannelFuture future = respContentLength(content, ctx, headers);

        // 使用chunked传输
        // ChannelFuture future = respEncodingChunked(content, ctx, headers);

        // 如果是非Keep-Alive，关闭连接
        if (!keepAlive) {
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

    static ChannelFuture respContentLength(ByteBuf content, ChannelHandlerContext ctx, HttpHeaders headers) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(headers);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        response.content().writeBytes(content);
        return ctx.writeAndFlush(response);
    }

    // 使用chunked传输
    static ChannelFuture respEncodingChunked(ByteBuf content, ChannelHandlerContext ctx, HttpHeaders headers) {
        DefaultHttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        httpResponse.headers().add(headers);
        HttpUtil.setTransferEncodingChunked(httpResponse, true);

        ctx.write(httpResponse);

        return ctx.writeAndFlush(new HttpChunkedInput(new ChunkedStream(new ByteArrayInputStream(content.array()))));
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.ALL_IDLE) {
                logger.info("http1 keepalive 超时,关闭连接");
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
