package com.lwl.nettyrest;


import com.lwl.annotation.ReqMapping;
import com.lwl.annotation.ReqMethod;
import com.lwl.annotation.Rest;

/**
 * @author liwenlong - 2018/3/11 13:18
 */
@Rest("second")
public class SecondRest {

    @ReqMapping(value = "say",method = {ReqMethod.GET})
    public String sayHello(String name){

        return "hahahahah";
    }

    @ReqMapping(value = "say2",method = {ReqMethod.GET})
    private void sayHello2(String name){

    }

    @ReqMapping(value = "say3",method = {ReqMethod.GET})
    protected void sayHello3(String name){

    }




}
