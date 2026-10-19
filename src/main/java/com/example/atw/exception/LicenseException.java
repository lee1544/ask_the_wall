package com.example.atw.exception;

import lombok.Getter;

/**
 * License 授权异常，统一返回 403。
 */
@Getter
public class LicenseException extends RuntimeException {

    private Integer code;

    public LicenseException(String message) {
        super(message);
        this.code = 403;
    }

    public LicenseException(String message, Throwable cause) {
        super(message, cause);
        this.code = 403;
    }
}
