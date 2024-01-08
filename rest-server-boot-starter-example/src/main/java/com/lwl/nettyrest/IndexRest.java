package com.lwl.nettyrest;

import com.alibaba.fastjson2.JSON;
import com.lwl.entity.UserInfo;
import com.lwl.httpserver.mvc.ReqMethod;
import com.lwl.httpserver.mvc.annotation.ReqMapping;
import com.lwl.httpserver.mvc.annotation.ReqParam;
import com.lwl.httpserver.mvc.annotation.Rest;

import java.util.List;

/**
 * @author liwenlong - 2018/3/11 13:18
 */
@Rest("index")
public class IndexRest {

    @ReqMapping(value = {"say", "hehe"})
    public UserInfo sayHello(@ReqParam(value = "name1", required = true) List<String> name,
                             String address, Integer age) {
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

    @ReqMapping(value = "say2", method = {ReqMethod.POST})
    public UserInfo sayHello2(UserInfo userInfo) {
        return userInfo;
    }

    @ReqMapping(value = "say3")
    public String sayHello3(@ReqParam(required = false) String id) {
        return "哈哈哈:" + id;
    }

    /**
     * 方法参数没有使用注解@ReqParam(required = false)表示必传
     *
     * @param id
     * @return
     */
    @ReqMapping(value = "say4", method = ReqMethod.POST)
    public String sayHello4(String id) {
        return id;
    }

}
