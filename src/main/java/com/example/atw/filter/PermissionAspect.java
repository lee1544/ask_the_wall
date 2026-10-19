package com.example.atw.filter;

import com.example.atw.annotation.RequirePermission;
import com.example.atw.exception.ForbiddenException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        Boolean isDevBypass = (Boolean) request.getAttribute("isDevBypass");
        if (Boolean.TRUE.equals(isDevBypass)) {
            return joinPoint.proceed();
        }

        Integer userId = (Integer) request.getAttribute("userId");
//        Integer orgId = (Integer) request.getAttribute("orgId");

        if (userId == null) {
            throw new ForbiddenException("未登录");
        }

        List<String> userPermissions = getUserPermissions(request);

        String requiredPermission = requirePermission.value();
        if (!userPermissions.contains(requiredPermission)) {
            throw new ForbiddenException("没有权限访问");
        }

        return joinPoint.proceed();
    }

    private List<String> getUserPermissions(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) request.getAttribute("userPermissions");

        return permissions != null ? permissions : Collections.emptyList();
    }
}
