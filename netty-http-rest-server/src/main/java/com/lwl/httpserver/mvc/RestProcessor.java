package com.lwl.httpserver.mvc;

import com.lwl.httpserver.init.HttpRestHandler;
import com.lwl.httpserver.mvc.annotation.ReqMapping;
import com.lwl.httpserver.mvc.annotation.ReqParam;
import com.lwl.httpserver.mvc.annotation.Rest;
import com.lwl.httpserver.mvc.extension.RestAnnotationScanner;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <pre>
 *  获取方法参数名两种解决方案:
 *     1 利用java8的新特性Parameter[] parameters = method.getParameters();可以获取,但是要开启-parameters参数
 *     2 利用Spring的LocalVariableTableParameterNameDiscoverer解析方法参数名,好处是不需要开启Java8特性
 *  应该在什么时候解析方法参数名:
 *     1 在方法调用的时候,这样的好处是产生的是局部变量内存回收块,占用资源少
 *     2 在初始化所有的类实例,映射url到方法的时候,通过map保存为全局变量,不需要每次都解析,加快运行速度
 * </pre>
 *
 * @author bruce - 2018/3/11 17:31
 */
public class RestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RestProcessor.class);

    private ConcurrentHashMap<String, UrlMappingMethodInfo> urlMethodInfoMap = new ConcurrentHashMap<>();

    private RestAnnotationScanner scanner;

    public RestProcessor(RestAnnotationScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * 扫描控制器注解类,并实例化,解析url和方法映射关系
     */
    public void prepare() {
        DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

        Map<String, Object> restBeanMap = scanner.getBeansWithAnnotation(Rest.class);
        Collection<Object> restBeans = restBeanMap.values();

        for (Object restBean : restBeans) {
            // Class<?> restBeanClass = AopUtils.getTargetClass(restBean);
            Class<?> restBeanClass = restBean.getClass();
            Method[] declaredMethods = restBeanClass.getDeclaredMethods();
            if (!(declaredMethods.length > 0)) {
                continue;
            }
            Rest restAnnotation = restBeanClass.getAnnotation(Rest.class);
            // 防止映射的url没写分割符,拼接出错误的url
            String parentPath = "/".concat(restAnnotation.value()).concat("/");
            for (Method method : declaredMethods) {
                ReqMapping mapping = null;
                if (!Modifier.isPublic(method.getModifiers()) || (mapping = method.getAnnotation(ReqMapping.class)) == null) {
                    continue;
                }
                UrlMappingMethodInfo methodInfo = new UrlMappingMethodInfo(restBean, method);
                methodInfo.setSupportMethod(mapping.method());
                methodInfo.setParameterNames(discoverer.getParameterNames(method));

                Parameter[] parameters = method.getParameters();
                methodInfo.setParameters(parameters);
                methodInfo.setReqParamAnnotations(getReqParamAnnotation(parameters));

                String[] paths = mapping.value();
                if (paths.length == 0) {
                    // 如果注解路径参数为空,就以方法名作为url路径
                    String url = toReqUrl(parentPath.concat(method.getName()));
                    this.saveReqMapping(url, methodInfo);
                    continue;
                }
                for (String path : paths) {
                    String url = toReqUrl(parentPath.concat(path));
                    this.saveReqMapping(url, methodInfo);
                }
            }

        }
    }


    public void invoke(ChannelHandlerContext ctx, FullHttpRequest request,
                       Map<String, List<String>> reqParams, String reqBody) {
        String queryUri = request.uri(); // 获取客户端访问的Uri
        // 分割掉get请求携带的参数
        String uri = queryUri;
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        // 通过url获取映射请求的方法,uri没有映射的方法返回404
        UrlMappingMethodInfo methodInfo = urlMethodInfoMap.get(uri);
        if (methodInfo == null) {
            logger.warn("response http 404, url from client is:{}", queryUri);
            HttpRestHandler.sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        HttpMethod httpMethod = request.method(); // 获取客户端请求方法
        boolean supportMethod = methodInfo.isSupportMethod(httpMethod);
        if (!supportMethod) { // 不支持的请求方法
            HttpRestHandler.sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        // 获取参数类型
        Parameter[] parameters = methodInfo.getParameters();
        try {
            MethodInvokeArgs result = parseMethodInvokeArgs(methodInfo, contentType, reqParams, reqBody);
            if (result.getStatus() == HttpResponseStatus.BAD_REQUEST) {
                HttpRestHandler.sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }
            Object rtn;
            if (parameters.length > 0) {
                rtn = methodInfo.getObjMethod().invoke(methodInfo.getObj(), result.getMethodInvokeArgs());
            } else {
                rtn = methodInfo.getObjMethod().invoke(methodInfo.getObj());
            }
            HttpRestHandler.sendResp(ctx, rtn, request);
        } catch (Exception e) {
            logger.error("处理请求:{},发生异常:", queryUri, e);
            HttpRestHandler.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * 将扫描注解得到的url和将对用的对象,方法映射
     *
     * @param url        扫描拼接得到的url
     * @param methodInfo 对应控制器实例的实例方法
     */
    private void saveReqMapping(String url, UrlMappingMethodInfo methodInfo) {
        UrlMappingMethodInfo previous = urlMethodInfoMap.putIfAbsent(url, methodInfo);
        if (previous != null) {
            throw new RuntimeException("存在多个请求映射:".concat(url));
        }
        logger.info("url:{} {} obj:{} methodInfo:{}", url, methodInfo.getSupportMethod(), objId(methodInfo.getObj()), methodInfo);
    }

    private String objId(Object obj) {
        String s = obj.toString();
        return s.substring(s.lastIndexOf(".") + 1);
    }

    /**
     * <pre>
     * 拼接出url可能出现多个分割符的情况,重新分割拼接
     * 例如:/mvnrepository.com///artifact//javax/javaee-api/8.0
     * 调整后为:/mvnrepository.com/artifact/javax/javaee-api/8.0
     * </pre>
     */
    private String toReqUrl(String url) {
        String[] split = url.split("/");
        StringBuilder builder = new StringBuilder();
        for (String s : split) {
            if (s != null && !s.equals("")) {
                builder.append("/").append(s);
            }
        }
        return builder.toString();
    }

    /**
     * 将http请求参数转为控制器方法调用参数
     *
     * @param httpParams 请求参数
     * @return
     */
    private MethodInvokeArgs parseMethodInvokeArgs(UrlMappingMethodInfo methodInfo,
                                                   String contentType,
                                                   Map<String, List<String>> httpParams,
                                                   String reqBody) {
        String[] parameterNames = methodInfo.getParameterNames();
        ReqParam[] reqParamAnnotations = methodInfo.getReqParamAnnotations();
        Parameter[] parameters = methodInfo.getParameters();

        MethodInvokeArgs result = new MethodInvokeArgs();
        result.setStatus(HttpResponseStatus.CONTINUE);
        Object[] args = null;
        if (parameterNames != null && parameterNames.length > 0) {
            args = new Object[parameterNames.length];
            for (int i = 0; i < parameterNames.length; i++) {
                ReqParam paramAnno = reqParamAnnotations[i];
                // 优先使用注解定义的参数,默认使用方法定义参数
                String httpParamName = (paramAnno != null && !paramAnno.value().trim().equals(""))
                        ? paramAnno.value().trim() : parameterNames[i];

                // 获取http请求参数对应的值
                // List<String> values = httpParams.get(httpParamName);
                Object values = getValueAndConvenience(parameters[i], httpParamName, contentType, httpParams, reqBody);

                // 如果该参数要求必填,且参数值为null,返回客户端错误的请求信息
                if (values == null && paramAnno != null && paramAnno.required()) {
                    result.setStatus(HttpResponseStatus.BAD_REQUEST);
                    return result;
                }
                // 注意这个时候得到的 http 请求值values可能为null或者size = 0,
                // size=0表示客户端传递了参数名但没有值
                args[i] = values;
            }
        }
        result.setMethodInvokeArgs(args);
        return result;
    }


    /**
     * @param parameter 参数类型
     */
    private Object getValueAndConvenience(Parameter parameter, String httpParamName,
                                          String contentType,
                                          Map<String, List<String>> httpParams,
                                          String reqBody) {
        if (!"application/json".equals(contentType)) {
            // 一个参数可能对应多个值
            List<String> values = httpParams.get(httpParamName);
            return values == null
                    ? null
                    : DefaultConversionService.getSharedInstance().convert(values, new TypeDescriptor(MethodParameter.forParameter(parameter)));
        }
        // 除了基础类型为, 仅支持application/json请求
        return MessageConverterRegistry.parseObject(reqBody, parameter.getType());
    }

    private ReqParam[] getReqParamAnnotation(Parameter[] parameters) {
        ReqParam[] reqParamAnnotations = new ReqParam[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ReqParam reqParam = parameters[i].getAnnotation(ReqParam.class);
            reqParamAnnotations[i] = reqParam;
        }
        return reqParamAnnotations;
    }


}
