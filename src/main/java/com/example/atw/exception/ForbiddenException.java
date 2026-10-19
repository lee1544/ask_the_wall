package com.example.atw.exception;

import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {
    
    private Integer code;
    
    public ForbiddenException(String message) {
        super(message);
        this.code = 403;
    }
}
