package org.xinp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration //声明为配置类
@RequiredArgsConstructor
public class WedConfig implements WebMvcConfigurer {

    private final Interceptor interceptor; //用户信息处理

    //注册拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.
                addInterceptor(interceptor) //添加拦截器（组,）
                .addPathPatterns("/**") //设置拦截路径（组,）
                .excludePathPatterns("/api/user/login"); //设置不拦截路径（组,）
    }
}