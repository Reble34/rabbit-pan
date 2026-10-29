package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.dto.AdminStatusDTO;
import com.rabbit.pan.dto.AdminUserCreateDTO;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.entity.FileShare;
import com.rabbit.pan.entity.OpLog;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.FileInfoMapper;
import com.rabbit.pan.mapper.FileShareMapper;
import com.rabbit.pan.mapper.OpLogMapper;
import com.rabbit.pan.mapper.SysUserMapper;
import com.rabbit.pan.util.MinioTemplate;
import com.rabbit.pan.vo.StatsVO;
import com.rabbit.pan.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台服务：用户管理 / 全局文件检索 / 统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final SysUserMapper sysUserMapper;
    private final FileInfoMapper fileInfoMapper;
    private final FileShareMapper fileShareMapper;
    private final OpLogMapper opLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final ConfigService configService;
    private final RedisService redisService;
    private final AuthService authService;
    private final StorageService storageService;
    private final MinioTemplate minioTemplate;

    /** 用户分页列表（按用户名模糊、状态筛选） */
    public Page<UserVO> userList(int pageNum, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword);
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByAsc(SysUser::getId);
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        // 一次分组聚合拿到本页所有用户的已用容量，避免逐用户查询（N+1）
        Map<Long, Long> usedMap = new HashMap<>();
        for (Map<String, Object> row : fileInfoMapper.sumSizeGroupByUser()) {
            Object uid = row.get("userId");
            Object total = row.get("total");
            if (uid instanceof Number u && total instanceof Number totalBytes) {
                usedMap.put(u.longValue(), totalBytes.longValue());
            }
        }
        Page<UserVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(u -> {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setRole(u.getRole());
            vo.setStatus(u.getStatus());
            vo.setStorageTotal(u.getStorageTotal());
            vo.setStorageUsed(usedMap.getOrDefault(u.getId(), 0L));
            return vo;
        }).toList());
        return result;
    }

    /** 新增用户 */
    public Long createUser(AdminUserCreateDTO dto) {
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname());
        user.setRole(Constants.ROLE_USER);
        user.setStatus(Constants.USER_STATUS_ENABLED);
        long quotaGb = dto.getQuotaGb() == null
                ? configService.getInt(ConfigService.KEY_DEFAULT_QUOTA_GB, 50) : dto.getQuotaGb();
        user.setStorageTotal(quotaGb * 1024L * 1024 * 1024);
        sysUserMapper.insert(user);
        log.info("管理员新增用户: username={}, quota={}GB", dto.getUsername(), quotaGb);
        return user.getId();
    }

    /** 修改配额 */
    public void updateQuota(Long userId, int quotaGb) {
        SysUser user = requireUser(userId);
        user.setStorageTotal(quotaGb * 1024L * 1024 * 1024);
        sysUserMapper.updateById(user);
        log.info("管理员调整配额: userId={} => {}GB", userId, quotaGb);
    }

    /** 启用/禁用用户（禁用后强制下线） */
    public void updateStatus(AdminStatusDTO dto) {
        SysUser user = requireUser(dto.getUserId());
        if (Constants.ROLE_ADMIN.equals(user.getRole())) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "不允许操作管理员账号");
        }
        user.setStatus(dto.getStatus());
        sysUserMapper.updateById(user);
        if (dto.getStatus() == Constants.USER_STATUS_DISABLED) {
            authService.kickUser(dto.getUserId());
        }
        log.info("管理员调整用户状态: userId={}, status={}", dto.getUserId(), dto.getStatus());
    }

    /** 删除用户及全部数据（级联：文件对象、分享、日志） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        SysUser user = requireUser(userId);
        if (Constants.ROLE_ADMIN.equals(user.getRole())) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "不允许删除管理员账号");
        }
        // 物理删除该用户 MinIO 对象（引用计数保护：秒传/复制使同一对象被多用户引用时不连带删除）
        List<FileInfo> userFiles = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId).isNotNull(FileInfo::getStoragePath));
        Set<Long> ownIds = userFiles.stream().map(FileInfo::getId).collect(java.util.stream.Collectors.toSet());
        List<String> objectKeys = new ArrayList<>();
        for (FileInfo f : userFiles) {
            Long refs = fileInfoMapper.selectCount(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getStoragePath, f.getStoragePath())
                    .notIn(FileInfo::getId, ownIds));
            if (refs == 0) {
                objectKeys.add(f.getStoragePath());
            }
        }
        minioTemplate.removeBatch(objectKeys);
        fileInfoMapper.delete(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUserId, userId));
        // 删除分享及 Redis 缓存
        List<FileShare> shares = fileShareMapper.selectList(new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getUserId, userId));
        shares.forEach(s -> redisService.delete(Constants.RedisKeys.SHARE_CODE + s.getShortCode()));
        fileShareMapper.delete(new LambdaQueryWrapper<FileShare>().eq(FileShare::getUserId, userId));
        opLogMapper.delete(new LambdaQueryWrapper<OpLog>().eq(OpLog::getUserId, userId));
        authService.kickUser(userId);
        sysUserMapper.deleteById(userId);
        log.info("管理员删除用户: userId={}, files={}, shares={}", userId, objectKeys.size(), shares.size());
    }

    /** 全局文件检索（跨用户） */
    public Page<FileInfo> globalFileSearch(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FileInfo::getFileName, keyword);
        }
        wrapper.orderByDesc(FileInfo::getUpdateTime);
        return fileInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /** 删除违规文件：物理删除对象 + 记录行（含其所在文件夹子树由管理员另行处理时单独删） */
    public void deleteFile(Long fileId) {
        FileInfo file = fileInfoMapper.selectById(fileId);
        if (file == null) {
            throw new BizException(ResultCode.FILE_NOT_FOUND);
        }
        if (file.getStoragePath() != null) {
            Long refCount = fileInfoMapper.selectCount(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getStoragePath, file.getStoragePath())
                    .ne(FileInfo::getId, fileId));
            if (refCount == 0) {
                minioTemplate.remove(file.getStoragePath());
            }
        }
        fileInfoMapper.deleteById(fileId);
        opLogMapper.insert(buildLog(file.getUserId(), "ADMIN_DELETE_FILE",
                "管理员删除违规文件: " + file.getFileName()));
        log.warn("管理员删除违规文件: fileId={}, ownerId={}", fileId, file.getUserId());
    }

    /** 数据概览统计 */
    public StatsVO statistics() {
        StatsVO vo = new StatsVO();
        vo.setUserCount(sysUserMapper.selectCount(null));
        vo.setFileCount(fileInfoMapper.selectCount(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getIsDir, false)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)));
        // SQL 聚合：不再把全表文件行读入 JVM
        vo.setStorageUsed(fileInfoMapper.sumSizeAll());
        vo.setStorageTotal(storageService.systemTotalBytes());
        Date todayStart = startOfToday();
        vo.setTodayOpCount(opLogMapper.selectCount(new LambdaQueryWrapper<OpLog>()
                .ge(OpLog::getCreateTime, todayStart)));
        vo.setTodayLoginCount(opLogMapper.selectCount(new LambdaQueryWrapper<OpLog>()
                .eq(OpLog::getAction, "LOGIN").ge(OpLog::getCreateTime, todayStart)));
        return vo;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private OpLog buildLog(Long userId, String action, String detail) {
        OpLog opLog = new OpLog();
        opLog.setUserId(userId == null ? 0L : userId);
        opLog.setAction(action);
        opLog.setDetail(detail);
        opLog.setCreateTime(new Date());
        return opLog;
    }

    private Date startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
