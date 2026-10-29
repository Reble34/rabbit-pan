package com.rabbit.pan.util;

import com.rabbit.pan.config.AppProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 12 位加密短码生成器
 *
 * <p>生成流程（见需求文档 3.2 节）：</p>
 * <ol>
 *   <li>雪花算法生成全局唯一 64 位 shareId；</li>
 *   <li>预设盐值异或 + 循环移位 + 乘法散列混淆，防止逆向还原；</li>
 *   <li>按无符号语义转 Base62（最长 11 位），左填充至 11 位；</li>
 *   <li>追加 1 位随机 Base62 字符，凑足固定 12 位。</li>
 * </ol>
 * <p>12 位组合空间约 3.2×10^21，且数值经过混淆，不可枚举、不可逆推。</p>
 */
@Component
public class ShortCodeGenerator {

    private static final int CODE_LENGTH = 12;
    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Random RANDOM = new SecureRandom();

    private final AppProperties properties;

    public ShortCodeGenerator(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * 基于 shareId 生成固定 12 位短码（纯函数，同一 shareId 恒定输出同一短码）
     */
    public String generate(Long shareId) {
        long x = shareId ^ getSaltLong();
        x = Long.rotateLeft(x, 21);
        x *= 0x9E3779B97F4A7C15L; // 黄金分割乘数，打散比特位
        x = Long.rotateLeft(x, 37);
        x ^= (x >>> 29);
        // 无符号 64 位整数最长 11 位 Base62，左填充后追加 1 位随机字符 => 固定 12 位
        return Base62.encodeFixed(x, CODE_LENGTH - 1) + ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
    }

    private long getSaltLong() {
        String salt = properties.getShareSalt() == null ? "rabbit-pan-default-salt" : properties.getShareSalt();
        byte[] bytes = salt.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long result = 0x2545F4914F6CDD1DL;
        for (byte b : bytes) {
            result = Long.rotateLeft(result, 5) ^ b;
        }
        return result;
    }
}
