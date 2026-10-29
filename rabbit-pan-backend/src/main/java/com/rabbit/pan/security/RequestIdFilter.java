package com.rabbit.pan.security;

import com.rabbit.pan.util.IpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路过滤器：为每个请求生成 requestId 写入 MDC，日志全链路可追踪
 */
@Slf4j
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        log.info("request start: {} {} ip={}", request.getMethod(), request.getRequestURI(),
                IpUtil.getClientIp(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("request end: {} {} status={}", request.getMethod(), request.getRequestURI(),
                    response.getStatus());
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
