<template>
  <div class="main-layout">
    <LiquidBackground />

    <!-- ===== 顶部全局导航（一级玻璃） ===== -->
    <header class="topbar glass-1">
      <div class="topbar-left">
        <button class="collapse-btn" title="折叠侧栏" @click="appStore.toggleSidebar">
          <el-icon><Expand v-if="appStore.sidebarCollapsed" /><Fold v-else /></el-icon>
        </button>
        <router-link to="/" class="logo">
          <svg class="logo-icon" viewBox="0 0 100 100" width="26" height="26" aria-hidden="true">
            <circle cx="50" cy="58" r="36" fill="#0a84ff" />
            <ellipse cx="32" cy="24" rx="13" ry="19" fill="#0a84ff" />
            <ellipse cx="62" cy="20" rx="11" ry="17" fill="#0a84ff" />
            <circle cx="40" cy="55" r="4" fill="#fff" />
            <circle cx="62" cy="55" r="4" fill="#fff" />
          </svg>
          <span class="logo-text">Rabbit 网盘</span>
        </router-link>
        <el-input
          v-model="keyword"
          class="global-search"
          placeholder="搜索我的文件…"
          clearable
          @keyup.enter="goSearch"
          @clear="goSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <div class="topbar-right">
        <span v-if="userStore.storage" class="storage-mini" :title="`已用 ${formatSize(userStore.storage.used)} / ${formatSize(userStore.storage.total)}`">
          <el-progress
            :percentage="Math.min(100, userStore.storage.percent || 0)"
            :stroke-width="6"
            :show-text="false"
            class="storage-bar"
          />
          <span class="storage-text">{{ formatSize(userStore.storage.used) }} / {{ formatSize(userStore.storage.total) }}</span>
        </span>
        <el-tooltip :content="appStore.dark ? '切换浅色' : '切换深色'">
          <button class="icon-btn" @click="appStore.toggleDark()">
            <el-icon><Sunny v-if="appStore.dark" /><Moon v-else /></el-icon>
          </button>
        </el-tooltip>
        <el-dropdown trigger="click" @command="onUserCommand">
          <el-avatar :size="32" class="avatar" :src="userStore.userInfo && userStore.userInfo.avatar
            ? (userStore.userInfo.avatar.startsWith('/api') ? userStore.userInfo.avatar : '') : ''">
            {{ (userStore.userInfo && userStore.userInfo.nickname || 'U').slice(0, 1) }}
          </el-avatar>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="dropdown-user">
                <div class="dropdown-name">{{ userStore.userInfo && userStore.userInfo.nickname }}</div>
                <div class="dropdown-name-sub">{{ userStore.userInfo && userStore.userInfo.username }}</div>
              </div>
              <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
              <el-dropdown-item v-if="userStore.isAdmin" command="admin" :icon="Setting">管理后台</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="layout-body">
      <!-- ===== 左侧侧边栏（一级玻璃） ===== -->
      <aside class="sidebar glass-1" :class="{ collapsed: appStore.sidebarCollapsed }">
        <nav class="side-nav">
          <router-link to="/" class="side-item" title="我的网盘">
            <el-icon><HomeFilled /></el-icon><span class="side-label">我的网盘</span>
          </router-link>
          <router-link to="/recent" class="side-item" title="最近文件">
            <el-icon><Clock /></el-icon><span class="side-label">最近文件</span>
          </router-link>
          <router-link to="/recycle" class="side-item" title="回收站">
            <el-icon><Delete /></el-icon><span class="side-label">回收站</span>
          </router-link>
          <router-link to="/shares" class="side-item" title="分享管理">
            <el-icon><Share /></el-icon><span class="side-label">分享管理</span>
          </router-link>
          <router-link v-if="userStore.isAdmin" to="/admin" class="side-item" title="管理后台">
            <el-icon><Setting /></el-icon><span class="side-label">管理后台</span>
          </router-link>
        </nav>
      </aside>

      <!-- ===== 主内容区 ===== -->
      <main class="main-content">
        <router-view />
      </main>
    </div>

    <!-- 上传浮窗 -->
    <UploadManager />
  </div>
</template>

<script setup>
/** MainLayout：顶部导航 + 侧边栏 + 主内容 + 上传浮窗 */
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Setting, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore, useAppStore } from '@/stores'
import { formatSize } from '@/utils/format'
import LiquidBackground from '@/components/LiquidBackground.vue'
import UploadManager from '@/components/UploadManager.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()
const keyword = ref(route.query.keyword || '')

onMounted(() => {
  userStore.fetchInfo().catch(() => {})
  userStore.fetchStorage().catch(() => {})
})

function goSearch() {
  router.push({ path: '/', query: { keyword: keyword.value || undefined, t: Date.now() } })
}

function onUserCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'admin') router.push('/admin')
  else if (cmd === 'logout') {
    userStore.logout().finally(() => router.push('/login'))
  }
}
</script>

<style scoped lang="scss">
.main-layout {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 顶栏 */
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-left: none;
  border-right: none;
  border-top: none;
  border-radius: 0;
  z-index: 100;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.collapse-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 18px;
  color: var(--rp-text-2);
  padding: 6px;
  border-radius: 8px;
  &:hover {
    background: var(--rp-primary-soft);
  }
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: var(--rp-text-1);
  .logo-text {
    font-weight: 800;
    font-size: 16px;
    letter-spacing: 0.5px;
  }
}
.global-search {
  max-width: 380px;
  flex: 1;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.storage-mini {
  display: flex;
  align-items: center;
  gap: 8px;
  .storage-bar {
    width: 90px;
  }
  .storage-text {
    font-size: 12px;
    color: var(--rp-text-2);
    white-space: nowrap;
  }
}
.icon-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 18px;
  color: var(--rp-text-2);
  padding: 6px;
  border-radius: 50%;
  &:hover {
    background: var(--rp-primary-soft);
  }
}
.avatar {
  cursor: pointer;
  background: linear-gradient(135deg, #0a84ff, #5ac8fa);
  font-weight: 700;
}
.dropdown-user {
  padding: 8px 16px 4px;
  .dropdown-name {
    font-weight: 700;
    color: var(--rp-text-1);
  }
  .dropdown-name-sub {
    font-size: 12px;
    color: var(--rp-text-3);
  }
}

/* 主体 */
.layout-body {
  flex: 1;
  display: flex;
  min-height: 0;
}
.sidebar {
  width: 200px;
  border-top: none;
  border-bottom: none;
  border-left: none;
  border-radius: 0;
  padding: 16px 10px;
  transition: width 0.3s var(--rp-ease);
  overflow: hidden;
  &.collapsed {
    width: 64px;
    .side-label {
      opacity: 0;
      width: 0;
    }
  }
}
.side-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.side-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: var(--rp-radius-sm);
  color: var(--rp-text-2);
  text-decoration: none;
  font-size: 14px;
  position: relative;
  white-space: nowrap;
  transition: all 0.25s var(--rp-ease);
  .side-label {
    transition: opacity 0.2s var(--rp-ease);
    white-space: nowrap;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.14);
    color: var(--rp-text-1);
  }
  &.router-link-active {
    background: var(--rp-primary-soft);
    color: var(--rp-primary);
    font-weight: 600;
    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 22%;
      height: 56%;
      width: 3px;
      border-radius: 3px;
      background: var(--rp-primary);
    }
  }
}
.main-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}
</style>
