package com.lwl.httpserver.boot.autoconfigure;

import com.lwl.httpserver.HttpServerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author bruce - 2018/4/30 19:16
 */
@Configuration
public class HttpRestServerAutoConfiguration  {

    @ConfigurationProperties(prefix = "bruce.http.rest.server")
    @Bean
    public HttpServerConfig newHttpServerConfig(){
        return new HttpServerConfig();
    }

    @Bean
    public HttpRestServerBean newHttpRestServerBean(HttpServerConfig config){
        return new HttpRestServerBean(config);
    }
}
