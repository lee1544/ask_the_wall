package com.example.atw.web;

import com.example.atw.dto.LoginRequest;
import com.example.atw.dto.LoginResponse;
import com.example.atw.dto.UserInfo;
import com.example.atw.filter.JwtTokenBlacklist;
import com.example.atw.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenBlacklist jwtTokenBlacklist;
    
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
    
    @GetMapping("/userinfo")
    public UserInfo getUserInfo(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        return authService.getUserInfo(userId);
    }
    
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            jwtTokenBlacklist.invalidate(token.substring(7));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已登出");
        return result;
    }
}
