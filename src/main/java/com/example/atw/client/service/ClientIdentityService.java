package com.example.atw.client.service;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
 * 客户端调用身份解析。已登录请求使用 JWT 用户，未登录请求使用固定匿名归属。
 * 不建立独立的游客账号，也不要求额外设备标识。
 */
@Service
public class ClientIdentityService {

    public static final int ANONYMOUS_USER_ID = 0;

    public Integer requireOwnerId(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        return userId == null ? ANONYMOUS_USER_ID : userId;
    }

    public Integer requireAuthenticatedUserId(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            throw new ClientApiException(401, ClientCodes.AUTH_EXPIRED, "该操作需要登录");
        }
        return userId;
    }
}
