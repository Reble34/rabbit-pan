package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.entity.SysConfig;
import com.rabbit.pan.mapper.SysConfigMapper;
import com.rabbit.pan.vo.ConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务：本地缓存 60s + 管理端修改时主动失效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    public static final String KEY_MAX_FILE_MB = "max_file_mb";
    public static final String KEY_ALLOWED_EXTS = "allowed_exts";
    public static final String KEY_RECYCLE_KEEP_DAYS = "recycle_keep_days";
    public static final String KEY_DEFAULT_QUOTA_GB = "default_quota_gb";
    public static final String KEY_TOTAL_STORAGE_GB = "total_storage_gb";

    private final SysConfigMapper sysConfigMapper;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_MILLIS = 60_000L;

    public String get(String key, String defaultValue) {
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.time < CACHE_MILLIS) {
            return entry.value;
        }
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        String value = config == null ? defaultValue : config.getConfigValue();
        cache.put(key, new CacheEntry(value, now));
        return value;
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public ConfigVO getAll() {
        ConfigVO vo = new ConfigVO();
        vo.setMaxFileMb(getInt(KEY_MAX_FILE_MB, 2048));
        vo.setAllowedExts(get(KEY_ALLOWED_EXTS, ""));
        vo.setRecycleKeepDays(getInt(KEY_RECYCLE_KEEP_DAYS, 30));
        vo.setDefaultQuotaGb(getInt(KEY_DEFAULT_QUOTA_GB, 5));
        vo.setTotalStorageGb(getInt(KEY_TOTAL_STORAGE_GB, 30));
        return vo;
    }

    public void update(ConfigVO vo) {
        upsert(KEY_MAX_FILE_MB, String.valueOf(vo.getMaxFileMb()), "单文件大小上限(MB)");
        upsert(KEY_ALLOWED_EXTS, vo.getAllowedExts() == null ? "" : vo.getAllowedExts(), "允许上传的扩展名");
        upsert(KEY_RECYCLE_KEEP_DAYS, String.valueOf(vo.getRecycleKeepDays()), "回收站保留天数");
        upsert(KEY_DEFAULT_QUOTA_GB, String.valueOf(vo.getDefaultQuotaGb()), "新用户默认配额(GB)");
        upsert(KEY_TOTAL_STORAGE_GB, String.valueOf(vo.getTotalStorageGb()), "系统总存储空间(GB)");
        cache.clear();
        log.info("系统配置已更新: {}", vo);
    }

    private void upsert(String key, String value, String desc) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(desc);
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(value);
            sysConfigMapper.updateById(config);
        }
    }

    private record CacheEntry(String value, long time) {
    }
}
