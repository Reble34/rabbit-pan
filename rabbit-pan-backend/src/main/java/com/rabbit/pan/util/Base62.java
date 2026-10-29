package com.rabbit.pan.util;

/**
 * Base62 编解码器（字符集：0-9 a-z A-Z）
 *
 * <p>将 64 位无符号整数编码为 Base62 字符串。62^11 ≈ 5.2×10^19 ≥ 2^64，
 * 因此任意 64 位整数最长 11 位字符即可完整表示。</p>
 */
public final class Base62 {

    private static final char[] CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int RADIX = 62;
    private static final int MAX_CHARS_FOR_UINT64 = 11;

    /**
     * 将 64 位整数按无符号语义编码为 Base62 字符串（无填充）
     */
    public static String encode(long value) {
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        long v = value;
        while (v != 0) {
            sb.append(CHARS[(int) Long.remainderUnsigned(v, RADIX)]);
            v = Long.divideUnsigned(v, RADIX);
        }
        return sb.reverse().toString();
    }

    /**
     * 编码并左填充到固定长度（填充字符 '0'，即数值 0）
     */
    public static String encodeFixed(long value, int length) {
        String encoded = encode(value);
        if (encoded.length() > length) {
            throw new IllegalArgumentException("数值超出 " + length + " 位 Base62 可表示范围");
        }
        return "0".repeat(length - encoded.length()) + encoded;
    }

    /**
     * Base62 解码为 64 位整数（按无符号语义）
     */
    public static long decode(String encoded) {
        long result = 0;
        for (int i = 0; i < encoded.length(); i++) {
            int idx = indexOf(encoded.charAt(i));
            if (idx < 0) {
                throw new IllegalArgumentException("非法 Base62 字符: " + encoded.charAt(i));
            }
            result = result * RADIX + idx;
        }
        return result;
    }

    private static int indexOf(char c) {
        for (int i = 0; i < CHARS.length; i++) {
            if (CHARS[i] == c) {
                return i;
            }
        }
        return -1;
    }

    public static int maxCharsForUint64() {
        return MAX_CHARS_FOR_UINT64;
    }

    private Base62() {
    }
}
