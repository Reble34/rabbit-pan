package com.rabbit.pan.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.config.AppProperties;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 系统初始化：首次启动时创建管理员账号
 * <p>密码来自环境变量 ADMIN_PASSWORD（默认 Admin@123456），生产环境务必修改。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final com.rabbit.pan.service.ConfigService configService;

    @Override
    public void run(ApplicationArguments args) {
        Long adminCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, Constants.ROLE_ADMIN));
        if (adminCount != null && adminCount > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(appProperties.getAdminInitPassword()));
        admin.setNickname("管理员");
        admin.setRole(Constants.ROLE_ADMIN);
        admin.setStatus(Constants.USER_STATUS_ENABLED);
        // 管理员配额 = 系统总存储空间（默认 30GB，全用户共享上限）
        admin.setStorageTotal(configService.getInt(
                com.rabbit.pan.service.ConfigService.KEY_TOTAL_STORAGE_GB, 30) * 1024L * 1024 * 1024);
        sysUserMapper.insert(admin);
        String masked = maskPassword(appProperties.getAdminInitPassword());
        log.warn("==========================================================");
        log.warn("  已初始化管理员账号: admin / {}", masked);
        log.warn("  请立即登录修改密码；初始密码由环境变量 ADMIN_PASSWORD 注入（日志不回显明文）！");
        log.warn("==========================================================");
    }

    /** 日志脱敏：仅保留首字符提示长度，避免凭据进入日志文件 */
    private String maskPassword(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "(empty)";
        }
        return raw.charAt(0) + "******(长度" + raw.length() + ")";
    }
}
