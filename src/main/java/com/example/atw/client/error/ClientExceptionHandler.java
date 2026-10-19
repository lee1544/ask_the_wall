package com.example.atw.client.error;

import com.example.atw.exception.ForbiddenException;
import com.example.atw.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.servlet.http.HttpServletRequest;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.example.atw.client.web")
public class ClientExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientExceptionHandler.class);

    @ExceptionHandler(ClientApiException.class)
    public ResponseEntity<ClientErrorResponse> handleClient(ClientApiException e, HttpServletRequest request) {
        String requestId = e.getRequestId() != null ? e.getRequestId() : headerRequestId(request);
        if (e.getStatus() >= 500) {
            log.warn("客户端模型接口失败 - {} {} [{}]: {}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        } else {
            log.info("客户端模型接口请求无效 - {} {} [{}]: {}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        }
        return build(e.getStatus(), requestId, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ClientErrorResponse> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED.value(), headerRequestId(request),
                ClientCodes.AUTH_EXPIRED, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ClientErrorResponse> handleForbidden(ForbiddenException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN.value(), headerRequestId(request),
                "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ClientErrorResponse> handleBadBody(Exception e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST.value(), headerRequestId(request),
                ClientCodes.INVALID_ARGUMENT, "请求参数不合法");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ClientErrorResponse> handleMissingHeader(MissingRequestHeaderException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST.value(), headerRequestId(request),
                ClientCodes.INVALID_ARGUMENT, "缺少请求头 " + e.getHeaderName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ClientErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED.value(), headerRequestId(request),
                ClientCodes.INVALID_ARGUMENT, "请求方法不支持");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ClientErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e,
                                                               HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), headerRequestId(request),
                ClientCodes.INVALID_ARGUMENT, "Content-Type 不支持");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ClientErrorResponse> handleTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE.value(), headerRequestId(request),
                ClientCodes.PAYLOAD_TOO_LARGE, "请求体过大");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ClientErrorResponse> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST.value(), headerRequestId(request),
                ClientCodes.INVALID_ARGUMENT, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ClientErrorResponse> handleOther(Exception e, HttpServletRequest request) {
        log.error("客户端模型接口内部异常 - {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), headerRequestId(request),
                ClientCodes.INTERNAL_ERROR, "服务器内部错误");
    }

    private ResponseEntity<ClientErrorResponse> build(int status, String requestId, String code, String message) {
        return ResponseEntity.status(status)
                .header("Cache-Control", "no-store")
                .body(ClientErrors.of(requestId, code, message));
    }

    private String headerRequestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-ID");
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
