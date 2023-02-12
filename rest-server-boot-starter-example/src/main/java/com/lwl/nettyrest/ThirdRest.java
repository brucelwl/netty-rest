package com.lwl.nettyrest;


import com.lwl.entity.UserInfo;
import com.lwl.httpserver.mvc.ReqMethod;
import com.lwl.httpserver.mvc.annotation.ReqMapping;
import com.lwl.httpserver.mvc.annotation.Rest;

/**
 * @author liwenlong - 2018/3/11 13:18
 */
@Rest
public class ThirdRest {

    @ReqMapping(method = {ReqMethod.GET})
    public void sayHello(String name) {
        System.out.println("");
    }

    @ReqMapping(method = {ReqMethod.GET})
    private void sayHello2(String name) {

    }

    @ReqMapping(method = {ReqMethod.GET})
    protected void sayHello3(String name) {

    }


    @ReqMapping(method = {ReqMethod.GET})
    public UserInfo sayHello4(String name) {
        return new UserInfo();
    }


}
