package com.lwl.httpserver.mvc;

import com.lwl.httpserver.init.HttpRestHandler;
import com.lwl.httpserver.mvc.annotation.ReqMapping;
import com.lwl.httpserver.mvc.annotation.ReqParam;
import com.lwl.httpserver.mvc.annotation.Rest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();

        Map<String, Object> restBeanMap = scanner.getBeansWithAnnotation(Rest.class);
        Collection<Object> restBeans = restBeanMap.values();

        for (Object restBean : restBeans) {
            //Class<?> restBeanClass = AopUtils.getTargetClass(restBean);
            Class<?> restBeanClass = restBean.getClass();
            Method[] declaredMethods = restBeanClass.getDeclaredMethods();
            if (!(declaredMethods.length > 0)) {
                continue;
            }
            Rest restAnnotation = restBeanClass.getAnnotation(Rest.class);
            //防止映射的url没写分割符,拼接出错误的url
            String parentPath = "/".concat(restAnnotation.value()).concat("/");
            for (Method method : declaredMethods) {
                ReqMapping mapping = null;
                if (!Modifier.isPublic(method.getModifiers()) || (mapping = method.getAnnotation(ReqMapping.class)) == null) {
                    continue;
                }
                UrlMappingMethodInfo methodInfo = new UrlMappingMethodInfo(restBean, method);
                methodInfo.supportMethod = mapping.method();
                methodInfo.parameterNames = discoverer.getParameterNames(method);
                methodInfo.genericParamTypes = method.getGenericParameterTypes();
                methodInfo.reqParamAnnotations = getReqParamAnnotation(method.getParameters());
                String[] paths = mapping.value();
                if (paths.length == 0) {
                    //如果注解路径参数为空,就以方法名作为url路径
                    String url = toReqUrl(parentPath.concat(method.getName()));
                    this.saveReqMapping(url, methodInfo);
                } else {
                    for (String path : paths) {
                        String url = toReqUrl(parentPath.concat(path));
                        this.saveReqMapping(url, methodInfo);
                    }
                }
            }

        }
    }

    public void invoke(ChannelHandlerContext ctx, FullHttpRequest request) {
        String queryUri = request.uri(); //获取客户端访问的Uri
        logger.info("客户端访问的Uri是:{}", queryUri);
        //分割掉get请求携带的参数
        String uri = queryUri;
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        //判断请求的url是否存在,uri没有映射的方法返回404
        if (!urlMethodInfoMap.containsKey(uri)) {
            HttpRestHandler.sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        HttpMethod httpMethod = request.method(); //获取客户端请求方法
        UrlMappingMethodInfo methodInfo = urlMethodInfoMap.get(uri);//通过url获取映射请求的方法
        boolean supportMethod = methodInfo.isSupportMethod(httpMethod);
        if (!supportMethod) { //不支持的请求方法
            HttpRestHandler.sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        //解析请求参数
        Map<String, List<String>> queryParams = null;
        if (httpMethod == HttpMethod.GET) {
            //解析get请求参数
            queryParams = decodeGetParams(queryUri);
        } else if (request.method() == HttpMethod.POST) {
            // 处理POST请求
            queryParams = decodePostParams(request);
        }
        //解析方法参数名
        String[] parameterNames = methodInfo.parameterNames;
        //获取参数类型
        Type[] parameterTypes = methodInfo.genericParamTypes;
        //获取参数对应的注解
        ReqParam[] reqParamAnnotations = methodInfo.reqParamAnnotations;
        try {
            MethodInvokeArgs result = parseMethodInvokeArgs(parameterNames, parameterTypes, reqParamAnnotations, queryParams);
            if (result.status == HttpResponseStatus.BAD_REQUEST) {
                HttpRestHandler.sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }
            Object rtn;
            if (parameterTypes.length > 0) {
                rtn = methodInfo.objMethod.invoke(methodInfo.obj, result.methodInvokeArgs);
            } else {
                rtn = methodInfo.objMethod.invoke(methodInfo.obj);
            }
            HttpRestHandler.sendResp(ctx, rtn, request);
        } catch (Exception e) {
            e.printStackTrace();
            HttpRestHandler.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 解析get的请求参数
     *
     * @param queryUri 浏览器输入的url
     */
    private Map<String, List<String>> decodeGetParams(String queryUri) {
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(queryUri);
        return queryStringDecoder.parameters();
    }

    /**
     * 解析post请求参数
     */
    private Map<String, List<String>> decodePostParams(FullHttpRequest request) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                new DefaultHttpDataFactory(false), request);
        List<InterfaceHttpData> postData = decoder.getBodyHttpDatas(); //
        if (postData.size() == 0) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> params = new LinkedHashMap<>(postData.size());
        for (InterfaceHttpData data : postData) {
            if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                MemoryAttribute attribute = (MemoryAttribute) data;
                // Often there's only 1 value.
                List<String> values = params.computeIfAbsent(attribute.getName(), k -> new ArrayList<String>(1));
                values.add(attribute.getValue());
            }
        }
        return params;
    }

    /**
     * 将扫描注解得到的url和将对用的对象,方法映射
     *
     * @param url        扫描拼接得到的url
     * @param methodInfo 对应控制器实例的实例方法
     */
    private void saveReqMapping(String url, UrlMappingMethodInfo methodInfo) {
        if (urlMethodInfoMap.containsKey(url)) {
            throw new RuntimeException("存在多个请求映射:".concat(url));
        }
        urlMethodInfoMap.put(url, methodInfo);
        logger.info("url:{} {} obj:{} methodInfo:{}", url, methodInfo.supportMethod, objId(methodInfo.obj), methodInfo);
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
     * @param parameterNames 控制器方法参数名
     * @param parameterTypes 控制器方法参数类型
     * @param httpParams     请求参数
     * @return
     */
    private MethodInvokeArgs parseMethodInvokeArgs(String[] parameterNames,
                                                   Type[] parameterTypes,
                                                   ReqParam[] reqParamAnnotations,
                                                   Map<String, List<String>> httpParams) {
        MethodInvokeArgs result = new MethodInvokeArgs();
        result.status = HttpResponseStatus.CONTINUE;
        Object[] args = null;
        if (parameterNames != null && parameterNames.length > 0) {
            args = new Object[parameterNames.length];
            for (int i = 0; i < parameterNames.length; i++) {
                ReqParam paramAnno = reqParamAnnotations[i];
                //优先使用注解定义的参数,默认使用方法定义参数
                String httpParamName = (paramAnno != null && !paramAnno.value().trim().equals(""))
                        ? paramAnno.value().trim() : parameterNames[i];

                //获取http请求参数对应的值
                //List<String> values = httpParams.get(httpParamName);
                Object values = getValueAndConvenience(parameterTypes[i], httpParamName, httpParams);

                //如果该参数要求必填,且参数值为null,返回客户端错误的请求信息
                if (values == null && paramAnno != null && paramAnno.required()) {
                    result.status = HttpResponseStatus.BAD_REQUEST;
                    return result;
                }
                //注意这个时候得到的 http 请求值values可能为null或者size = 0,
                //size=0表示客户端传递了参数名但没有值
                args[i] = values;
            }
        }
        result.methodInvokeArgs = args;
        return result;
    }

    public boolean isBasicType(Class<?> parameterType) {
        if (parameterType == String.class || parameterType.isPrimitive()) {
            return true;
        }
        Field type = ReflectionUtils.findField(parameterType, "TYPE");
        if (type == null) {
            return false;
        }
        ReflectionUtils.makeAccessible(type);
        Object field = ReflectionUtils.getField(type, parameterType);
        if (field instanceof Class) {
            return ((Class<?>) field).isPrimitive();
        }
        return false;
    }

    /**
     * @param parameterType 参数类型
     */
    private Object getValueAndConvenience(Type parameterType, String httpParamName, Map<String, List<String>> httpParams) {
        if (parameterType instanceof Class && isBasicType((Class<?>) parameterType)) {
            //一个参数可能对应多个值
            List<String> values = httpParams.get(httpParamName);
            return values == null
                    ? null
                    : DefaultConversionService.getSharedInstance().convert(values, (Class<?>) parameterType);
        }
        //AbstractEnvironment environment = new AbstractEnvironment() {
        //};
        //MapPropertySource mapPropertySource = new MapPropertySource("http-req-params", new HashMap<>(httpParams));
        //environment.getPropertySources().addLast(mapPropertySource);
        //Binder binder = Binder.get(environment);
        //
        //if (parameterType instanceof ParameterizedType) {
        //    ParameterizedType parameterizedType = (ParameterizedType) parameterType;
        //    Type rawType = parameterizedType.getRawType();
        //    if (List.class.isAssignableFrom((Class<?>) rawType)) {
        //        Bindable<? extends List<?>> listBindable = Bindable.listOf((Class<?>) parameterizedType.getActualTypeArguments()[0]);
        //        return binder.bind(httpParamName, listBindable).orElse(null);
        //    }
        //    if (Set.class.isAssignableFrom((Class<?>) rawType)) {
        //        Bindable<? extends Set<?>> setBindable = Bindable.setOf((Class<?>) parameterizedType.getActualTypeArguments()[0]);
        //        return binder.bind(httpParamName, setBindable).orElse(null);
        //    }
        //}
        //ResolvableType resolvableType = ResolvableType.forType(parameterType);
        //return binder.bind("", Bindable.of(resolvableType)).orElse(null);
        throw new IllegalArgumentException("不支持,需要手动写一个属性到对象的映射工具");
    }

    public ReqParam[] getReqParamAnnotation(Parameter[] parameters) {
        ReqParam[] reqParamAnnotations = new ReqParam[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ReqParam reqParam = parameters[i].getAnnotation(ReqParam.class);
            reqParamAnnotations[i] = reqParam;
        }
        return reqParamAnnotations;
    }

    /**
     * 方法调用参数,如果status为HttpResponseStatus.BAD_REQUEST;提示客户端请求错误信息
     */
    private static class MethodInvokeArgs {
        private Object[] methodInvokeArgs;
        private HttpResponseStatus status;
    }

    /**
     * url映射的方法信息
     */
    private static class UrlMappingMethodInfo {
        /**
         * @param obj    控制器实例对象
         * @param method 请求对应的方法
         */
        private UrlMappingMethodInfo(Object obj, Method method) {
            this.obj = obj;
            this.objMethod = method;
        }

        private Object obj;    //method所在实体类
        private Method objMethod;

        private ReqMethod[] supportMethod; //该方法支持的http请求方式

        private String[] parameterNames; //方法参数名
        private Type[] genericParamTypes; //方法参数类型
        private ReqParam[] reqParamAnnotations; //方法参数对应的注解

        private boolean isSupportMethod(HttpMethod reqMethod) {
            return supportMethod != null && supportMethod.length > 0
                    && Arrays.stream(supportMethod).anyMatch(support -> support.name().equalsIgnoreCase(reqMethod.name()));
        }

        @Override
        public String toString() {
            return "UrlMappingMethodInfo{" +
                    "obj=" + obj +
                    ", objMethod=" + objMethod +
                    ", supportMethod=" + Arrays.toString(supportMethod) +
                    ", parameterNames=" + Arrays.toString(parameterNames) +
                    ", genericParamTypes=" + Arrays.toString(genericParamTypes) +
                    ", reqParamAnnotations=" + Arrays.toString(reqParamAnnotations) +
                    '}';
        }
    }

}
