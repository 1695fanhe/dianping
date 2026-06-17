package com.example.dianping.config;

import com.example.dianping.utils.LoginInterceptor;
import com.example.dianping.utils.RefreshTokenInterceptor;
import com.example.dianping.utils.SystemConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("redirect:/login.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations("file:" + SystemConstants.IMAGE_UPLOAD_DIR + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 🔥 1. 先刷新 token + 放入 UserHolder
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .order(0)
                .addPathPatterns("/**");

        // 🔥 2. 再做登录校验
        registry.addInterceptor(new LoginInterceptor())
                .order(1)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**",
                        "/",
                        "/**/*.html",
                        "/css/**",
                        "/js/**",
                        "/imgs/**",
                        "/favicon.ico"
                );
    }
}