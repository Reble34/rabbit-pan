package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 移动请求（复制请求复用同一结构）
 */
@Data
public class MoveDTO {

    @NotEmpty(message = "请选择要移动的文件")
    private List<Long> fileIds;

    /** 目标父目录 ID，根目录传 0 */
    @NotNull(message = "目标目录不能为空")
    private Long targetParentId;
}
