package com.lwl.httpserver.file;

import com.lwl.httpserver.init.HttpRestHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by bruce in 2020/3/5 21:39
 */
public class HttpFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(HttpFileServerHandler.class);

    //非法URI正则
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    //文件是否被允许访问下载验证
    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

    private final String url;

    public HttpFileServerHandler(String url) {
        this.url = url;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        //对请求的编码结果进行判断
        if (!req.decoderResult().isSuccess()) {
            HttpRestHandler.sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        if (req.method() != HttpMethod.GET) {
            HttpRestHandler.sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String uri = req.uri();
        String path = sanitizeUri(uri);
        if (path == null) {
            //403
            HttpRestHandler.sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }
        //创建file对象
        File file = new File(path);
        if (!file.exists() || file.isHidden()) {
            HttpRestHandler.sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        // 如果为文件夹
        if (file.isDirectory()) {
            if (uri.endsWith("/")) {
                //如果以正常"/"结束 说明是访问的一个文件目录：则进行展示文件列表（web服务端则可以跳转一个Controller，遍历文件并跳转到一个页面）
                sendListing(ctx, file);
            } else {
                //如果非"/"结束 则重定向，补全"/" 再次请求
                sendRedirect(ctx, uri + '/');
            }
            return;
        }
        // 如果所创建的file对象不是文件类型
        if (!file.isFile()) {
            // 403
            HttpRestHandler.sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        // Cache Validation
        String ifModifiedSince = req.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null) {
            sendNotModified(ctx, req);
            return;
        }

        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        //建立响应对象
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        //获取文件长度
        long fileLength = randomAccessFile.length();
        //设置响应信息
        HttpUtil.setContentLength(response, fileLength);
        //设置响应头
        setContentTypeHeader(response, file);

        setDateAndCacheHeaders(response, file);

        //如果一直保持连接则设置响应头信息为：HttpHeaders.Values.KEEP_ALIVE
        if (HttpUtil.isKeepAlive(req)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        //进行写出
        ctx.write(response);

        //写出ChunkedFile,如果使用 writeAndFlush 则不会打印 发送进度
        //ChannelProgressivePromise channelProgressivePromise = ctx.newProgressivePromise();
        //channelProgressivePromise.addListener(new ChannelProgressiveFutureListener() {
        //    @Override
        //    public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) throws Exception {
        //        if (total < 0) { // total unknown
        //            System.err.println("Transfer progress: " + progress);
        //        } else {
        //            System.err.println("Transfer progress: " + progress + " / " + total);
        //        }
        //    }
        //
        //    @Override
        //    public void operationComplete(ChannelProgressiveFuture future) throws Exception {
        //        System.out.println("Transfer complete.");
        //    }
        //});

        //HttpChunkedInput will write the end marker (LastHttpContent) for us.
        //ChannelFuture sendFileFuture = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(randomAccessFile)), ctx.newProgressivePromise());
        ChannelFuture sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile), ctx.newProgressivePromise());
        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) throws Exception {
                if (total < 0) { // total unknown
                    logger.info("Transfer progress: {}", progress);
                } else {
                    logger.info("Transfer progress: {}", progress + " / " + total);
                }
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future) throws Exception {
                logger.info("Transfer complete.");
            }
        });

        //发送一个空消息体，主要是用于刷新缓冲区数据,向客户端发送
        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        //ChannelFuture lastContentFuture = sendFileFuture;
        //如果当前连接请求非Keep-Alive ，最后一包消息发送完成后 服务器主动关闭连接
        if (!HttpUtil.isKeepAlive(req)) {
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.channel().isActive()) {
            HttpRestHandler.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            ctx.close();
        }
        cause.printStackTrace();
    }

    private void sendNotModified(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED, Unpooled.EMPTY_BUFFER);

        this.sendAndCleanupConnection(ctx, request, response);
    }

    /**
     * If Keep-Alive is disabled, attaches "Connection: close" header to the response
     * and closes the connection after the response being sent.
     */
    private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        final boolean keepAlive = HttpUtil.isKeepAlive(request);
        HttpUtil.setContentLength(response, response.content().readableBytes());
        if (!keepAlive) {
            // We're going to close the connection as soon as the response is sent,
            // so we should also make it clear for the client.
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        } else if (request.protocolVersion().equals(HTTP_1_0)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        ChannelFuture flushPromise = ctx.writeAndFlush(response);

        if (!keepAlive) {
            // Close the connection as soon as the response is sent.
            flushPromise.addListener(ChannelFutureListener.CLOSE);
        }
    }


    private static void setContentTypeHeader(HttpResponse response, File file) {
        //使用mime对象获取文件类型
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
    }

    /**
     * Expires：指定了在浏览器上缓冲存储的页距过期还有多少时间，等同Cache-control中的max-age的效果，
     * 如果同时存在，则被Cache-Control的max-age覆盖。若把其值设置为0，则表示页面立即过期。
     * 并且若此属性在页面当中被设置了多次，则取其最小值。
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));

        // Date header
        Calendar time = new GregorianCalendar();
        //response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

        int HTTP_CACHE_SECONDS = 0;
        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        //response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaderNames.LAST_MODIFIED,
                dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /** 重定向操作 **/
    private static void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        //建立响应对象
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        //设置新的请求地址放入响应对象中去
        response.headers().set(HttpHeaderNames.LOCATION, newUri);
        //使用ctx对象写出并且刷新到SocketChannel中去 并主动关闭连接(这里是指关闭处理发送数据的线程连接)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendListing(ChannelHandlerContext ctx, File dir) {
        // 设置响应对象
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        // 响应头
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        // 追加文本内容
        StringBuilder ret = new StringBuilder();
        String dirPath = dir.getPath();
        ret.append("<!DOCTYPE html>\r\n");
        ret.append("<html><head><title>");
        ret.append(dirPath);
        ret.append(" 目录：");
        ret.append("</title></head><body>\r\n");
        ret.append("<h3>");
        ret.append(dirPath).append(" 目录：");
        ret.append("</h3>\r\n");
        ret.append("<ul>");
        ret.append("<li>链接：<a href=\"../\">..</a></li>\r\n");

        File[] files = dir.listFiles();
        if (files != null) {
            // 遍历文件 添加超链接
            for (File f : files) {
                //step 1: 跳过隐藏或不可读文件
                if (f.isHidden() || !f.canRead()) {
                    continue;
                }
                String name = f.getName();
                //step 2: 如果不被允许，则跳过此文件
                if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
                    continue;
                }
                //拼接超链接即可
                ret.append("<li>链接：<a href=\"");
                ret.append(name);
                ret.append("\">");
                ret.append(name);
                ret.append("</a></li>\r\n");
            }
        }
        ret.append("</ul></body></html>\r\n");
        //构造结构，写入缓冲区
        ByteBuf buffer = Unpooled.copiedBuffer(ret, CharsetUtil.UTF_8);
        //进行写出操作
        response.content().writeBytes(buffer);
        //重置写出区域
        buffer.release();
        //使用ctx对象写出并且刷新到SocketChannel中去 并主动关闭连接(这里是指关闭处理发送数据的线程连接)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /** 从url解析出本地文件路径 */
    private String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        // 对uri进行细粒度判断：4步验证操作
        if (!uri.startsWith(url)) {
            return null;
        }
        if (!uri.startsWith("/")) {
            return null;
        }
        //将文件分隔符替换为本地操作系统的文件路径分隔符
        uri = uri.replace('/', File.separatorChar);

        if (uri.contains(File.separator + '.')
                || uri.contains('.' + File.separator) || uri.startsWith(".")
                || uri.endsWith(".") || INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        //当前工程所在目录 + URI构造绝对路径进行返回
        return System.getProperty("user.dir") + File.separator + uri;
    }


}
