package com.lwl.nettyrest;


import com.lwl.mvc.annotation.ReqMapping;
import com.lwl.mvc.ReqMethod;
import com.lwl.mvc.annotation.Rest;

/**
 * @author liwenlong - 2018/3/11 13:18
 */
@Rest
public class ThirdRest {

    @ReqMapping(method = {ReqMethod.GET})
    public void sayHello(String name){

    }

    @ReqMapping(method = {ReqMethod.GET})
    private void sayHello2(String name){

    }

    @ReqMapping(method = {ReqMethod.GET})
    protected void sayHello3(String name){

    }


    @ReqMapping(method = {ReqMethod.GET})
    public void sayHello4(String name){

    }



}
