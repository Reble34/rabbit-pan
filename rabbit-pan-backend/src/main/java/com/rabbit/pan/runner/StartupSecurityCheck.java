package com.rabbit.pan.runner;

import com.rabbit.pan.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 启动安全检查（prod profile 专用）：禁止使用示例/默认密钥与默认口令上线。
 * <p>命中任意检查项时抛出异常终止启动，防止「忘改配置」导致密钥泄露面扩大。</p>
 */
@Slf4j
@Order(-100)
@Component
@RequiredArgsConstructor
public class StartupSecurityCheck implements ApplicationRunner {

    /** 与 application.yml / .env.example 中示例值保持同步 */
    private static final Set<String> FORBIDDEN_JWT_SECRETS = Set.of(
            "change-me-in-production-please-use-32-bytes-at-least");
    private static final Set<String> FORBIDDEN_SHARE_SALTS = Set.of(
            "change-me-too", "aszx21qwedcv", "rabbit-pan-default-salt");
    private static final Set<String> FORBIDDEN_ADMIN_PASSWORDS = Set.of(
            "Admin@123456");

    private final AppProperties appProperties;
    private final org.springframework.core.env.Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        boolean isProd = Set.of(environment.getActiveProfiles()).contains("prod");
        if (!isProd) {
            return;
        }
        String jwtSecret = appProperties.getJwt().getSecret();
        if (jwtSecret == null || jwtSecret.isBlank()
                || FORBIDDEN_JWT_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException(
                    "安全检查失败：生产环境必须通过 JWT_SECRET 注入强随机密钥（openssl rand -hex 32）");
        }
        String salt = appProperties.getShareSalt();
        if (salt == null || salt.isBlank() || FORBIDDEN_SHARE_SALTS.contains(salt)) {
            throw new IllegalStateException(
                    "安全检查失败：生产环境必须通过 SHARE_SALT 注入强随机盐（openssl rand -hex 16）");
        }
        String adminPassword = appProperties.getAdminInitPassword();
        if (adminPassword == null || FORBIDDEN_ADMIN_PASSWORDS.contains(adminPassword)) {
            throw new IllegalStateException(
                    "安全检查失败：生产环境必须通过 ADMIN_PASSWORD 注入非默认管理员口令");
        }
        log.info("启动安全检查通过：密钥/盐值/管理员口令均为自定义值");
    }
}
