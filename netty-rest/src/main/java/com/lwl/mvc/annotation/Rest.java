package com.lwl.mvc.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 *  注解参数的可支持数据类型：
 *　　1.所有基本数据类型（int,float,boolean,byte,double,char,long,short)
 *　　2.String类型
 *　　3.Class类型
 *　　4.enum类型
 *　　5.Annotation类型
 *　　6.以上所有类型的数组
 * </pre>
 * @author bruce - 2018/3/11 12:47
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Rest {

    String value() default "";

}
