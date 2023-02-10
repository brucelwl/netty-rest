package com.lwl.httpserver.init;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by bruce on 2023/2/11 01:35
 */
public class NameThreadFactory implements ThreadFactory {

    private final AtomicInteger threadNumber = new AtomicInteger();

    private String prefix = "";

    public NameThreadFactory(String prefix) {
        this.prefix = prefix.endsWith("-") ? prefix : prefix + "-";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, prefix + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
