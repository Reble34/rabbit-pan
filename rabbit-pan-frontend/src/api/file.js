import request from '@/utils/request'

/** 文件与目录 API */
export const fileApi = {
  list(params) {
    return request.get('/file/list', { params })
  },
  search(params) {
    return request.get('/file/search', { params })
  },
  createFolder(data) {
    return request.post('/file/folder', data)
  },
  rename(data) {
    return request.put('/file/rename', data)
  },
  move(data) {
    return request.put('/file/move', data)
  },
  copy(data) {
    return request.post('/file/copy', data)
  },
  delete(fileIds) {
    return request.delete('/file/delete', { params: { fileIds: fileIds.join(',') } })
  },
  batch(data) {
    return request.post('/file/batch', data)
  },
  detail(id) {
    return request.get(`/file/${id}`)
  },
  /** 目录树懒加载（仅文件夹） */
  async dirChildren(parentId) {
    const page = await request.get('/file/list', {
      params: { parentId, pageNum: 1, pageSize: 500 }
    })
    return (page.records || []).filter((f) => f.isDir).map((f) => ({ id: f.id, label: f.fileName }))
  }
}

/** 下载与打包 API */
export const downloadApi = {
  batchZip(fileIds) {
    return request.post('/download/batch', fileIds, { timeout: 30000 })
  },
  zipStatus(taskId) {
    return request.get(`/download/zip/${taskId}`)
  }
}

/**
 * 预览 URL 拼接（媒体标签使用）：mu= 为 5 分钟有效的短时签名令牌，
 * 不再携带长效 JWT（防令牌进入浏览器历史/访问日志/Referer）
 */
export function previewUrl(kind, id, mediaToken, extra = {}) {
  const qs = new URLSearchParams({ mu: mediaToken || '', ...extra })
  return `/api/v1/preview/${kind}/${id}?${qs.toString()}`
}

/** 下载地址（mu= 短时令牌） */
export function downloadUrl(id, mediaToken) {
  return `/api/v1/download/${id}?mu=${encodeURIComponent(mediaToken)}`
}
