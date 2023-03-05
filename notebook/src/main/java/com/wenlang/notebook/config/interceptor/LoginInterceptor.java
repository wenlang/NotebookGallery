package com.wenlang.notebook.config.interceptor;

import com.alibaba.fastjson.JSON;
import com.wenlang.notebook.config.ResultCode;
import com.wenlang.notebook.utils.RedisUtil;
import com.wenlang.notebook.utils.ResponseUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import static com.wenlang.notebook.config.cons.CommonCons.AUTHORIZATION;
import static com.wenlang.notebook.config.cons.CommonCons.DEVICETYPE;


/**
 * 登录拦截器
 */
@Component
public class LoginInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        response.setCharacterEncoding("utf-8");
        response.setContentType("text/html; charset=utf-8");
        // 如果不是映射到方法直接通过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 从header中得到token
        String authorization = request.getHeader(AUTHORIZATION);
        String deviceType = request.getHeader(DEVICETYPE);
        boolean res = checkToken(authorization, deviceType);
        if (!res) {
            PrintWriter printWriter = response.getWriter();
            printWriter.write(JSON.toJSONString(ResponseUtil.result(ResultCode.LOGIN_TIMEOUT)));
            printWriter.close();
        }
        return res;
    }

    private boolean checkToken(String authorization, String deviceType) {

        if (StringUtils.isEmpty(authorization) || StringUtils.isEmpty(deviceType)) {
            return false;
        }

        /**
         * authorization等于MD5(token_userNum)
         */
        //兼容处理
        Object val1 = redisUtil.get(authorization);
        return val1 != null;
    }

}
