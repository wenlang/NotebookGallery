package com.wenlang.notebook.config;

public enum ResultCode {

    LOGIN_TIMEOUT(5001, "登陆超时!"),

    PARAM_ERROR(5002, "参数错误!"),

    UNAUTHORIZED(5003, "没有权限!"),

    NOEXISTUSERS(5006, "登录失败!"),

    EXISTUSERS(5007, "用户名已存在!"),

    RATE_LIMIT(5008, "访问速率受限！"),

    ERROR(5000, "失败！"),

    ILLEGAL_REQUEST(5017, "非法请求！"),

    SUCCESS(2000, "成功！");

    private int code;

    private String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
