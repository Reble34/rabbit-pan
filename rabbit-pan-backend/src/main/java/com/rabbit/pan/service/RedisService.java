package com.rabbit.pan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 统一封装服务（String 结构 + Hash + Set，值统一 JSON 序列化）
 */
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ==================== String ====================

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void deleteAll(List<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public boolean hasKey(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    public boolean expire(String key, Duration ttl) {
        Boolean ok = redisTemplate.expire(key, ttl);
        return Boolean.TRUE.equals(ok);
    }

    /** 自增并设置首次过期时间，返回自增后的值 */
    public Long increment(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1 && ttl != null) {
            redisTemplate.expire(key, ttl);
        }
        return count;
    }

    // ==================== 对象存取（JSON 序列化） ====================

    public <T> void setObject(String key, T value, Duration ttl) {
        try {
            set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 序列化失败: " + e.getMessage(), e);
        }
    }

    public <T> T getObject(String key, Class<T> clazz) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public <T> T getObject(String key, TypeReference<T> typeRef) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, typeRef);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Hash ====================

    public void putAllHash(String key, Map<String, String> map, Duration ttl) {
        redisTemplate.opsForHash().putAll(key, map);
        if (ttl != null) {
            redisTemplate.expire(key, ttl);
        }
    }

    public Map<Object, Object> getHash(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    // ==================== Set ====================

    public void sAdd(String key, String member, Duration ttl) {
        redisTemplate.opsForSet().add(key, member);
        if (ttl != null) {
            redisTemplate.expire(key, ttl);
        }
    }

    public void sRemove(String key, String member) {
        redisTemplate.opsForSet().remove(key, member);
    }

    public Set<String> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /** 设置 TTL（便捷方法，供 TimeUnit 场景使用） */
    public void expire(String key, long timeout, TimeUnit unit) {
        redisTemplate.expire(key, timeout, unit);
    }
}
