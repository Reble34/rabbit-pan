import request from '@/utils/request'

/** 认证与账号 API */
export const authApi = {
  register(data) {
    return request.post('/user/register', data)
  },
  login(data) {
    return request.post('/user/login', data)
  },
  logout(refreshToken) {
    return request.post('/user/logout', { refreshToken })
  },
  info() {
    return request.get('/user/info')
  },
  changePassword(data) {
    return request.put('/user/password', data)
  },
  updateNickname(nickname) {
    return request.put('/user/nickname', { nickname })
  },
  uploadAvatar(file) {
    const fd = new FormData()
    fd.append('file', file)
    return request.put('/user/avatar', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  logs(pageNum = 1, pageSize = 10) {
    return request.get('/user/logs', { params: { pageNum, pageSize } })
  },
  storageUsage() {
    return request.get('/storage/usage')
  }
}
