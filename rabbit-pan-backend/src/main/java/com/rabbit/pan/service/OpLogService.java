package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.entity.OpLog;
import com.rabbit.pan.mapper.OpLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 操作日志服务：关键操作全留痕（异步写入，异常不阻塞主流程）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpLogService {

    private final OpLogMapper opLogMapper;

    @Async
    public void record(Long userId, String action, String detail, String ip) {
        try {
            OpLog opLog = new OpLog();
            opLog.setUserId(userId == null ? 0L : userId);
            opLog.setAction(action);
            opLog.setDetail(detail);
            opLog.setIp(ip == null ? "-" : ip);
            opLog.setCreateTime(new Date());
            opLogMapper.insert(opLog);
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    /** 分页查询当前用户日志 */
    public Page<OpLog> page(Long userId, int pageNum, int pageSize) {
        return opLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OpLog>()
                        .eq(OpLog::getUserId, userId)
                        .orderByDesc(OpLog::getCreateTime));
    }
}
