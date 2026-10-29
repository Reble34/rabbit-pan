import request from '@/utils/request'

/** 上传 API：秒传 / 分片 / 合并 / 取消 / 直传 */
export const uploadApi = {
  check(data) {
    return request.post('/upload/check', data, { timeout: 15000 })
  },
  init(data) {
    return request.post('/upload/init', data, { timeout: 15000 })
  },
  resume(uploadId, data) {
    return request.post('/upload/resume', data, {
      params: { uploadId },
      timeout: 15000
    })
  },
  chunk(uploadId, chunkIndex, blob) {
    const fd = new FormData()
    fd.append('file', blob)
    fd.append('uploadId', uploadId)
    fd.append('chunkIndex', String(chunkIndex))
    return request.post('/upload/chunk', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 0
    })
  },
  merge(data) {
    return request.post('/upload/merge', data, { timeout: 120000 })
  },
  cancel(uploadId) {
    return request.delete(`/upload/${uploadId}`)
  },
  single(file, parentDirId) {
    const fd = new FormData()
    fd.append('file', file)
    return request.post('/upload/single', fd, {
      params: { parentDirId },
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 0
    })
  }
}
