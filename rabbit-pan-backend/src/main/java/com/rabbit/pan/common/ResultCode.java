package com.rabbit.pan.common;

import lombok.Getter;

/**
 * 统一响应码枚举
 * <p>200 成功；4xx 客户端错误；5xx 服务端错误；1xxx 业务错误</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    SERVER_ERROR(500, "服务异常，请稍后重试"),
    RATE_LIMIT(429, "请求过于频繁，请稍后重试"),

    // ===== 业务错误码 =====
    ACCOUNT_LOCKED(1001, "登录失败次数过多，账号已临时锁定"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    NAME_CONFLICT(1003, "同名文件或文件夹已存在"),
    FILE_NOT_FOUND(1004, "文件不存在或已被删除"),
    QUOTA_EXCEEDED(1005, "存储空间不足，请清理后重试"),
    FILE_TYPE_BLOCKED(1006, "该文件类型不允许上传"),
    FILE_TOO_LARGE(1007, "文件大小超出限制"),
    UPLOAD_TASK_INVALID(1008, "上传任务不存在或已过期"),
    SHARE_INVALID(1009, "分享链接不存在或已失效"),
    SHARE_EXPIRED(1010, "分享链接已过期"),
    SHARE_PASSWORD_ERROR(1011, "提取码错误"),
    SHARE_VIEW_LIMIT(1012, "分享链接访问次数已达上限"),
    PASSWORD_ERROR(1013, "账号或密码错误"),
    OLD_PASSWORD_ERROR(1014, "原密码错误"),
    OPERATION_NOT_ALLOWED(1015, "不允许的操作"),
    LOGIN_DISABLED(1016, "账号已被禁用，请联系管理员"),
    INVITE_INVALID(1017, "口令码无效或已被使用"),
    SYSTEM_STORAGE_FULL(1018, "系统存储空间已满，请联系管理员扩容");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
