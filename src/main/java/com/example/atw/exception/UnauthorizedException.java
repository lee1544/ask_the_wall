package com.example.atw.exception;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {
    
    private Integer code;
    
    public UnauthorizedException(String message) {
        super(message);
        this.code = 401;
    }
}
