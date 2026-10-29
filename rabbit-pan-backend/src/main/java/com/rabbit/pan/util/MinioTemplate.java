package com.rabbit.pan.util;

import com.rabbit.pan.config.MinioProperties;
import com.rabbit.pan.config.MinioConfig;
import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.Item;
import io.minio.messages.RemoveObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * MinIO 操作模板：统一封装常用对象存储操作，异常转换
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioTemplate {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public String bucket() {
        return properties.getBucket();
    }

    /** 上传对象（已知大小） */
    public void put(String objectKey, InputStream stream, long size, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectKey)
                .stream(stream, size, -1)
                .contentType(contentType)
                .build());
    }

    /** 上传对象（未知大小，流式分片上传） */
    public void putStreamed(String objectKey, InputStream stream, long partSize, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectKey)
                .stream(stream, -1, partSize)
                .contentType(contentType)
                .build());
    }

    /** 获取对象输入流（调用方负责关闭） */
    public InputStream get(String objectKey) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectKey)
                .build());
    }

    /** 按范围获取对象输入流（Range 断点续传/视频拖动） */
    public InputStream getRange(String objectKey, long offset, long length) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectKey)
                .offset(offset)
                .length(length)
                .build());
    }

    /** 对象大小（字节），不存在抛异常 */
    public long statSize(String objectKey) throws Exception {
        return minioClient.statObject(StatObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectKey)
                .build()).size();
    }

    public boolean exists(String objectKey) {
        try {
            statSize(objectKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 服务端复制对象（分片合并单分片场景） */
    public void copy(String srcObjectKey, String dstObjectKey) throws Exception {
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(dstObjectKey)
                .source(CopySource.builder()
                        .bucket(properties.getBucket())
                        .object(srcObjectKey)
                        .build())
                .build());
    }

    /** 合并多个对象为一个（分片合并），按 sources 顺序拼接 */
    public void compose(List<ComposeSource> sources, String dstObjectKey) throws Exception {
        minioClient.composeObject(ComposeObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(dstObjectKey)
                .sources(sources)
                .build());
    }

    /** 删除单个对象（不存在时静默忽略） */
    public void remove(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 对象删除失败: key={}, err={}", objectKey, e.getMessage());
        }
    }

    /** 批量删除对象 */
    public void removeBatch(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        try {
            List<RemoveObject> objects = objectKeys.stream()
                    .map(RemoveObject::new).toList();
            Iterable<io.minio.Result<DeleteError>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(properties.getBucket())
                            .objects(objects)
                            .build());
            // 必须迭代触发实际删除
            results.forEach(r -> {
                try {
                    r.get();
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            log.warn("MinIO 批量删除失败: count={}, err={}", objectKeys.size(), e.getMessage());
        }
    }

    /** 列举指定前缀下所有对象键 */
    public List<String> listByPrefix(String prefix) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        try {
            Iterable<io.minio.Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(properties.getBucket())
                            .prefix(prefix)
                            .build());
            for (io.minio.Result<Item> result : results) {
                keys.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.warn("MinIO 前缀列举失败: prefix={}, err={}", prefix, e.getMessage());
        }
        return keys;
    }
}
