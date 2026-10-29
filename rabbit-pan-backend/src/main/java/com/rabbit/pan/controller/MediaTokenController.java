package com.rabbit.pan.controller;

import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.Result;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.security.MediaTokenUtil;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.RedisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 媒体访问短时令牌：预览/下载类 GET 请求无法携带 Authorization 头时，
 * 使用本接口换取 5 分钟有效的签名令牌（mu= 参数），避免在 URL 中暴露长效 JWT。
 */
@Tag(name = "媒体令牌")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaTokenController {

    private final MediaTokenUtil mediaTokenUtil;
    private final RedisService redisService;

    /** 每用户每分钟最多签发次数（防刷） */
    private static final int ISSUE_LIMIT_PER_MINUTE = 30;

    @Operation(summary = "签发媒体短时令牌（5 分钟有效，绑定当前用户）")
    @PostMapping("/token")
    public Result<Map<String, Object>> issue() {
        Long userId = SecurityUtils.currentUserId();
        Long count = redisService.increment(Constants.RedisKeys.MEDIA_TOKEN_RATE + userId,
                Duration.ofSeconds(60));
        if (count != null && count > ISSUE_LIMIT_PER_MINUTE) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "令牌签发过于频繁，请稍后再试");
        }
        String token = mediaTokenUtil.issue(userId);
        return Result.ok(Map.of(
                "mediaToken", token,
                "expiresIn", MediaTokenUtil.TTL_SECONDS));
    }
}
