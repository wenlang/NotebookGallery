package com.wenlang.notebook.config.ratelimit.distribute;

import com.alibaba.fastjson.JSON;
import com.wenlang.notebook.config.ResultCode;
import com.wenlang.notebook.utils.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static com.wenlang.notebook.config.cons.CommonCons.AUTHORIZATION;


/**
 * 限流拦截器
 * 当我们api限流是基于IP或用户的话，那么我们有一些约定：
 * 基于IP限流的话，则http request的中header里面必须带有ip信息，ip的header支持
 *   "X-Forwarded-For","X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP",   "HTTP_X_FORWARDED_FOR"
 * <p>
 * 特别注意，Nginx作为分发服务器时，则Nginx forward正式客户端请求到下游微服务时，务必要把客户的真实IP塞入到header中往下传递，需要在Nginx server配置中加入
 * proxy_set_header    X-Real-IP        $remote_addr;
 * 基于用户限流的话，约定用户token的header名称为：UserToken
 */
@Slf4j
public class RateLimitCheckInterceptor implements HandlerInterceptor {
    private static final String USER_TOKEN_KEY = AUTHORIZATION;
    @Autowired
    private RedisRateLimiterFactory redisRateLimiterFactory;
    @Value("${appName:admin}")
    private String appNameT;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        boolean isSuccess = true;
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        if (method.isAnnotationPresent(RateLimiter.class)) {
            RateLimiter rateLimiterAnnotation = method.getAnnotation(RateLimiter.class);
            int permits = rateLimiterAnnotation.permits();
            TimeUnit timeUnit = rateLimiterAnnotation.timeUnit();
            String path = rateLimiterAnnotation.path();
            if ("".equals(path)) {
                path = request.getRequestURI();
            }
            //获取应用名称
            String appName = appNameT;
            path = appName + ":ratelimit:" + "{" + path + "}";
            //todo 添加访问管理模块逻辑，即进行IP的拉黑，黑名单、白名单、设定访问频率阙值后自动拉黑，限制访问等逻辑
            //todo 最好使用mq进行异步处理+redis缓存
            if (rateLimiterAnnotation.base() == RateLimiter.Base.General) {
                String rateLimiterKey = path;
                RedisRateLimiter redisRatelimiter = redisRateLimiterFactory.get(path, timeUnit, permits);
                isSuccess = rateCheck(redisRatelimiter, rateLimiterKey, response);
            } else if (rateLimiterAnnotation.base() == RateLimiter.Base.IP) {
                //获取真实的IP
                String ip = RateLimitIpUtil.getIpAddress(request);
                if (ip != null) {
                    String rateLimiterKey = path + ":" + ip;
                    RedisRateLimiter redisRatelimiter = redisRateLimiterFactory.get(rateLimiterKey, timeUnit, permits);
                    isSuccess = rateCheck(redisRatelimiter, rateLimiterKey, response);
                }
            } else if (rateLimiterAnnotation.base() == RateLimiter.Base.User) {
                String userToken = getUserToken(request);
                if (userToken != null) {
                    String rateLimiterKey = path + ":" + userToken;
                    RedisRateLimiter redisRatelimiter = redisRateLimiterFactory.get(rateLimiterKey, timeUnit, permits);
                    isSuccess = rateCheck(redisRatelimiter, rateLimiterKey, response);
                }
            }

        }
        return isSuccess;
    }


    private boolean rateCheck(RedisRateLimiter redisRatelimiter, String keyPrefix, HttpServletResponse response)
            throws Exception {
        if (!redisRatelimiter.acquire(keyPrefix)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().print(JSON.toJSONString(ResponseUtil.result(ResultCode.RATE_LIMIT)));
            return false;
        }
        return true;
    }


    private String getUserToken(HttpServletRequest request) {
        String userToken = request.getHeader(USER_TOKEN_KEY);
        return userToken;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
    }

}

