package com.example.atw.client.config;

import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.error.ClientErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 客户端接口在进入 Controller 之前抛出的媒体类型/方法错误，统一按客户端错误格式返回。
 */
@Configuration
public class ClientWebMvcConfig implements WebMvcConfigurer {

    private static final String CLIENT_PREFIX = "/api/v1/client/";

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(0, this::resolveClientException);
    }

    private ModelAndView resolveClientException(HttpServletRequest request, HttpServletResponse response,
                                                Object handler, Exception ex) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(CLIENT_PREFIX)) {
            return null;
        }
        int status;
        String message;
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value();
            message = "Content-Type 不支持";
        } else if (ex instanceof HttpRequestMethodNotSupportedException) {
            status = HttpStatus.METHOD_NOT_ALLOWED.value();
            message = "请求方法不支持";
        } else {
            return null;
        }
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            objectMapper.writeValue(response.getWriter(),
                    ClientErrors.of(headerRequestId(request), ClientCodes.INVALID_ARGUMENT, message));
        } catch (IOException e) {
            return null;
        }
        return new ModelAndView();
    }

    private String headerRequestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-ID");
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
