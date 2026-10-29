import request from '@/utils/request'

/** 分享 API（管理 + 公开访问） */
export const shareApi = {
  create(data) {
    return request.post('/share/create', data)
  },
  list(pageNum = 1, pageSize = 10) {
    return request.get('/share/list', { params: { pageNum, pageSize } })
  },
  revoke(id) {
    return request.delete(`/share/${id}`)
  },
  update(id, data) {
    return request.put(`/share/${id}`, data)
  },
  // ===== 公开访问 =====
  info(shortCode) {
    return request.get(`/share/${shortCode}/info`)
  },
  verify(shortCode, password) {
    return request.post(`/share/${shortCode}/verify`, { password })
  },
  listShared(shortCode, shareToken, dirId = 0) {
    return request.get(`/share/${shortCode}/list`, {
      params: { shareToken, dirId }
    })
  }
}
