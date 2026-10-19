package com.example.atw.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        logger.error("业务异常 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException e, HttpServletRequest request) {
        logger.warn("未授权访问 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbiddenException(ForbiddenException e, HttpServletRequest request) {
        logger.warn("禁止访问 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        logger.warn("访问被拒绝 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(403, "没有权限访问");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(LicenseException.class)
    public ResponseEntity<ApiResponse<Object>> handleLicenseException(LicenseException e, HttpServletRequest request) {
        logger.warn("License 校验失败 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NotFoundException e, HttpServletRequest request) {
        logger.warn("资源不存在 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        logger.warn("参数验证失败 - {}: {}", request.getRequestURI(), errors);
        ApiResponse<Object> response = new ApiResponse<>(400, "参数验证失败");
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        logger.warn("参数绑定失败 - {}: {}", request.getRequestURI(), errors);
        ApiResponse<Object> response = new ApiResponse<>(400, "参数绑定失败");
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("非法参数 - {}: {}", request.getRequestURI(), e.getMessage());
        ApiResponse<Object> response = new ApiResponse<>(400, e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        logger.error("运行时异常 - {}: {}", request.getRequestURI(), e.getMessage(), e);
        ApiResponse<Object> response = new ApiResponse<>(500, "服务器内部错误: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e, HttpServletRequest request) {
        logger.error("系统异常 - {}: {}", request.getRequestURI(), e.getMessage(), e);
        ApiResponse<Object> response = new ApiResponse<>(500, "系统异常，请联系管理员");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
