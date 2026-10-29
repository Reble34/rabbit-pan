package com.rabbit.pan.security;

import com.rabbit.pan.common.Constants;
import com.rabbit.pan.service.RedisService;
import com.rabbit.pan.util.IpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * 接口限流过滤器：基于 Redis 计数器，保护登录/短链/提取码校验等敏感接口
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisService redisService;
    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 敏感接口限流规则：路径模式 -> 每 IP 每分钟最大次数 */
    private static final Map<String, Integer> RULES = Map.of(
            "/api/v1/user/login", Constants.RATE_LIMIT_LOGIN,
            "/api/v1/user/register", Constants.RATE_LIMIT_LOGIN,
            "/api/v1/s/**", Constants.RATE_LIMIT_DEFAULT,
            "/api/v1/share/*/verify", Constants.RATE_LIMIT_DEFAULT);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        for (Map.Entry<String, Integer> rule : RULES.entrySet()) {
            if (matcher.match(rule.getKey(), uri)) {
                String ip = IpUtil.getClientIp(request);
                String key = Constants.RedisKeys.RATE_LIMIT + rule.getKey() + ":" + ip;
                Long count = redisService.increment(key, Duration.ofSeconds(60));
                if (count != null && count > rule.getValue()) {
                    response.setStatus(429);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null}");
                    return;
                }
                break;
            }
        }
        filterChain.doFilter(request, response);
    }
}
