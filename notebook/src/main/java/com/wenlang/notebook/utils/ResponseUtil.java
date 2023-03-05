package com.wenlang.notebook.utils;

import com.wenlang.notebook.config.ResultCode;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;


@Data
@ToString
@Slf4j
public class ResponseUtil<T> {

    private int code;

    private String msg;

    private String errDesc;

    private T data;

    public ResponseUtil() {
    }

    private ResponseUtil(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    private ResponseUtil(int code, String msg, String errDesc) {
        this.code = code;
        this.msg = msg;
        this.errDesc = errDesc;
    }

    private ResponseUtil(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static ResponseUtil result(int code, String msg) {
        return new ResponseUtil(code, msg);
    }

    public static ResponseUtil result(ResultCode resultCode) {
        ResponseUtil responseUtil = new ResponseUtil(resultCode.getCode(), resultCode.getMsg());
        return responseUtil;
    }

    public static <T> ResponseUtil<T> result(int code, String msg, T data) {
        ResponseUtil<T> responseUtil = new ResponseUtil<>(code, msg, data);
        return responseUtil;
    }

    public static <T> ResponseUtil<T> result(ResultCode resultCode, T data) {
        ResponseUtil<T> responseUtil = new ResponseUtil<>(resultCode.getCode(), resultCode.getMsg(), data);
        return responseUtil;
    }
}
