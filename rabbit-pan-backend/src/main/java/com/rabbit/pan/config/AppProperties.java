package com.rabbit.pan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务配置项（application.yml 中 rabbit.* 前缀，禁止硬编码）
 */
@Data
@ConfigurationProperties(prefix = "rabbit")
public class AppProperties {

    private Jwt jwt = new Jwt();
    /** 短链混淆盐值 */
    private String shareSalt;
    /** 前端站点地址（用于拼接短链与 302 跳转） */
    private String frontendBaseUrl;
    /** 首次启动初始化管理员密码 */
    private String adminInitPassword;
    /** CORS 允许来源白名单（空 = 仅同源，不发 CORS 头；配置后允许携带凭证） */
    private Cors cors = new Cors();
    private Upload upload = new Upload();
    private Snowflake snowflake = new Snowflake();

    @Data
    public static class Cors {
        /** 允许的跨域来源，逗号分隔；留空表示关闭跨域支持（生产同源部署推荐） */
        private java.util.List<String> allowedOrigins = new java.util.ArrayList<>();
    }

    @Data
    public static class Jwt {
        /** HMAC-SHA256 密钥，长度必须 >= 32 字节 */
        private String secret;
        /** accessToken 有效时长（小时） */
        private long accessExpireHours = 2;
        /** refreshToken 有效时长（天） */
        private long refreshExpireDays = 7;
    }

    @Data
    public static class Upload {
        /** 分片大小（MB），MinIO composeObject 要求除最后一片外 >= 5MB */
        private int chunkSizeMb = 5;
        /** 分片任务有效期（小时） */
        private int chunkExpireHours = 24;
    }

    @Data
    public static class Snowflake {
        private long workerId = 1;
        private long datacenterId = 1;
    }
}
