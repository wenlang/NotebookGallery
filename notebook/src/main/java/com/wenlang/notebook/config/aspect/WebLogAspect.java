package com.wenlang.notebook.config.aspect;

import com.google.gson.Gson;
import com.wenlang.notebook.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

import static com.wenlang.notebook.config.cons.CommonCons.*;
import static com.wenlang.notebook.config.cons.RedisKeyCons.WEB_LOG_ASPECT;


/**
 * 描述：接口日志切面
 */
@Aspect
@Component
@Order(100)
@Slf4j
public class WebLogAspect {
    private static ThreadLocal<Map<String, Object>> threadLocal = new ThreadLocal<>();
    private static final String START_TIME = "startTime";

    @Autowired
    private RedisUtil redisUtil;

    @Around("execution(* com.wenlang.notebook.*.controller.*.*(..))")
    public Object doAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        String webLogAspectType = null;
        try {
            webLogAspectType = redisUtil.getString(WEB_LOG_ASPECT);
        } catch (Exception e) {
            Object result = proceedingJoinPoint.proceed();
            return result;
        }

        if ((webLogAspectType != null) && (webLogAspectType.contains(WEB_LOG_ASPECT_CUSTOMER) || Objects.equals(webLogAspectType, WEB_LOG_ASPECT_ALL))) {
            //开始打印请求日志
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            String deviceType = request.getHeader(DEVICETYPE);

            //过滤掉HttpServletRequest、HttpServletResponse 类型参数，不参与序列化
            Object[] args = proceedingJoinPoint.getArgs();
            List<Object> collect = Arrays.stream(args).filter(x -> {
                if (x instanceof HttpServletRequest) {
                    return false;
                } else if (x instanceof HttpServletResponse) {
                    return false;
                }
                return true;
            }).collect(Collectors.toList());
            collect.add(0, String.format("deviceType:%s", deviceType));
            String uid = String.format("%s-%s", Thread.currentThread().getId(), UUID.randomUUID());
            // 打印请求相关参数
            log.info("uid:{}========================================== Start ==========================================", uid);
            // 打印请求 url
            log.info("uid:{}==URL            : {}", uid, request.getRequestURL().toString());
//        // 打印 Http method
//        log.info("HTTP Method    : {}", request.getMethod());
//        // 打印调用 controller 的全路径以及执行方法
//        log.info("Class Method   : {}.{}", proceedingJoinPoint.getSignature().getDeclaringTypeName(), proceedingJoinPoint.getSignature().getName());
            // 打印请求的 IP
//        log.info("IP             : {}", request.getRemoteAddr());
            // 打印请求入参
            log.info("uid:{}==Request Args   : {}", uid, new Gson().toJson(collect));
            Long startTime = System.currentTimeMillis();
            Map<String, Object> threadInfo = new HashMap<>();
            threadInfo.put(START_TIME, startTime);
            threadLocal.set(threadInfo);

            //执行方法
            Object result = proceedingJoinPoint.proceed();
            Map<String, Object> threadInfo2 = threadLocal.get();
            // 打印出参
//        log.info("Response Args  : {}", new Gson().toJson(result));
            // 执行耗时
            log.info("uid:{}==Time-Consuming : {} ms", uid, System.currentTimeMillis() - (Long) threadInfo2.get(START_TIME));
            log.info("uid:{}========================================== End ==========================================", uid);

            return result;
        } else {
            //执行方法
            Object result = proceedingJoinPoint.proceed();
            return result;
        }

    }


}
