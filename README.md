# netty-rest

#### 项目介绍
基于netty实现Http服务器,无需外部servlet容器(例如tomcat),使用方式类似于spring mvc 的@RestController

netty自身提供了对http的支持,通过FullHttpRequest对象可以获取请求的url,和http请求方式,但是并没有处理url应该怎么处理请求.所以基本思路是:

1. 自定义注解:@Rest,@ReqMapping,@ReqParam , @Rest用于标记某个类是控制器,@ReqMapping用于标记方法哪个url对应该方法,@ReqParam用于标记方法参数,对应于哪个http请求参数

2.编写PackageScanUtils工具类用于扫描指定包下的class为类

3 通过反射获取带有@Rest注解的类并实例化,获取@Rest和@RuqMapping的value值,拼接成url,通过ConcurrentHashMap 以键值对的形式,记录url和对应方法Method的关系,当客户端请求时,通过url从Map中获取执行的Method和对应的Object,通过反射执行对应的方法

4 将反射调用方法得到的结果通FullHttpResponse返回,返回前先判断结果类如果是字符串直接通过ChannelHandlerContext.write输出,其他类型则通过json返回
