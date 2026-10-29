package com.rabbit.pan.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 初始化分片上传请求（fileName / fileSize / md5 / parentDirId 继承自秒传校验结构）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UploadInitDTO extends UploadCheckDTO {
}
