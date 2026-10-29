package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.entity.InviteCode;
import com.rabbit.pan.mapper.InviteCodeMapper;
import com.rabbit.pan.util.ShortCodeGenerator;
import com.rabbit.pan.util.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 注册口令码服务
 *
 * <p>口令码与 12 位短链同款加密算法：雪花 ID → 盐值混淆（XOR + 循环移位 + 乘法散列）
 * → Base62 定长 12 位，不可枚举、不可逆推。管理员在后台批量生成；注册时校验并原子消费（一码一用）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private static final int MAX_CODE_RETRY = 5;

    private final InviteCodeMapper inviteCodeMapper;
    private final Snowflake snowflake;
    private final ShortCodeGenerator shortCodeGenerator;
    private final RedisService redisService;

    /** 管理员批量生成口令码 */
    @Transactional(rollbackFor = Exception.class)
    public List<InviteCode> generate(Long adminUserId, int count) {
        List<InviteCode> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            InviteCode invite = new InviteCode();
            invite.setCreateUserId(adminUserId);
            invite.setStatus(1);
            invite.setCreateTime(new Date());
            for (int attempt = 0; attempt < MAX_CODE_RETRY; attempt++) {
                invite.setCodeId(snowflake.nextId());
                invite.setCode(shortCodeGenerator.generate(invite.getCodeId()));
                try {
                    inviteCodeMapper.insert(invite);
                    break;
                } catch (DuplicateKeyException e) {
                    if (attempt == MAX_CODE_RETRY - 1) {
                        throw new BizException(ResultCode.SERVER_ERROR, "口令码生成冲突，请重试");
                    }
                    invite.setId(null);
                }
            }
            // 写入 Redis 缓存加速注册校验（永久码不设 TTL）
            if (invite.getExpireTime() != null) {
                long ttl = invite.getExpireTime().getTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    redisService.set(Constants.RedisKeys.INVITE_CODE + invite.getCode(),
                            String.valueOf(invite.getId()), java.time.Duration.ofMillis(ttl));
                }
            } else {
                redisService.set(Constants.RedisKeys.INVITE_CODE + invite.getCode(),
                        String.valueOf(invite.getId()));
            }
            result.add(invite);
        }
        log.info("管理员生成口令码: adminId={}, count={}", adminUserId, count);
        return result;
    }

    /** 口令码分页列表 */
    public Page<InviteCode> page(int pageNum, int pageSize, Integer status) {
        LambdaQueryWrapper<InviteCode> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(InviteCode::getStatus, status);
        }
        wrapper.orderByDesc(InviteCode::getCreateTime);
        return inviteCodeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /** 作废口令码（仅可用状态可作废） */
    public void revoke(Long id) {
        InviteCode invite = inviteCodeMapper.selectById(id);
        if (invite == null) {
            throw new BizException(ResultCode.NOT_FOUND, "口令码不存在");
        }
        if (invite.getStatus() != 1) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "该口令码不是可用状态");
        }
        invite.setStatus(0);
        inviteCodeMapper.updateById(invite);
        redisService.delete(Constants.RedisKeys.INVITE_CODE + invite.getCode());
        log.info("口令码已作废: id={}, code={}", id, invite.getCode());
    }

    /**
     * 注册消费口令码：校验有效后原子占用（一码一用）
     *
     * @throws BizException 口令码无效 / 已过期 / 已被使用
     */
    @Transactional(rollbackFor = Exception.class)
    public void consume(String code, Long userId) {
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.INVITE_INVALID);
        }
        code = code.trim();
        InviteCode invite = inviteCodeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCode, code)
                .eq(InviteCode::getStatus, 1)
                .last("LIMIT 1"));
        if (invite == null
                || (invite.getExpireTime() != null && invite.getExpireTime().before(new Date()))) {
            throw new BizException(ResultCode.INVITE_INVALID);
        }
        // 原子占用：仅当仍为可用状态时更新成功（并发注册同一码只有一个成功）
        int rows = inviteCodeMapper.update(null, new LambdaUpdateWrapper<InviteCode>()
                .eq(InviteCode::getId, invite.getId())
                .eq(InviteCode::getStatus, 1)
                .set(InviteCode::getStatus, 0)
                .set(InviteCode::getUsedBy, userId)
                .set(InviteCode::getUsedTime, new Date()));
        if (rows == 0) {
            throw new BizException(ResultCode.INVITE_INVALID, "口令码已被使用");
        }
        redisService.delete(Constants.RedisKeys.INVITE_CODE + code);
        log.info("口令码已消费: code={}, userId={}", code, userId);
    }
}
