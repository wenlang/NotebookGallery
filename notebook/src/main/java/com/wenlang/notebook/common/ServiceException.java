package com.wenlang.notebook.common;


/**
 * 业务异常类
 */
public class ServiceException extends RuntimeException {

    /**
     * @Fields serialVersionUID :
     */
    private static final long serialVersionUID = -9046362194677293871L;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
