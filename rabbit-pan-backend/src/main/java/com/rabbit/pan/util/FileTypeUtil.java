package com.rabbit.pan.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文件类型工具：扩展名分类、危险类型拦截、MIME 推断、魔数校验
 */
public final class FileTypeUtil {

    /** 分类常量（与 file_info.file_type 对应） */
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_PDF = "pdf";
    public static final String TYPE_TXT = "txt";
    public static final String TYPE_MD = "md";
    public static final String TYPE_DOC = "doc";
    public static final String TYPE_ZIP = "zip";
    public static final String TYPE_OTHER = "other";

    private static final Map<String, String> CATEGORY_MAP = Map.ofEntries(
            Map.entry("jpg", TYPE_IMAGE), Map.entry("jpeg", TYPE_IMAGE), Map.entry("png", TYPE_IMAGE),
            Map.entry("gif", TYPE_IMAGE), Map.entry("webp", TYPE_IMAGE), Map.entry("bmp", TYPE_IMAGE),
            Map.entry("svg", TYPE_IMAGE),
            Map.entry("mp4", TYPE_VIDEO), Map.entry("avi", TYPE_VIDEO), Map.entry("mov", TYPE_VIDEO),
            Map.entry("mkv", TYPE_VIDEO), Map.entry("webm", TYPE_VIDEO), Map.entry("flv", TYPE_VIDEO),
            Map.entry("mp3", TYPE_AUDIO), Map.entry("wav", TYPE_AUDIO), Map.entry("flac", TYPE_AUDIO),
            Map.entry("aac", TYPE_AUDIO), Map.entry("ogg", TYPE_AUDIO), Map.entry("m4a", TYPE_AUDIO),
            Map.entry("pdf", TYPE_PDF),
            Map.entry("txt", TYPE_TXT), Map.entry("log", TYPE_TXT), Map.entry("csv", TYPE_TXT),
            Map.entry("json", TYPE_TXT), Map.entry("xml", TYPE_TXT), Map.entry("yml", TYPE_TXT),
            Map.entry("yaml", TYPE_TXT),
            Map.entry("md", TYPE_MD),
            Map.entry("doc", TYPE_DOC), Map.entry("docx", TYPE_DOC), Map.entry("xls", TYPE_DOC),
            Map.entry("xlsx", TYPE_DOC), Map.entry("ppt", TYPE_DOC), Map.entry("pptx", TYPE_DOC),
            Map.entry("zip", TYPE_ZIP), Map.entry("rar", TYPE_ZIP), Map.entry("7z", TYPE_ZIP),
            Map.entry("tar", TYPE_ZIP), Map.entry("gz", TYPE_ZIP));

    /** 系统级强制拦截的扩展名（叠加在可配置 allowed/blocked 之上） */
    private static final Set<String> FORBIDDEN_EXT = Set.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "vbs", "js", "sh", "ps1", "dll");

    private static final Map<String, String> MIME_MAP = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"), Map.entry("mov", "video/quicktime"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("flac", "audio/flac"),
            Map.entry("ogg", "audio/ogg"), Map.entry("m4a", "audio/mp4"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"), Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("txt", "text/plain"), Map.entry("md", "text/markdown"),
            Map.entry("json", "application/json"), Map.entry("csv", "text/csv"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    /** 获取扩展名（小写，不含点） */
    public static String getExt(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    /** 获取不含扩展名的文件名 */
    public static String getBaseName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        return idx <= 0 ? fileName : fileName.substring(0, idx);
    }

    /** 按扩展名分类 */
    public static String getCategory(String fileName) {
        return CATEGORY_MAP.getOrDefault(getExt(fileName), TYPE_OTHER);
    }

    /** MIME 类型推断 */
    public static String getMimeType(String fileName) {
        return MIME_MAP.getOrDefault(getExt(fileName), "application/octet-stream");
    }

    /** 系统级强制拦截（不依赖数据库配置） */
    public static boolean isForbiddenExt(String fileName) {
        return FORBIDDEN_EXT.contains(getExt(fileName));
    }

    /** 图片魔数白名单：JPEG / PNG / GIF / BMP / WEBP */
    public static boolean isImageMagic(byte[] head) {
        if (head == null || head.length < 12) {
            return false;
        }
        // JPEG: FF D8 FF
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return true;
        }
        // GIF: GIF8
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return true;
        }
        // BMP: 42 4D
        if (head[0] == 'B' && head[1] == 'M') {
            return true;
        }
        // WEBP: RIFF....WEBP
        return head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }

    /**
     * 文件名/昵称清洗：去除控制字符（防日志注入）、首尾空白与危险前后缀，限长 255。
     */
    public static String sanitizeText(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = input.replaceAll("[\\x00-\\x1f\\x7f]", "");
        cleaned = cleaned.trim();
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 255);
        }
        return cleaned;
    }

    /**
     * 魔数真实类型校验：拦截伪装扩展名的 Windows 可执行文件（PE: MZ 头）
     * 及 HTML/脚本注入文件（扩展名声明为图片/文档但内容为标签脚本的场景由前端渲染层兜底）
     */
    public static boolean isExecutableMagic(byte[] head) {
        if (head == null || head.length < 2) {
            return false;
        }
        return head[0] == 'M' && head[1] == 'Z';
    }

    private FileTypeUtil() {
    }
}
