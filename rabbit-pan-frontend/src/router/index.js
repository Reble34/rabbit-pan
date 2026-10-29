import { createRouter, createWebHistory } from 'vue-router'
import { tokenStore } from '@/utils/request'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/share/:shortCode',
    name: 'ShareView',
    component: () => import('@/views/ShareView.vue'),
    meta: { public: true, title: '分享' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    children: [
      { path: '', name: 'Drive', component: () => import('@/views/DriveView.vue'), meta: { title: '我的网盘' } },
      { path: 'recent', name: 'Recent', component: () => import('@/views/DriveView.vue'), meta: { title: '最近文件', recent: true } },
      { path: 'recycle', name: 'Recycle', component: () => import('@/views/RecycleView.vue'), meta: { title: '回收站' } },
      { path: 'shares', name: 'ShareManage', component: () => import('@/views/ShareManageView.vue'), meta: { title: '分享管理' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人中心' } },
      { path: 'admin', name: 'Admin', component: () => import('@/views/AdminView.vue'), meta: { title: '管理后台', admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = `${to.meta.title || ''} · Rabbit 网盘`
  // 公开页面直接放行
  if (to.meta.public) {
    // 已登录访问登录页 => 跳转网盘
    if (to.name === 'Login' && tokenStore.access) {
      return { path: '/' }
    }
    return true
  }
  if (!tokenStore.access) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 管理后台路由同时校验角色（后端 @PreAuthorize 仍是权威防线）
  if (to.meta.admin) {
    let role = null
    try {
      role = JSON.parse(localStorage.getItem('user_info') || 'null')?.role || null
    } catch (e) {
      role = null
    }
    if (role !== 'ADMIN') {
      return { path: '/' }
    }
  }
  return true
})

export default router
