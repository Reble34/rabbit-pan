package com.rabbit.pan.common;

/**
 * 全局常量（统一维护 Redis Key、默认值等）
 */
public final class Constants {

    private Constants() {
    }

    /** Redis Key 前缀统一管理 */
    public static final class RedisKeys {
        /** 登录态：accessToken -> userId */
        public static final String LOGIN_TOKEN = "login:token:";
        /** 刷新令牌：refreshToken -> userId */
        public static final String REFRESH_TOKEN = "refresh:token:";
        /** 用户全部令牌集合（用于禁用用户/改密后强制下线） */
        public static final String USER_TOKENS = "login:user:";
        /** 登录失败计数：username -> count */
        public static final String LOGIN_FAIL = "login:fail:";
        /** 短链映射：shortCode -> shareId */
        public static final String SHARE_CODE = "share:code:";
        /** 注册口令码缓存：code -> codeId */
        public static final String INVITE_CODE = "invite:code:";
        /** 分享临时访问凭证：tempToken -> shareId */
        public static final String SHARE_TEMP = "share:temp:";
        /** 分片上传任务元数据 hash */
        public static final String UPLOAD_TASK = "upload:task:";
        /** 分片上传已传分片集合 */
        public static final String UPLOAD_CHUNKS = "upload:chunks:";
        /** ZIP 打包任务状态 */
        public static final String ZIP_TASK = "zip:task:";
        /** 接口限流计数 */
        public static final String RATE_LIMIT = "rate:";
        /** 系统配置缓存 */
        public static final String SYS_CONFIG = "sys:config";
        /** 媒体短时令牌限频：userId -> count */
        public static final String MEDIA_TOKEN_RATE = "media:rate:";

        private RedisKeys() {
        }
    }

    /** 文件状态 */
    public static final int FILE_STATUS_NORMAL = 0;
    public static final int FILE_STATUS_RECYCLE = 1;

    /** 用户状态 */
    public static final int USER_STATUS_ENABLED = 1;
    public static final int USER_STATUS_DISABLED = 0;

    /** 用户角色 */
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    /** 分享状态 */
    public static final int SHARE_STATUS_ACTIVE = 1;
    public static final int SHARE_STATUS_REVOKED = 0;
    /** 分享类型：1 文件 2 文件夹 */
    public static final int SHARE_TYPE_FILE = 1;
    public static final int SHARE_TYPE_FOLDER = 2;

    /** 限流阈值：每 IP 每分钟 */
    public static final int RATE_LIMIT_LOGIN = 10;
    public static final int RATE_LIMIT_DEFAULT = 60;
    /** 登录失败锁定阈值与时长 */
    public static final int LOGIN_FAIL_LIMIT = 5;
    public static final int LOGIN_LOCK_MINUTES = 15;

    /** MinIO 内部目录约定 */
    public static final String MINIO_DIR_FILES = "files/";
    public static final String MINIO_DIR_CHUNK = "tmp/chunk/";
    public static final String MINIO_DIR_ZIP = "tmp/zip/";
    public static final String MINIO_DIR_THUMB = "thumbs/";
    public static final String MINIO_DIR_AVATAR = "avatar/";

    private Constants() {
    }
}
