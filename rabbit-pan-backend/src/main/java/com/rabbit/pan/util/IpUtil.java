package com.rabbit.pan.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取（兼容 Nginx 反向代理场景）
 *
 * <p>安全约定（配合 deploy/nginx.conf）：Nginx 使用 <code>proxy_set_header X-Real-IP $remote_addr</code>
 * <b>覆盖式</b>注入 X-Real-IP，客户端伪造的同名头会被丢弃，因此 X-Real-IP 可信。</p>
 * <p>X-Forwarded-For 采用追加模式时链首为客户端可控值，<b>不可作为限流/审计依据</b>；
 * 仅在缺失 X-Real-IP 的非代理直连场景下回退读取，且取最右侧一跳。</p>
 */
public final class IpUtil {

    public static String getClientIp(HttpServletRequest request) {
        // 1) 可信优先：Nginx 覆盖式注入，客户端无法伪造
        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        // 2) 回退：X-Forwarded-For 取最右侧一跳（离服务器最近的代理所追加的真实来源）
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            int idx = xff.lastIndexOf(',');
            String candidate = (idx >= 0 ? xff.substring(idx + 1) : xff).trim();
            if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return request.getRemoteAddr();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private IpUtil() {
    }
}
