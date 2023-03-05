package com.wenlang.notebook;

import com.wenlang.notebook.utils.EnvUtil;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.wenlang")
@MapperScan({"com.wenlang.**.mapper", "com.wenlang.notebook.*.mapper"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAsync
public class NoteBookApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(NoteBookApplication.class, args);
        EnvUtil.setLoadActiveProfiles(ctx.getEnvironment().getActiveProfiles());
    }

}
