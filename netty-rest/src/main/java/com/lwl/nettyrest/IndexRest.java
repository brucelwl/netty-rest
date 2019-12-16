package com.lwl.nettyrest;

import com.alibaba.fastjson.JSON;
import com.lwl.annotation.ReqMapping;
import com.lwl.annotation.ReqMethod;
import com.lwl.annotation.ReqParam;
import com.lwl.annotation.Rest;
import com.lwl.entity.UserInfo;

import java.util.ArrayList;

/**
 * @author liwenlong - 2018/3/11 13:18
 */
@Rest("index")
public class IndexRest {

    @ReqMapping(value = {"say", "hehe"})
    public UserInfo sayHello(@ReqParam(value = "name1", required = false) ArrayList<String> name, String address, Integer age) {
        UserInfo userInfo = new UserInfo();
        userInfo.setNickname(JSON.toJSONString(name));
        userInfo.setId(1001);
        userInfo.setLoginname("啊哈哈哈");
        userInfo.setPwd("wdwsfddsf23546+5");

        //测试返回数据gzip压缩
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 357; i++) {
            stringBuilder.append("" + i);
        }
        userInfo.setPwd(stringBuilder.toString());

        return userInfo;
    }

    @ReqMapping(value = "say2", method = {ReqMethod.GET})
    public String sayHello2(@ReqParam(required = false) String address, Integer age) {
        return address + " age " + age;
    }

    @ReqMapping(value = "say3")
    public String sayHello3(@ReqParam(required = false) String id) {
        return "哈哈哈:" + id;
    }

    /**
     * 方法参数没有使用注解@ReqParam(required = false)表示必传
     * @param id
     * @return
     */
    @ReqMapping(value = "say4", method = ReqMethod.POST)
    public String sayHello4(String id) {
        return id;
    }

}
