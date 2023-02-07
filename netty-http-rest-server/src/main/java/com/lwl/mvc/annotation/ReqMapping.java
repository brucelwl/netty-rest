package com.lwl.mvc.annotation;

import com.lwl.mvc.ReqMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author bruce - 2018/3/11 12:54
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReqMapping {

    String[] value() default {};

    ReqMethod[] method() default {ReqMethod.GET, ReqMethod.POST};

}
