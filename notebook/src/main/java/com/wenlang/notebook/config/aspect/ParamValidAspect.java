package com.wenlang.notebook.config.aspect;

import com.wenlang.notebook.config.ResultCode;
import com.wenlang.notebook.utils.ResponseUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import java.util.List;

/**
 * 参数校验
 */
@Aspect
@Component
public class ParamValidAspect {
    @Around("execution(* com.wenlang.*.*.controller.*.*(..)) && args(..,bindingResult)")
    public Object validateParam(ProceedingJoinPoint pjp, BindingResult bindingResult) throws Throwable {
        Object retVal;
        if (bindingResult.hasErrors()) {
            List<ObjectError> allErrors = bindingResult.getAllErrors();
            retVal = ResponseUtil.result(ResultCode.PARAM_ERROR.getCode(), allErrors.get(0).getDefaultMessage());
        } else {
            retVal = pjp.proceed();
        }
        return retVal;
    }
}
