package com.rabbit.pan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rabbit.pan.entity.FileInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 文件表 Mapper
 *
 * <p>容量统计一律走 SQL 聚合：原先 selectList 后由 JVM 求和，
 * 文件量大时会把整表行数据搬到应用内存，上传配额校验会被放大成 O(n)。</p>
 */
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    /** 单个用户已用容量（字节，仅正常状态文件） */
    @Select("SELECT COALESCE(SUM(size), 0) FROM file_info "
            + "WHERE user_id = #{userId} AND status = 0 AND is_dir = 0")
    long sumSizeByUser(@Param("userId") Long userId);

    /** 全系统已用容量（字节） */
    @Select("SELECT COALESCE(SUM(size), 0) FROM file_info WHERE status = 0 AND is_dir = 0")
    long sumSizeAll();

    /** 按用户分组统计已用容量，用于管理端用户列表（避免逐行查询的 N+1） */
    @Select("SELECT user_id AS userId, COALESCE(SUM(size), 0) AS total FROM file_info "
            + "WHERE status = 0 AND is_dir = 0 GROUP BY user_id")
    List<Map<String, Object>> sumSizeGroupByUser();
}
