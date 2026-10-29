package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 回收站恢复请求
 */
@Data
public class RestoreDTO {

    @NotEmpty(message = "请选择要恢复的文件")
    private List<Long> fileIds;
}
