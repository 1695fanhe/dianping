package com.example.dianping.utils;

import cn.hutool.core.bean.BeanUtil;
import com.example.dianping.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String token = request.getHeader("Authorization");
        System.out.println("token:" + token);
        if (token == null || token.isEmpty()) {
            return true;
        }

        String key = RedisConstants.LOGIN_USER_KEY + token;

        Map<Object, Object> userMap =
                stringRedisTemplate.opsForHash().entries(key);

        if (userMap == null || userMap.isEmpty()) {
            return true;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        UserHolder.saveUser(userDTO);
        System.out.println("userDTO : " + userDTO);
        // 🔥 刷新 TTL（滑动过期）
        stringRedisTemplate.expire(
                key,
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.MINUTES
        );

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserHolder.removeUser();
    }
}