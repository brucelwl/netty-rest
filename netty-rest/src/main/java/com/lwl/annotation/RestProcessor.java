package com.lwl.annotation;

import com.alibaba.fastjson.JSON;
import com.lwl.init.HttpRestHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private ConcurrentHashMap<String, UrlMappingMethodInfo> urlMethodInfoMap = new ConcurrentHashMap<>();

    private String scanPackage;

    public RestProcessor(String scanPackage) {
        this.scanPackage = scanPackage;
    }

    /**
     * 扫描控制器注解类,并实例化,解析url和方法映射关系
     *
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public void prepare() throws IllegalAccessException, InstantiationException {
        LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();
        Set<Class<?>> classesInPackage = new PackageScanUtils().getClassesInPackage(scanPackage, false);
        for (Class<?> aClass : classesInPackage) {
            //System.out.println("扫描到的类: " + aClass.getName());
            if (aClass.isAnnotationPresent(Rest.class)) {
                Object obj = aClass.newInstance();
                Rest restAnnotation = aClass.getAnnotation(Rest.class);
                //防止映射的url没写分割符,拼接出错误的url
                String path1 = "/".concat(restAnnotation.value()).concat("/");
                Method[] declaredMethods = aClass.getDeclaredMethods();
                if (declaredMethods != null) {
                    for (Method method : declaredMethods) {
                        ReqMapping mapping = null;
                        if (Modifier.isPublic(method.getModifiers())
                                && (mapping = method.getAnnotation(ReqMapping.class)) != null) {
                            UrlMappingMethodInfo methodInfo = new UrlMappingMethodInfo(obj, method);
                            methodInfo.supportMethod = mapping.method();
                            methodInfo.parameterNames = discoverer.getParameterNames(method);
                            methodInfo.parameterTypes = method.getParameterTypes();
                            methodInfo.reqParamAnnotations = getReqParamAnnotation(method.getParameterAnnotations());
                            String[] paths = mapping.value();
                            //如果注解路径参数为空,就以方法名作为url路径
                            if (paths.length == 0) {
                                String url = toReqUrl(path1.concat(method.getName()));
                                this.saveReqMapping(url, methodInfo);
                            } else {
                                for (String path : paths) {
                                    String url = toReqUrl(path1.concat(path));
                                    this.saveReqMapping(url, methodInfo);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void invoke(ChannelHandlerContext ctx, FullHttpRequest request) {
        String queryUri = request.uri(); //获取客户端访问的Uri
        System.out.println("客户端访问的Uri是:" + queryUri);
        //分割掉get请求携带的参数
        String uri = queryUri;
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
            System.out.println("去掉get参数url是:" + uri);
        }
        //判断请求的url是否存在,uri没有映射的方法返回404
        if (!urlMethodInfoMap.containsKey(uri)) {
            HttpRestHandler.sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        HttpMethod method = request.method(); //获取客户端请求方法
        UrlMappingMethodInfo methodInfo = urlMethodInfoMap.get(uri);//通过url获取映射请求的方法
        boolean supportMethod = methodInfo.isSupportMethod(method);
        if (!supportMethod) { //不支持的请求方法
            HttpRestHandler.sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        //解析请求参数
        Map<String, List<String>> queryParams = null;
        if (method == HttpMethod.GET) {//解析get请求参数
            queryParams = decodeGetParams(queryUri);
        } else if (request.method() == HttpMethod.POST) {
            // 处理POST请求
            queryParams = decodePostParams(request);
        }
        //解析方法参数名
        String[] parameterNames = methodInfo.parameterNames;
        //获取参数类型
        Class<?>[] parameterTypes = methodInfo.parameterTypes;
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
        System.out.println(url + " methodInfo:" + methodInfo.hashCode()
                + " obj:" + methodInfo.obj.hashCode() + " "
                + JSON.toJSONString(methodInfo.supportMethod));
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
    private MethodInvokeArgs parseMethodInvokeArgs(String[] parameterNames, Class<?>[] parameterTypes, ReqParam[] reqParamAnnotations, Map<String, List<String>> httpParams) {
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
                List<String> values = httpParams.get(httpParamName);

                //如果该参数要求必填,且参数值为null,返回客户端错误的请求信息
                if (values == null && paramAnno != null && paramAnno.required()) {
                    result.status = HttpResponseStatus.BAD_REQUEST;
                    return result;
                }
                //注意这个时候得到的 http 请求值values可能为null或者size = 0,
                //size=0表示客户端传递了参数名但没有值
                args[i] = valueConvenience(parameterTypes[i], values);
            }
        }
        result.methodInvokeArgs = args;
        return result;
    }

    /**
     * @param parameterType 参数类型
     * @param values        一个参数可能对应多个值
     * @return
     */
    private Object valueConvenience(Class<?> parameterType, List<String> values) {
        Object result = null;

        if (String.class == parameterType) {
            if (values == null) {
                return null;
            }
            if (values.size() == 0) {
                result = "";
            } else {
                result = values.get(0);
            }
        } else if (int.class == parameterType) {
            result = values == null || values.size() == 0 ? 0 : Integer.parseInt(values.get(0));

        } else if (Integer.class == parameterType) {
            result = values == null || values.size() == 0 ? null : Integer.valueOf(values.get(0));

        } else if (long.class == parameterType) {
            result = values == null || values.size() == 0 ? 0 : Long.parseLong(values.get(0));

        } else if (Long.class == parameterType) {
            result = values == null || values.size() == 0 ? null : Long.valueOf(values.get(0));

        } else if (String[].class == parameterType) {
            result = values == null || values.size() == 0 ? null : values.toArray(new String[0]);

        } else if (List.class == parameterType || ArrayList.class == parameterType) {
            result = values;
        }
        //这时候可以认为是自定义实体类类型,通过反射解析
        //TODO 其他数据类型处理
        return result;
    }

    public ReqParam[] getReqParamAnnotation(Annotation[][] paramAnnotations) {
        ReqParam[] reqParamAnnotations = new ReqParam[paramAnnotations.length];
        for (int i = 0; i < paramAnnotations.length; i++) {
            ReqParam reqParam = null;
            Annotation[] annotation = paramAnnotations[i];
            if (annotation != null && annotation.length > 0) {
                reqParam = (ReqParam) Arrays.stream(annotation)
                        .filter(anno -> anno.annotationType() == ReqParam.class)
                        .findFirst().orElse(null);
            }
            reqParamAnnotations[i] = reqParam;
        }
        return reqParamAnnotations;
    }

    /**
     * 方法调用参数,如果status为HttpResponseStatus.BAD_REQUEST;提示客户端请求错误信息
     */
    private class MethodInvokeArgs {
        private Object[] methodInvokeArgs;
        private HttpResponseStatus status;
    }

    /**
     * url映射的方法信息
     */
    private class UrlMappingMethodInfo {
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
        private Class<?>[] parameterTypes; //方法参数类型
        private ReqParam[] reqParamAnnotations; //方法参数对应的注解

        private boolean isSupportMethod(HttpMethod reqMethod) {
            return supportMethod != null && supportMethod.length > 0
                    && Arrays.stream(supportMethod).anyMatch(support -> support.name().equalsIgnoreCase(reqMethod.name()));
        }
    }

}
