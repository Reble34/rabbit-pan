package com.rabbit.pan.vo;

import lombok.Data;

import java.util.List;

/**
 * 初始化分片上传响应
 */
@Data
public class UploadInitVO {

    private String uploadId;
    /** 服务端分片大小（字节） */
    private Integer chunkSize;
    /** 已上传分片序号（断点续传） */
    private List<Integer> uploadedChunks;
}
