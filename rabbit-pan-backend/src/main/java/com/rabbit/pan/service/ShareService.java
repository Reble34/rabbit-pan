package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.config.AppProperties;
import com.rabbit.pan.dto.ShareCreateDTO;
import com.rabbit.pan.dto.ShareUpdateDTO;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.entity.FileShare;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.FileInfoMapper;
import com.rabbit.pan.mapper.FileShareMapper;
import com.rabbit.pan.mapper.SysUserMapper;
import com.rabbit.pan.util.ShortCodeGenerator;
import com.rabbit.pan.util.Snowflake;
import com.rabbit.pan.vo.FileVO;
import com.rabbit.pan.vo.ShareInfoVO;
import com.rabbit.pan.vo.ShareVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * 分享服务：12 位加密短链的生成、校验与访问控制
 *
 * <p>生成流程：雪花算法 shareId → 盐值混淆（XOR + 循环移位 + 乘法散列）→
 * Base62 定长 12 位 → 唯一索引兜底冲突重试 → Redis 缓存 shortCode→shareId 映射。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    /** 分享临时访问凭证有效期（小时） */
    private static final long TEMP_TOKEN_HOURS = 2;
    private static final int MAX_CODE_RETRY = 5;

    private final FileShareMapper fileShareMapper;
    private final FileInfoMapper fileInfoMapper;
    private final SysUserMapper sysUserMapper;
    private final FileService fileService;
    private final Snowflake snowflake;
    private final ShortCodeGenerator shortCodeGenerator;
    private final RedisService redisService;
    private final AppProperties appProperties;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // ==================== 管理（需登录） ====================

    /** 创建分享：生成 12 位短码并返回短链 */
    @Transactional(rollbackFor = Exception.class)
    public ShareVO create(Long userId, ShareCreateDTO dto) {
        FileInfo file = fileService.getOwned(userId, dto.getFileId());
        if (file.getStatus() != Constants.FILE_STATUS_NORMAL) {
            throw new BizException(ResultCode.FILE_NOT_FOUND);
        }
        FileShare share = new FileShare();
        share.setShareId(snowflake.nextId());
        share.setUserId(userId);
        share.setFileId(file.getId());
        share.setShareType(Boolean.TRUE.equals(file.getIsDir())
                ? Constants.SHARE_TYPE_FOLDER : Constants.SHARE_TYPE_FILE);
        share.setPassword(dto.getPassword() == null || dto.getPassword().isBlank()
                ? null : hashExtractCode(dto.getPassword()));
        share.setExpireTime(dto.getExpireDays() == null || dto.getExpireDays() == 0
                ? null : new Date(System.currentTimeMillis() + dto.getExpireDays() * 86_400_000L));
        share.setMaxView(dto.getMaxView() == null ? -1 : dto.getMaxView());
        share.setViewCount(0);
        share.setCanDownload(Boolean.FALSE.equals(dto.getCanDownload()) ? 0 : 1);
        share.setStatus(Constants.SHARE_STATUS_ACTIVE);
        share.setCreateTime(new Date());
        share.setUpdateTime(new Date());

        // 短码冲突自动重试（唯一索引兜底，12 位空间 3.2e21 实际几乎不可能冲突）
        for (int attempt = 0; attempt < MAX_CODE_RETRY; attempt++) {
            share.setShortCode(shortCodeGenerator.generate(share.getShareId()));
            try {
                fileShareMapper.insert(share);
                break;
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_CODE_RETRY - 1) {
                    throw new BizException(ResultCode.SERVER_ERROR, "短码生成冲突，请重试");
                }
                // 极罕见：换一个新 shareId 再试
                share.setShareId(snowflake.nextId());
                share.setId(null);
            }
        }
        // Redis 缓存映射，TTL 与分享有效期一致
        String codeKey = Constants.RedisKeys.SHARE_CODE + share.getShortCode();
        if (share.getExpireTime() != null) {
            long ttlMillis = share.getExpireTime().getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                redisService.set(codeKey, String.valueOf(share.getShareId()), Duration.ofMillis(ttlMillis));
            }
        } else {
            redisService.set(codeKey, String.valueOf(share.getShareId()));
        }
        log.info("创建分享: userId={}, fileId={}, shortCode={}", userId, file.getId(), share.getShortCode());
        return toShareVO(share, file);
    }

    /** 我的分享列表（分页，附带文件信息） */
    public Page<ShareVO> listMine(Long userId, int pageNum, int pageSize) {
        Page<FileShare> page = fileShareMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FileShare>()
                        .eq(FileShare::getUserId, userId)
                        .orderByDesc(FileShare::getCreateTime));
        Page<ShareVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ShareVO> records = new ArrayList<>();
        for (FileShare share : page.getRecords()) {
            FileInfo file = fileInfoMapper.selectById(share.getFileId());
            records.add(toShareVO(share, file));
        }
        result.setRecords(records);
        return result;
    }

    /** 撤销分享：立即失效，同步删除 Redis 缓存 */
    public void revoke(Long userId, Long shareId) {
        FileShare share = getOwnedShare(userId, shareId);
        fileShareMapper.deleteById(share.getId());
        redisService.delete(Constants.RedisKeys.SHARE_CODE + share.getShortCode());
        log.info("撤销分享: userId={}, shareId={}", userId, shareId);
    }

    /** 更新分享配置（有效期/提取码/权限/访问上限），短码保持不变 */
    public void update(Long userId, Long shareId, ShareUpdateDTO dto) {
        FileShare share = getOwnedShare(userId, shareId);
        if (dto.getExpireDays() != null) {
            share.setExpireTime(dto.getExpireDays() == 0
                    ? null : new Date(System.currentTimeMillis() + dto.getExpireDays() * 86_400_000L));
        }
        if (dto.getPassword() != null) {
            share.setPassword(dto.getPassword().isBlank() ? null : hashExtractCode(dto.getPassword()));
        }
        if (dto.getMaxView() != null) {
            share.setMaxView(dto.getMaxView());
        }
        if (dto.getCanDownload() != null) {
            share.setCanDownload(Boolean.FALSE.equals(dto.getCanDownload()) ? 0 : 1);
        }
        share.setUpdateTime(new Date());
        fileShareMapper.updateById(share);
        // 刷新 Redis TTL
        String codeKey = Constants.RedisKeys.SHARE_CODE + share.getShortCode();
        if (share.getExpireTime() != null) {
            long ttlMillis = share.getExpireTime().getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                redisService.set(codeKey, String.valueOf(share.getShareId()), Duration.ofMillis(ttlMillis));
            }
        }
        log.info("更新分享配置: userId={}, shareId={}", userId, shareId);
    }

    // ==================== 公开访问 ====================

    /** 解析短码 → shareId：优先 Redis，未命中回源 MySQL 并回填缓存 */
    public FileShare resolveByShortCode(String shortCode) {
        if (shortCode == null || shortCode.length() != 12) {
            throw new BizException(ResultCode.SHARE_INVALID);
        }
        String shareIdStr = redisService.get(Constants.RedisKeys.SHARE_CODE + shortCode);
        FileShare share;
        if (shareIdStr != null) {
            share = fileShareMapper.selectOne(new LambdaQueryWrapper<FileShare>()
                    .eq(FileShare::getShareId, Long.parseLong(shareIdStr)));
        } else {
            share = fileShareMapper.selectOne(new LambdaQueryWrapper<FileShare>()
                    .eq(FileShare::getShortCode, shortCode));
            if (share != null && share.getStatus() == Constants.SHARE_STATUS_ACTIVE) {
                String codeKey = Constants.RedisKeys.SHARE_CODE + shortCode;
                if (share.getExpireTime() != null
                        && share.getExpireTime().getTime() > System.currentTimeMillis()) {
                    redisService.set(codeKey, String.valueOf(share.getShareId()),
                            Duration.ofMillis(share.getExpireTime().getTime() - System.currentTimeMillis()));
                }
            }
        }
        if (share == null || share.getStatus() != Constants.SHARE_STATUS_ACTIVE) {
            throw new BizException(ResultCode.SHARE_INVALID);
        }
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            throw new BizException(ResultCode.SHARE_EXPIRED);
        }
        if (share.getMaxView() != null && share.getMaxView() >= 0
                && share.getViewCount() != null && share.getViewCount() >= share.getMaxView()) {
            throw new BizException(ResultCode.SHARE_VIEW_LIMIT);
        }
        return share;
    }

    /** 分享基本信息（公开）：附带访问次数 +1 */
    public ShareInfoVO info(String shortCode) {
        FileShare share = resolveByShortCode(shortCode);
        FileInfo file = fileInfoMapper.selectById(share.getFileId());
        if (file == null) {
            throw new BizException(ResultCode.SHARE_INVALID);
        }
        // 访问次数 +1（原子条件自增：并发下不会越过 maxView 上限）
        LambdaUpdateWrapper<FileShare> inc = new LambdaUpdateWrapper<FileShare>()
                .eq(FileShare::getId, share.getId())
                .setSql("view_count = view_count + 1");
        if (share.getMaxView() != null && share.getMaxView() >= 0) {
            inc.apply("view_count < {0}", share.getMaxView());
        }
        int updated = fileShareMapper.update(null, inc);
        if (updated == 0) {
            throw new BizException(ResultCode.SHARE_VIEW_LIMIT);
        }
        SysUser owner = sysUserMapper.selectById(share.getUserId());
        ShareInfoVO vo = new ShareInfoVO();
        vo.setShortCode(shortCode);
        vo.setFileId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setFileSize(file.getSize());
        vo.setIsDir(file.getIsDir());
        vo.setNeedPassword(share.getPassword() != null && !share.getPassword().isBlank());
        vo.setExpireTime(share.getExpireTime());
        vo.setCanDownload(share.getCanDownload() != null && share.getCanDownload() == 1);
        vo.setOwnerName(owner == null ? "未知用户" : owner.getNickname());
        return vo;
    }

    /** 校验提取码，通过后签发临时访问凭证（2 小时有效） */
    public String verify(String shortCode, String password) {
        FileShare share = resolveByShortCode(shortCode);
        if (share.getPassword() == null) {
            return issueTempToken(share);
        }
        if (password == null || !matchesExtractCode(share, password)) {
            throw new BizException(ResultCode.SHARE_PASSWORD_ERROR);
        }
        return issueTempToken(share);
    }

    /**
     * 提取码校验：BCrypt（新格式，$2 开头）优先；
     * 历史无盐 MD5 兼容校验，通过后透明升级为 BCrypt，逐步完成存量迁移。
     */
    private boolean matchesExtractCode(FileShare share, String raw) {
        String stored = share.getPassword();
        if (stored.startsWith("$2")) {
            return passwordEncoder.matches(raw, stored);
        }
        if (md5Hex(raw).equals(stored)) {
            try {
                FileShare update = new FileShare();
                update.setId(share.getId());
                update.setPassword(hashExtractCode(raw));
                fileShareMapper.updateById(update);
            } catch (Exception e) {
                log.warn("提取码存储升级失败（不影响本次访问）: shareId={}", share.getId());
            }
            return true;
        }
        return false;
    }

    /** 提取码哈希：BCrypt（自带随机盐，抗离线爆破） */
    private String hashExtractCode(String raw) {
        return passwordEncoder.encode(raw);
    }

    private String issueTempToken(FileShare share) {
        String tempToken = UUID.randomUUID().toString().replace("-", "");
        redisService.set(Constants.RedisKeys.SHARE_TEMP + tempToken, String.valueOf(share.getId()),
                Duration.ofHours(TEMP_TOKEN_HOURS));
        return tempToken;
    }

    /** 通过临时凭证还原分享（凭证只指向当前有效分享，仍需二次状态校验） */
    public FileShare resolveByTempToken(String tempToken) {
        if (tempToken == null || tempToken.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED, "请先通过提取码验证");
        }
        String shareRowId = redisService.get(Constants.RedisKeys.SHARE_TEMP + tempToken);
        if (shareRowId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "访问凭证已过期，请重新验证提取码");
        }
        FileShare share = fileShareMapper.selectById(Long.parseLong(shareRowId));
        if (share == null || share.getStatus() != Constants.SHARE_STATUS_ACTIVE
                || (share.getExpireTime() != null && share.getExpireTime().before(new Date()))) {
            throw new BizException(ResultCode.SHARE_INVALID);
        }
        return share;
    }

    /** 分享文件夹浏览：dirId 必须位于分享子树内 */
    public List<FileVO> listSharedFolder(FileShare share, Long dirId) {
        FileInfo root = fileInfoMapper.selectById(share.getFileId());
        if (root == null || !Boolean.TRUE.equals(root.getIsDir())) {
            throw new BizException(ResultCode.SHARE_INVALID);
        }
        Long targetDir = (dirId == null || dirId == 0) ? root.getId() : dirId;
        if (!fileService.inSubtree(root.getId(), targetDir)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        List<FileInfo> children = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, targetDir)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                .orderByDesc(FileInfo::getIsDir)
                .orderByAsc(FileInfo::getFileName));
        List<FileVO> result = new ArrayList<>();
        // 公开分享列表使用脱敏 VO：不下发 MD5（防配合秒传机制做侧信道引用）
        children.forEach(f -> result.add(fileService.toPublicFileVO(f)));
        return result;
    }

    /** 分享文件安全校验：fileId 必须位于分享子树内 */
    public FileInfo getSharedFile(FileShare share, Long fileId) {
        FileInfo root = fileInfoMapper.selectById(share.getFileId());
        if (root == null) {
            throw new BizException(ResultCode.SHARE_INVALID);
        }
        if (!root.getId().equals(fileId) && !fileService.inSubtree(root.getId(), fileId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        FileInfo file = fileInfoMapper.selectById(fileId);
        if (file == null || file.getStatus() != Constants.FILE_STATUS_NORMAL) {
            throw new BizException(ResultCode.FILE_NOT_FOUND);
        }
        return file;
    }

    // ==================== 内部 ====================

    private FileShare getOwnedShare(Long userId, Long shareId) {
        FileShare share = fileShareMapper.selectById(shareId);
        if (share == null || !share.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "分享记录不存在");
        }
        return share;
    }

    private ShareVO toShareVO(FileShare share, FileInfo file) {
        ShareVO vo = new ShareVO();
        vo.setId(share.getId());
        vo.setShareId(share.getShareId());
        vo.setShortCode(share.getShortCode());
        String base = appProperties.getFrontendBaseUrl() == null ? "" : appProperties.getFrontendBaseUrl();
        vo.setShortUrl(base + "/s/" + share.getShortCode());
        vo.setFileId(share.getFileId());
        if (file != null) {
            vo.setFileName(file.getFileName());
            vo.setFileSize(file.getSize());
            vo.setIsDir(file.getIsDir());
        }
        vo.setShareType(share.getShareType());
        vo.setHasPassword(share.getPassword() != null && !share.getPassword().isBlank());
        vo.setMaxView(share.getMaxView());
        vo.setViewCount(share.getViewCount());
        vo.setCanDownload(share.getCanDownload() != null && share.getCanDownload() == 1);
        vo.setExpireTime(share.getExpireTime());
        vo.setStatus(share.getStatus());
        vo.setCreateTime(share.getCreateTime());
        return vo;
    }

    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }
}
