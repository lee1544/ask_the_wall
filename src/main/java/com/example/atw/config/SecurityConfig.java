package com.example.atw.config;

import com.example.atw.exception.ApiResponse;
import com.example.atw.filter.JwtAuthenticationFilter;
import com.example.atw.filter.LicenseFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private LicenseFilter licenseFilter;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * License 校验先于 Spring Security 过滤器链执行，未授权时直接拦截业务接口。
     * 通过 FilterRegistrationBean 控制注册与顺序，避免被自动注册为普通过滤器而重复执行。
     */
    @Bean
    public FilterRegistrationBean<LicenseFilter> licenseFilterRegistration() {
        FilterRegistrationBean<LicenseFilter> registration = new FilterRegistrationBean<>(licenseFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors().and()
                .authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/license/activate", "/api/license/verify").permitAll()
                .anyRequest().authenticated()
                .and()
                // Spring Security 自身的异常不走 GlobalExceptionHandler，这里统一返回 JSON 响应体
                .exceptionHandling()
                .authenticationEntryPoint((request, response, ex) ->
                        writeJson(request, response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录状态已失效"))
                .accessDeniedHandler((request, response, ex) ->
                        writeJson(request, response, HttpServletResponse.SC_FORBIDDEN, "没有权限访问"))
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJson(javax.servlet.http.HttpServletRequest request, HttpServletResponse response,
                           int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (request.getRequestURI().startsWith("/api/v1/client/")) {
            String code = status == HttpServletResponse.SC_UNAUTHORIZED ? "AUTH_EXPIRED" : "FORBIDDEN";
            String requestId = request.getHeader("X-Request-ID");
            response.getWriter().write(objectMapper.writeValueAsString(
                    com.example.atw.client.error.ClientErrors.of(requestId, code, message)));
            return;
        }
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResponse<>(status, message)));
    }
}
