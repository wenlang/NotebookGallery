package com.wenlang.notebook.config.ratelimit.distribute;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface RateLimiter {

    enum Base {
        /**
         * 统一控制。如果你忽略前缀，那么就代表这个限制是针对所有的访问统计的。
         */
        General,
        /**
         * 如果你是根据ip来限制，那么这个前缀就是用户请求的公网ip（注意如果用户请求如果不是直连到API服务器，
         * 中间经过了诸如nginx的代理服务器，则需要配置nginx把用户真实ip包含在请求头中继续往后传）
         * 按IP限制
         */
        IP,
        /**
         * 按用户控制。  如果你是根据用户id来限制，那么这个前缀就是你的用户id
         */
        User
    }

    Base base();

    /**
     * 一般来说这个不用配置，但是如果你的RestController方法映射的URL上带有URL参数，类似@GetMapping("/user/{id}") 这种，这时候这个path配置就很有必要了，本例中，你可以将path配置为path="/user"。
     *
     * @return
     */
    String path() default "";

    /**
     * 限流的时间单位
     *
     * @return
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 单位时间允许访问的次数限制
     *
     * @return
     */
    int permits();
}
