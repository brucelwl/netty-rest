package com.lwl.nettyrest;

import com.alibaba.fastjson2.JSON;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Created by bruce on 2023/11/15 01:26
 */
public class UndertowHttpServer {

    public static void main(String[] args) throws IOException {
        OptionMap xnioWorkerOptions = OptionMap.builder()
                .set(Options.WORKER_NAME, "Http-Server")
                .set(Options.WORKER_TASK_CORE_THREADS, 10)
                .set(Options.WORKER_TASK_MAX_THREADS, 100)
                .set(Options.WORKER_IO_THREADS, Runtime.getRuntime().availableProcessors() / 2)
                .getMap();
        XnioWorker xnioWorker = Xnio.getInstance().createWorker(xnioWorkerOptions);

        PathHandler pathHandler = Handlers.path(new DefaultHandler());

        // 处理/aa路径的handler
        pathHandler.addPrefixPath("aa", new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                System.out.println("exchange:" + exchange + "," + Thread.currentThread().getName());
            }
        });

        DispatchRequestHandler rootHandler = new DispatchRequestHandler(pathHandler);
        Undertow server = Undertow.builder()
                .setWorker(xnioWorker)
                .addHttpListener(8081, "0.0.0.0", rootHandler)
                .build();
        server.start();
    }


    public static class DispatchRequestHandler implements HttpHandler {

        private final HttpHandler httpHandler;

        public DispatchRequestHandler(HttpHandler httpHandler) {
            this.httpHandler = httpHandler;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            System.out.println("exchange:" + exchange + "," + Thread.currentThread().getName() + " " + exchange.getRequestURL());
            exchange.dispatch(httpHandler);
        }

    }


    public static class DefaultHandler implements HttpHandler {


        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            String requestURL = exchange.getRequestURL();

            HttpString requestMethod = exchange.getRequestMethod();

            HeaderMap requestHeaders = exchange.getRequestHeaders();

            // HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, null);

            exchange.startBlocking();
            InputStream inputStream = exchange.getInputStream();

            // ServletInputStream inputStream = request.getInputStream();

            Object o = JSON.parseObject(inputStream, Map.class);

            //System.out.println(o);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Hello World");
        }
    }

}
