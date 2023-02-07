package com.lwl.nettyrest;

import com.alibaba.fastjson.JSON;
import com.lwl.entity.UserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

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

    @Test
    public void test3() {

        boolean b = DefaultConversionService.getSharedInstance().canConvert(String.class, List.class);
        System.out.println(b);
        System.out.println(DefaultConversionService.getSharedInstance().canConvert(List.class, String.class));

        Object convert = DefaultConversionService.getSharedInstance().convert("aaa", List.class);
        System.out.println(convert);

        ArrayList<String> values = new ArrayList<>();
        values.add("1");
        values.add("2");
        values.add("1");
        //values.add("aaa");
        HashMap<String, Object> params = new HashMap<>();
        params.put("id", 4);
        params.put("loginname", "bb");

        MutablePropertyValues propertyValues = new MutablePropertyValues(params);

        Optional<UserInfo> empty = Optional.empty();

        DataBinder dataBinder = new DataBinder(empty, "user");
        dataBinder.setIgnoreInvalidFields(false);
        dataBinder.setIgnoreUnknownFields(false);

        dataBinder.bind(propertyValues);

        List<ObjectError> allErrors = dataBinder.getBindingResult().getAllErrors();
        List<FieldError> fieldErrors = dataBinder.getBindingResult().getFieldErrors();

        Object target = dataBinder.getTarget();
        //dataBinder.setBindingErrorProcessor();

        Map<String, Object> model = dataBinder.getBindingResult().getModel();

        System.out.println(target);

        System.out.println(int.class.isPrimitive());

        //dataBinder.

    }

    @Test
    public void test4() throws NoSuchMethodException {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("address.code", "17");
        hashMap.put("id", Arrays.asList("15", "16"));
        hashMap.put("loginname", Arrays.asList("aaa", "bbb"));

        MapPropertySource mapPropertySource = new MapPropertySource("http-req-params", hashMap);

        AbstractEnvironment env = new AbstractEnvironment() {
        };
        env.getPropertySources().addLast(mapPropertySource);

        Binder binder = Binder.get(env);

        //Bindable.listOf()
        //BindResult<HashMap<String,Object>> bind = binder.bind("", HashMap.class);
        //HashMap userInfo = bind.orElse(null);

        Method sayHello = IndexRest.class.getMethod("sayHello", ArrayList.class, String.class, Integer.class);

        ResolvableType resolvableType = ResolvableType.forMethodParameter(sayHello, 0);

        MethodParameter methodParameter = new MethodParameter(sayHello, 0);
        Type genericParameterType = methodParameter.getGenericParameterType();

        //ResolvableType.forMethodParameter()

        System.out.println(resolvableType);
    }

}
