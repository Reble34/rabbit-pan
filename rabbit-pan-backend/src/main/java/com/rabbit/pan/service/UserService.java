package com.rabbit.pan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.entity.OpLog;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.SysUserMapper;
import com.rabbit.pan.util.FileTypeUtil;
import com.rabbit.pan.vo.StorageVO;
import com.rabbit.pan.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 用户服务：个人信息、头像、昵称
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final StorageService storageService;
    private final com.rabbit.pan.util.MinioTemplate minioTemplate;

    /** 构建用户视图对象（含存储用量） */
    public UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setStorageUsed(storageService.usedBytes(user.getId()));
        vo.setStorageTotal(user.getStorageTotal());
        vo.setFileCount(storageService.fileCount(user.getId()));
        vo.setCreateTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(user.getCreateTime()));
        return vo;
    }

    /** 存储使用情况 */
    public StorageVO storageUsage(SysUser user) {
        long used = storageService.usedBytes(user.getId());
        long total = user.getStorageTotal() == null ? 0 : user.getStorageTotal();
        StorageVO vo = new StorageVO();
        vo.setUsed(used);
        vo.setTotal(total);
        vo.setRemain(Math.max(0, total - used));
        vo.setFileCount(storageService.fileCount(user.getId()));
        vo.setPercent(total <= 0 ? 0 : Math.round(used * 10000.0 / total) / 100.0);
        return vo;
    }

    /** 修改昵称 */
    public void updateNickname(Long userId, String nickname) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new com.rabbit.pan.common.BizException(com.rabbit.pan.common.ResultCode.NOT_FOUND);
        }
        user.setNickname(nickname);
        sysUserMapper.updateById(user);
    }

    /** 流式输出用户头像（公开访问：从 MinIO avatar/ 目录取该用户最新对象） */
    public void streamAvatar(Long userId, jakarta.servlet.http.HttpServletResponse response) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getAvatar() == null) {
            response.setStatus(404);
            return;
        }
        // 头像对象键：avatar/{userId}_{ts}.{ext}，取该前缀最新一个
        var keys = minioTemplate.listByPrefix(com.rabbit.pan.common.Constants.MINIO_DIR_AVATAR + userId + "_");
        if (keys.isEmpty()) {
            response.setStatus(404);
            return;
        }
        keys.sort(java.util.Collections.reverseOrder());
        try (var in = minioTemplate.get(keys.get(0))) {
            response.setContentType("image/png");
            // 防 MIME 嗅探：声明类型与内容可能不一致时禁止浏览器猜测
            response.setHeader("X-Content-Type-Options", "nosniff");
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.warn("头像读取失败: userId={}", userId, e);
        }
    }

    /** 上传头像：魔数校验图片真实类型（拒绝伪装与 SVG），2MB 上限，存储到 MinIO 并更新地址 */
    public String updateAvatar(Long userId, MultipartFile file) {
        String ext = FileTypeUtil.getExt(file.getOriginalFilename());
        if (!FileTypeUtil.TYPE_IMAGE.equals(FileTypeUtil.getCategory(file.getOriginalFilename()))
                || "svg".equals(ext)) {
            throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.FILE_TYPE_BLOCKED,
                    "头像仅支持 JPG/PNG/GIF/BMP/WEBP 格式");
        }
        if (file.getSize() > 2L * 1024 * 1024) {
            throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.FILE_TOO_LARGE, "头像不能超过 2MB");
        }
        // 魔数校验：内容必须是真实图片，防止任意文件伪装扩展名
        try (java.io.InputStream headStream = file.getInputStream()) {
            byte[] head = headStream.readNBytes(12);
            if (!FileTypeUtil.isImageMagic(head)) {
                throw new com.rabbit.pan.common.BizException(
                        com.rabbit.pan.common.ResultCode.FILE_TYPE_BLOCKED, "文件内容不是有效图片");
            }
        } catch (com.rabbit.pan.common.BizException e) {
            throw e;
        } catch (Exception e) {
            throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.SERVER_ERROR, "头像读取失败");
        }
        String objectKey = Constants.MINIO_DIR_AVATAR + userId + "_" + System.currentTimeMillis() + "." + ext;
        try {
            minioTemplate.put(objectKey, file.getInputStream(), file.getSize(), FileTypeUtil.getMimeType(ext));
        } catch (Exception e) {
            throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.SERVER_ERROR, "头像上传失败");
        }
        String avatarUrl = "/api/v1/user/avatar/" + userId + "?v=" + System.currentTimeMillis();
        SysUser user = sysUserMapper.selectById(userId);
        user.setAvatar(avatarUrl);
        sysUserMapper.updateById(user);
        log.info("用户头像已更新: userId={}", userId);
        return avatarUrl;
    }
}
