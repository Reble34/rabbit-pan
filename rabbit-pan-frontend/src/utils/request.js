import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 统一 axios 实例：baseURL /api/v1，自动携带 Bearer Token，401 自动用 refreshToken 续期重放 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 60000
})

// ============ Token 存取 ============
export const tokenStore = {
  get access() {
    return localStorage.getItem('access_token') || ''
  },
  get refresh() {
    return localStorage.getItem('refresh_token') || ''
  },
  save(access, refresh) {
    if (access) localStorage.setItem('access_token', access)
    if (refresh) localStorage.setItem('refresh_token', refresh)
  },
  clear() {
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('user_info')
  }
}

// ============ 刷新队列：并发 401 只触发一次 refresh ============
let refreshingPromise = null

async function doRefresh() {
  const refreshToken = tokenStore.refresh
  if (!refreshToken) {
    throw new Error('no refresh token')
  }
  const { data } = await axios.post(
    (import.meta.env.VITE_API_BASE || '/api/v1') + '/user/refresh',
    { refreshToken },
    { timeout: 10000 }
  )
  if (data.code !== 200 || !data.data || !data.data.accessToken) {
    throw new Error(data.message || 'refresh failed')
  }
  tokenStore.save(data.data.accessToken, data.data.refreshToken)
  return data.data.accessToken
}

function redirectToLogin() {
  tokenStore.clear()
  if (!location.hash.startsWith('#/login') && !location.pathname.startsWith('/login')) {
    ElMessage.warning('登录已过期，请重新登录')
    setTimeout(() => {
      location.href = '/login'
    }, 600)
  }
}

// ============ 媒体短时令牌（避免在 URL 中暴露长效 JWT） ============
let mediaTokenCache = { token: '', expiresAt: 0, promise: null }

export function getMediaToken(force = false) {
  // 提前 30 秒视为过期，避免临界失效
  if (!force && mediaTokenCache.token && Date.now() < mediaTokenCache.expiresAt - 30000) {
    return Promise.resolve(mediaTokenCache.token)
  }
  if (mediaTokenCache.promise) {
    return mediaTokenCache.promise
  }
  mediaTokenCache.promise = service
    .post('/media/token')
    .then((data) => {
      mediaTokenCache = {
        token: data.mediaToken,
        expiresAt: Date.now() + (data.expiresIn || 300) * 1000,
        promise: null
      }
      return mediaTokenCache.token
    })
    .catch((e) => {
      mediaTokenCache.promise = null
      throw e
    })
  return mediaTokenCache.promise
}

// ============ 请求拦截器 ============
service.interceptors.request.use((config) => {
  const token = tokenStore.access
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// ============ 响应拦截器 ============
service.interceptors.response.use(
  async (response) => {
    // Blob 响应（文件下载/预览）：直接返回完整 response
    if (response.config.responseType === 'blob') {
      // 后端业务错误也可能以 JSON 返回
      if (
        response.data &&
        response.data.type === 'application/json' &&
        response.data.size < 4096
      ) {
        const text = await response.data.text()
        const body = JSON.parse(text)
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(body)
      }
      return response
    }
    const body = response.data
    if (body.code === 200) {
      return body.data === undefined ? body : body.data
    }
    if (body.code === 401) {
      // 尝试刷新令牌后重放原请求
      if (!refreshingPromise) {
        refreshingPromise = doRefresh().finally(() => {
          refreshingPromise = null
        })
      }
      try {
        await refreshingPromise
        return service(response.config)
      } catch (e) {
        redirectToLogin()
        return Promise.reject(e)
      }
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(body)
  },
  (error) => {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('网络超时，请检查网络连接')
    } else if (!error.response) {
      ElMessage.error('网络异常，请检查网络连接')
    } else {
      const status = error.response.status
      const message = (error.response.data && error.response.data.message) || '服务异常'
      if (status === 429) {
        ElMessage.warning('请求过于频繁，请稍后重试')
      } else {
        ElMessage.error(`[${status}] ${message}`)
      }
    }
    return Promise.reject(error)
  }
)

/** 下载文件并触发保存（带鉴权） */
export async function downloadFile(url, fileName) {
  const res = await service.get(url, { responseType: 'blob', timeout: 0 })
  const blob = new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName || 'download'
  link.click()
  URL.revokeObjectURL(link.href)
}

export default service
