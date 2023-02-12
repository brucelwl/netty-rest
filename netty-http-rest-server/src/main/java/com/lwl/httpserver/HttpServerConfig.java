package com.lwl.httpserver;

import io.netty.handler.ssl.SslContext;

/**
 * Created by bruce on 2023/2/11 01:45
 */
public class HttpServerConfig {
    private int port = 6688;

    /** 处理业务线程最小数量 */
    private int minThreads = 10;
    /** 处理业务线程最大数量 */
    private int maxThreads = 100;
    /** 线程空闲时间 */
    private long threadKeepAliveMs = 60000;

    /** 默认开启返回数据压缩 */
    private boolean compressionEnabled = true;

    /** 默认超过2kb开始压缩 */
    private int compressionThreshold = 2 * 1024;

    private boolean sslClientMode = false;

    private String threadPrefix;
    private String nioThreadPrefix;

    private SslContext sslContext;



    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMinThreads() {
        return minThreads;
    }

    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public long getThreadKeepAliveMs() {
        return threadKeepAliveMs;
    }

    public void setThreadKeepAliveMs(long threadKeepAliveMs) {
        this.threadKeepAliveMs = threadKeepAliveMs;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    public int getCompressionThreshold() {
        return compressionThreshold;
    }

    public void setCompressionThreshold(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    public boolean isSslClientMode() {
        return sslClientMode;
    }

    public void setSslClientMode(boolean sslClientMode) {
        this.sslClientMode = sslClientMode;
    }

    public String getThreadPrefix() {
        return threadPrefix;
    }

    public void setThreadPrefix(String threadPrefix) {
        this.threadPrefix = threadPrefix;
    }

    public String getNioThreadPrefix() {
        return nioThreadPrefix;
    }

    public void setNioThreadPrefix(String nioThreadPrefix) {
        this.nioThreadPrefix = nioThreadPrefix;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SslContext sslContext) {
        this.sslContext = sslContext;
    }
}
