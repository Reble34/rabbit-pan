package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量操作请求（统一批量入口：删除/移动/复制）
 */
@Data
public class BatchDTO {

    @NotEmpty(message = "请选择要操作的文件")
    private List<Long> fileIds;

    /** 操作类型：delete / move / copy */
    private String operation;

    /** 目标目录（move/copy 时必填） */
    private Long targetParentId;
}
