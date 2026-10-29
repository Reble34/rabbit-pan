package com.rabbit.pan.security;

import com.rabbit.pan.common.Constants;
import com.rabbit.pan.service.RedisService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>优先从 Authorization: Bearer {token} 取令牌；媒体预览等 GET 场景支持短时签名令牌
 * ?mu={uid}.{exp}.{sig}（由 /media/token 签发，5 分钟有效、绑定用户，避免 URL 暴露长效 JWT）。</p>
 * <p>refreshToken 不允许访问任何业务接口，仅用于 /user/refresh。</p>
 */
@Slf4j
@Component
@Order
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisService redisService;
    private final MediaTokenUtil mediaTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            // 媒体签名令牌回退：mu 参数校验通过后以该用户身份放行（媒体接口无需角色）
            String mediaToken = request.getParameter("mu");
            Long mediaUserId = mediaTokenUtil.verify(mediaToken);
            if (mediaUserId != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                new LoginUser(mediaUserId, null, Constants.ROLE_USER), null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + Constants.ROLE_USER)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            if (jwtUtil.isRefreshToken(claims)) {
                filterChain.doFilter(request, response);
                return;
            }
            // Redis 登录态校验：登出/禁用/改密后令牌立即失效
            String loginUserId = redisService.get(Constants.RedisKeys.LOGIN_TOKEN + token);
            if (loginUserId == null || !loginUserId.equals(claims.getSubject())) {
                filterChain.doFilter(request, response);
                return;
            }
            LoginUser loginUser = new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.getRole())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            // 令牌无效/过期：保持匿名上下文，由授权层返回 401
            log.debug("JWT 校验失败: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    /** 仅接受 Authorization: Bearer；媒体 GET 场景一律改用 mu= 短时签名令牌（见上方分支），
     *  不再支持 ?token= 传 JWT —— 防止长效令牌进入 URL/浏览器历史/访问日志。 */
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
