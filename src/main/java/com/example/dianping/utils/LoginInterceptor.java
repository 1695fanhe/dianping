package com.example.dianping.utils;

import com.example.dianping.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        UserDTO user = UserHolder.getUser();

        if (user == null) {
            response.setStatus(401);
            return false;
        }

        return true;
    }
}