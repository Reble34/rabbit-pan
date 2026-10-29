package com.rabbit.pan.security;

import com.rabbit.pan.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 媒体短时签名令牌：替代在 URL 查询参数中携带长效 JWT。
 *
 * <p>格式：<code>{uid}.{exp}.{sig}</code>，sig = HMAC-SHA256(mediaKey, uid + "." + exp)。
 * mediaKey 由 JWT 密钥派生（独立域），令牌有效期 300 秒、绑定签发用户；
 * 泄露窗口远小于 accessToken，且不进入浏览器历史/访问日志的长期凭据。</p>
 */
@Component
public class MediaTokenUtil {

    /** 令牌有效期（秒） */
    public static final long TTL_SECONDS = 300;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final byte[] mediaKey;

    public MediaTokenUtil(AppProperties properties) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(("media-sign:" + properties.getJwt().getSecret())
                            .getBytes(StandardCharsets.UTF_8));
            this.mediaKey = derived;
        } catch (Exception e) {
            throw new IllegalStateException("媒体令牌密钥初始化失败", e);
        }
    }

    /** 签发：uid + 过期时间戳（秒） */
    public String issue(Long userId) {
        long exp = System.currentTimeMillis() / 1000 + TTL_SECONDS;
        String uid = String.valueOf(userId);
        return uid + "." + exp + "." + hmac(uid + "." + exp);
    }

    /**
     * 校验：签名正确且未过期时返回 uid，否则返回 null。
     */
    public Long verify(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        String uid = parts[0];
        String exp = parts[1];
        String sig = parts[2];
        long expAt;
        try {
            expAt = Long.parseLong(exp);
        } catch (NumberFormatException e) {
            return null;
        }
        if (expAt < System.currentTimeMillis() / 1000) {
            return null;
        }
        if (!MessageDigest.isEqual(hmac(uid + "." + exp).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            return Long.parseLong(uid);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(mediaKey, "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}
