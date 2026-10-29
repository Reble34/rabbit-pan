package com.rabbit.pan.config;

import com.rabbit.pan.common.Constants;
import com.rabbit.pan.security.JwtAuthenticationFilter;
import com.rabbit.pan.security.RateLimitFilter;
import com.rabbit.pan.security.RequestIdFilter;
import com.rabbit.pan.security.RestAccessDeniedHandler;
import com.rabbit.pan.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 6 配置：无状态 JWT 鉴权
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestIdFilter requestIdFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final AppProperties appProperties;

    /** 无需登录即可访问的接口 */
    private static final String[] PUBLIC_URLS = {
            "/api/v1/user/register",
            "/api/v1/user/login",
            "/api/v1/user/refresh",
            "/api/v1/s/**",
            "/api/v1/share/*/info",
            "/api/v1/share/*/verify",
            "/api/v1/share/*/list",
            "/api/v1/share/*/download/**",
            "/api/v1/share/*/preview/**",
            "/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**", "/favicon.ico", "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/user/avatar/*").permitAll()
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin())
                        // 防 MIME 嗅探：媒体响应按声明类型处理，杜绝 SVG/HTML 内容嗅探执行
                        .contentTypeOptions(Customizer.withDefaults()))
                // 过滤器顺序：requestId -> 限流 -> JWT（锚点必须是 Security 内置过滤器，否则 no registered order）
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 白名单模式：仅当显式配置 rabbit.cors.allowed-origins 时才启用跨域（同源部署无需 CORS）
        List<String> origins = appProperties.getCors().getAllowedOrigins() == null
                ? List.of() : appProperties.getCors().getAllowedOrigins();
        List<String> effective = origins.stream()
                .filter(o -> o != null && !o.isBlank()).toList();
        if (!effective.isEmpty()) {
            config.setAllowedOrigins(effective);
            config.setAllowCredentials(true);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition", "Content-Range"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
