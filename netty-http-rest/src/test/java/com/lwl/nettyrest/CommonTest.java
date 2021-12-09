package com.lwl.nettyrest;

import com.alibaba.fastjson.JSON;
import com.lwl.entity.UserInfo;
import org.junit.Test;

import java.io.IOException;

public class CommonTest {

    @Test
    public void test1() {

        String content = "哈哈哈哈哈哈哈哈哈哈哈哈哈哈";
        byte[] bytes = content.getBytes();
        System.out.println("压缩前长度:" + bytes.length);
        byte[] zip = GzipUtils.gzip(bytes);
        System.out.println("压缩后长度:" + zip.length);

        byte[] bytes1 = GzipUtils.unGzip(zip);
        System.out.println("还原数据:" + new String(bytes1));
    }

    @Test
    public void test2() throws IOException {
        UserInfo userInfo = new UserInfo();
        userInfo.setPwd("aaaazsdkjhlklkgg,jhg急急急急急急急急急急急急国际劳工开发开放开发了一个了");
        userInfo.setLoginname("aaaaa中文");

        byte[] bytes = JSON.toJSONBytes(userInfo);

        System.out.println(bytes.length);

        GzipUtils.gzipObject(userInfo);

        byte[] gzip = GzipUtils.gzip(bytes);
        System.out.println(gzip.length);
    }


}
