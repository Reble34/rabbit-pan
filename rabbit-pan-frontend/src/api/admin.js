import request from '@/utils/request'

/** 回收站 API */
export const recycleApi = {
  list(params) {
    return request.get('/recycle/list', { params })
  },
  restore(fileIds) {
    return request.post('/recycle/restore', { fileIds })
  },
  deleteForever(fileIds) {
    return request.delete('/recycle/delete', { data: fileIds })
  },
  clear() {
    return request.delete('/recycle/clear')
  }
}

/** 管理后台 API */
export const adminApi = {
  userList(params) {
    return request.get('/admin/user/list', { params })
  },
  createUser(data) {
    return request.post('/admin/user', data)
  },
  updateQuota(data) {
    return request.put('/admin/user/quota', data)
  },
  updateStatus(data) {
    return request.put('/admin/user/status', data)
  },
  deleteUser(id) {
    return request.delete(`/admin/user/${id}`)
  },
  fileSearch(params) {
    return request.get('/admin/file/search', { params })
  },
  deleteFile(id) {
    return request.delete(`/admin/file/${id}`)
  },
  getConfig() {
    return request.get('/admin/config')
  },
  updateConfig(data) {
    return request.put('/admin/config', data)
  },
  statistics() {
    return request.get('/admin/statistics')
  },
  /** 注册口令码 */
  inviteGenerate(count) {
    return request.post('/admin/invite/generate', { count })
  },
  inviteList(params) {
    return request.get('/admin/invite/list', { params })
  },
  inviteRevoke(id) {
    return request.delete(`/admin/invite/${id}`)
  }
}
