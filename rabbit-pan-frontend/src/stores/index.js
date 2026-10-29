import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { tokenStore } from '@/utils/request'

/** 用户状态：登录态、用户信息、存储用量 */
export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: JSON.parse(localStorage.getItem('user_info') || 'null'),
    storage: null
  }),
  getters: {
    isLoggedIn: () => !!tokenStore.access,
    isAdmin: (state) => state.userInfo && state.userInfo.role === 'ADMIN'
  },
  actions: {
    async login(payload) {
      const data = await authApi.login(payload)
      tokenStore.save(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('user_info', JSON.stringify(data.user))
      return data
    },
    async register(payload) {
      return authApi.register(payload)
    },
    async fetchInfo() {
      this.userInfo = await authApi.info()
      localStorage.setItem('user_info', JSON.stringify(this.userInfo))
    },
    async fetchStorage() {
      this.storage = await authApi.storageUsage()
    },
    async logout() {
      try {
        await authApi.logout(tokenStore.refresh)
      } catch (e) {
        /* 忽略登出接口异常 */
      }
      tokenStore.clear()
      this.userInfo = null
      this.storage = null
    }
  }
})

/** 应用状态：主题 / 侧栏折叠 */
export const useAppStore = defineStore('app', {
  state: () => ({
    dark: localStorage.getItem('rp_dark') === '1',
    sidebarCollapsed: localStorage.getItem('rp_sidebar') === '1'
  }),
  actions: {
    toggleDark() {
      this.dark = !this.dark
      localStorage.setItem('rp_dark', this.dark ? '1' : '0')
      document.documentElement.classList.toggle('dark', this.dark)
    },
    init() {
      document.documentElement.classList.toggle('dark', this.dark)
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem('rp_sidebar', this.sidebarCollapsed ? '1' : '0')
    }
  }
})
