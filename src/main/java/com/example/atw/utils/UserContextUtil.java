package com.example.atw.utils;

import com.example.atw.exception.UnauthorizedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class UserContextUtil {
    
    public static Integer getCurrentUserId() {
        HttpServletRequest request = getCurrentRequest();
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return userId;
    }
    
    public static Integer getCurrentOrgId() {
        HttpServletRequest request = getCurrentRequest();
        Integer orgId = (Integer) request.getAttribute("orgId");
        if (orgId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return orgId;
    }
    
    public static String getCurrentUsername() {
        HttpServletRequest request = getCurrentRequest();
        String username = (String) request.getAttribute("username");
        if (username == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return username;
    }
    
    public static boolean isDevBypass() {
        HttpServletRequest request = getCurrentRequest();
        Boolean isDevBypass = (Boolean) request.getAttribute("isDevBypass");
        return isDevBypass != null && isDevBypass;
    }
    
    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new UnauthorizedException("无法获取请求上下文");
        }
        return attributes.getRequest();
    }
}
