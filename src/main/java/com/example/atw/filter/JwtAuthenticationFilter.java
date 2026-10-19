package com.example.atw.filter;

import com.example.atw.service.PermissionService;
import com.example.atw.service.UserRoleService;
import com.example.atw.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private JwtTokenBlacklist jwtTokenBlacklist;

    @Autowired
    private UserPermissionCache userPermissionCache;

    @Value("${dev.bypass-token}")
    private String bypassToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (token != null) {
            if (StringUtils.hasText(bypassToken) && bypassToken.equals(token)) {
                request.setAttribute("userId", 1);
                request.setAttribute("orgId", 1);
                request.setAttribute("username", "developer");
                request.setAttribute("isDevBypass", true);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken("developer", null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (jwtTokenUtil.validateToken(token)) {
                // 检查 token 是否已被注销
                if (jwtTokenBlacklist.isInvalid(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String username = jwtTokenUtil.getUsernameFromToken(token);
                Integer userId = jwtTokenUtil.getUserIdFromToken(token);
                Integer orgId = jwtTokenUtil.getOrgIdFromToken(token);

                // 优先从缓存获取权限，缓存未命中时查询数据库并缓存
                List<String> permissions = userPermissionCache.get(userId);
                if (permissions == null) {
                    List<Integer> roleIds = userRoleService.getRoleIdsByUserId(userId);
                    permissions = permissionService.getPermissionCodesByRoleIds(roleIds);
                    userPermissionCache.put(userId, permissions);
                }

                request.setAttribute("userId", userId);
                request.setAttribute("orgId", orgId);
                request.setAttribute("username", username);
                request.setAttribute("userPermissions", permissions);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
