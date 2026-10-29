<template>
  <div class="share-page">
    <LiquidBackground />

    <!-- 顶部条 -->
    <header class="share-topbar glass-1">
      <router-link to="/login" class="brand">
        <svg viewBox="0 0 100 100" width="24" height="24" aria-hidden="true">
          <circle cx="50" cy="58" r="36" fill="#0a84ff" />
          <ellipse cx="32" cy="24" rx="13" ry="19" fill="#0a84ff" />
          <ellipse cx="62" cy="20" rx="11" ry="17" fill="#0a84ff" />
        </svg>
        <span>Rabbit 网盘</span>
      </router-link>
      <span class="share-note">安全分享链接 · 由 Rabbit 网盘提供支持</span>
    </header>

    <div class="share-body" v-loading="loading">
      <!-- 无效 / 过期 / 超限 -->
      <div v-if="state === 'invalid'" class="state-card glass-1">
        <el-icon :size="56" color="var(--rp-danger)"><CircleCloseFilled /></el-icon>
        <h2>{{ invalidTitle }}</h2>
        <p>{{ invalidText }}</p>
        <el-button type="primary" @click="loadInfo">重新加载</el-button>
      </div>

      <!-- 提取码 -->
      <div v-else-if="state === 'password'" class="state-card glass-1">
        <el-icon :size="56" color="var(--rp-primary)"><Lock /></el-icon>
        <h2>提取文件</h2>
        <p>「{{ info.fileName }}」由 {{ info.ownerName }} 分享，请输入提取码</p>
        <div class="pwd-input-row">
          <el-input v-model="password" size="large" maxlength="6" placeholder="提取码（4-6 位）"
            style="width: 240px" @keyup.enter="doVerify" />
          <el-button type="primary" size="large" :loading="verifying" @click="doVerify">提取文件</el-button>
        </div>
      </div>

      <!-- 内容 -->
      <div v-else-if="state === 'ready'" class="content-card glass-1">
        <div class="content-header">
          <el-icon :size="34" :color="isDirItem(info) ? '#0a84ff' : typeMeta(info.fileType || 'other').color">
            <component :is="isDirItem(info) ? 'FolderOpened' : typeMeta(info.fileType || 'other').icon" />
          </el-icon>
          <div class="content-meta">
            <h2 class="file-title">{{ info.fileName }}</h2>
            <p class="file-sub">
              {{ isDirItem(info) ? '文件夹' : formatSize(info.fileSize) }}
              · 来自 {{ info.ownerName }}
              <template v-if="info.expireTime"> · {{ formatTime(info.expireTime) }} 后失效</template>
            </p>
          </div>
          <el-tag v-if="!info.canDownload" type="warning" effect="plain">仅在线预览</el-tag>
        </div>

        <!-- 文件夹面包屑 -->
        <div v-if="isDirItem(info)" class="dir-breadcrumb">
          <a class="crumb" :class="{ current: dirStack.length === 1 }" @click="enterDir(0)">根目录</a>
          <template v-for="(crumb, i) in dirStack.slice(1)" :key="crumb.id">
            <span class="sep">/</span>
            <a class="crumb" :class="{ current: i === dirStack.length - 2 }" @click="enterDirTo(i + 1)">{{ crumb.fileName }}</a>
          </template>
        </div>

        <!-- 文件夹列表 -->
        <div v-if="isDirItem(info)" class="dir-list" v-loading="listLoading">
          <div v-for="item in dirItems" :key="item.id" class="dir-row"
            @dblclick="onActivate(item)">
            <el-icon :size="20" :color="isDir(item) ? '#0a84ff' : typeMeta(item.fileType).color">
              <component :is="isDir(item) ? 'Folder' : typeMeta(item.fileType).icon" />
            </el-icon>
            <span class="dir-name" :title="item.fileName">{{ item.fileName }}</span>
            <span class="dir-size">{{ isDir(item) ? '-' : formatSize(item.size) }}</span>
            <span class="dir-actions">
              <el-button v-if="!isDir(item)" text size="small" :icon="View" @click="previewItem(item)">预览</el-button>
              <el-button v-if="info.canDownload" text size="small" :icon="Download"
                @click="downloadItem(item)">{{ isDir(item) ? '打包下载' : '下载' }}</el-button>
            </span>
          </div>
          <el-empty v-if="!listLoading && dirItems.length === 0" description="该文件夹为空" />
        </div>

        <!-- 单文件操作 -->
        <div v-else class="single-actions">
          <el-button v-if="info.canDownload" type="primary" size="large" :icon="Download" @click="downloadSingle">下载文件</el-button>
          <el-button size="large" :icon="View" @click="previewSingle">在线预览</el-button>
        </div>
      </div>
    </div>

    <PreviewModal v-model="previewModal.visible" :file="previewModal.file"
      :share-token="shareToken" :share-code="shortCode" :can-download="info ? info.canDownload : true" />
  </div>
</template>

<script setup>
/** ShareView 公开分享页：提取码校验 -> 文件夹浏览 / 文件预览下载 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus/es/components/message/index'
import { View, Download } from '@element-plus/icons-vue'
import { shareApi } from '@/api/share'
import { formatSize, formatTime, typeMeta, isDir } from '@/utils/format'
import LiquidBackground from '@/components/LiquidBackground.vue'
import PreviewModal from '@/components/PreviewModal.vue'

const route = useRoute()
const shortCode = computed(() => String(route.params.shortCode || ''))

const loading = ref(false)
const state = ref('loading') // loading | invalid | password | ready
const info = ref(null)
const password = ref('')
const verifying = ref(false)
const shareToken = ref('')
const dirItems = ref([])
const listLoading = ref(false)
const dirStack = ref([])
const previewModal = reactive({ visible: false, file: null })

const invalidTitle = computed(() => {
  if (state.value !== 'invalid') return ''
  return info.value && info.value.reason === 'expired' ? '分享已过期' : '分享不存在或已失效'
})
const invalidText = computed(() =>
  state.value === 'invalid' ? '链接可能已被撤销、过期或访问次数达到上限。' : ''
)

function isDirItem(item) {
  return item && (item.isDir === true || item.isDir === 1)
}

onMounted(loadInfo)

async function loadInfo() {
  loading.value = true
  try {
    const res = await axios.get(`/api/v1/share/${shortCode.value}/info`)
    const body = res.data
    if (body.code === 200) {
      info.value = body.data
      state.value = body.data.needPassword ? 'password' : 'ready'
      shareToken.value = ''
      dirStack.value = []
      if (state.value === 'ready') {
        if (isDirItem(info.value)) await loadDir(0)
        else dirStack.value = []
      }
    } else {
      info.value = { reason: body.code === 1010 ? 'expired' : 'invalid' }
      state.value = 'invalid'
    }
  } catch (e) {
    info.value = { reason: 'invalid' }
    state.value = 'invalid'
  } finally {
    loading.value = false
  }
}

async function doVerify() {
  if (!password.value) {
    ElMessage.warning('请输入提取码')
    return
  }
  verifying.value = true
  try {
    const res = await axios.post(`/api/v1/share/${shortCode.value}/verify`, { password: password.value })
    const body = res.data
    if (body.code === 200) {
      shareToken.value = body.data.shareToken
      state.value = 'ready'
      if (isDirItem(info.value)) await loadDir(0)
    } else {
      ElMessage.error(body.message || '提取码错误')
    }
  } finally {
    verifying.value = false
  }
}

async function loadDir(dirId) {
  listLoading.value = true
  try {
    const res = await axios.get(`/api/v1/share/${shortCode.value}/list`, {
      params: { shareToken: shareToken.value, dirId }
    })
    const body = res.data
    if (body.code === 200) {
      dirItems.value = body.data || []
    } else {
      ElMessage.error(body.message || '加载失败')
    }
  } finally {
    listLoading.value = false
  }
}

function enterDir(dirId) {
  if (dirId === 0) {
    dirStack.value = []
    loadDir(0)
    return
  }
  const item = dirItems.value.find((d) => d.id === dirId)
  if (!item) return
  dirStack.value.push(item)
  loadDir(item.id)
}

function enterDirTo(index) {
  dirStack.value = dirStack.value.slice(0, index + 1)
  loadDir(index === 0 ? 0 : dirStack.value[index].id)
}

function onActivate(item) {
  if (isDir(item)) {
    enterDir(item.id)
  } else {
    previewItem(item)
  }
}

function previewItem(item) {
  previewModal.file = item
  previewModal.visible = true
}

function previewSingle() {
  previewModal.file = {
    id: info.value.fileId,
    fileName: info.value.fileName,
    fileType: guessType(info.value.fileName)
  }
  previewModal.visible = true
}

function guessType(name) {
  const ext = (name.split('.').pop() || '').toLowerCase()
  const map = {
    jpg: 'image', jpeg: 'image', png: 'image', gif: 'image', webp: 'image', bmp: 'image',
    mp4: 'video', webm: 'video', mov: 'video',
    mp3: 'audio', wav: 'audio', flac: 'audio', ogg: 'audio', m4a: 'audio',
    pdf: 'pdf', txt: 'txt', md: 'md'
  }
  return map[ext] || 'other'
}

function shareDownloadUrl(fileId) {
  return `/api/v1/share/${shortCode.value}/download/${fileId}?shareToken=${encodeURIComponent(shareToken.value)}`
}

function downloadItem(item) {
  if (isDir(item)) {
    ElMessage.info('文件夹打包下载需要登录后操作，单文件可直接下载')
    return
  }
  const a = document.createElement('a')
  a.href = shareDownloadUrl(item.id)
  a.download = item.fileName
  a.click()
}

function downloadSingle() {
  if (info.value && info.value.fileId) {
    const a = document.createElement('a')
    a.href = shareDownloadUrl(info.value.fileId)
    a.download = info.value.fileName
    a.click()
  } else {
    ElMessage.warning('请从列表中选择文件下载')
  }
}
</script>

<style scoped lang="scss">
.share-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.share-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 20px;
  border-radius: 0;
  border-left: none;
  border-right: none;
  border-top: none;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  color: var(--rp-text-1);
  text-decoration: none;
}
.share-note {
  font-size: 12px;
  color: var(--rp-text-3);
}
.share-body {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 8vh 16px 40px;
}
.state-card,
.content-card {
  width: min(720px, 94vw);
  border-radius: 20px;
  padding: 36px 40px;
  text-align: center;
}
.state-card {
  h2 {
    margin: 14px 0 6px;
    color: var(--rp-text-1);
  }
  p {
    color: var(--rp-text-2);
    margin-bottom: 18px;
  }
}
.pwd-input-row {
  display: flex;
  justify-content: center;
  gap: 12px;
}
.content-card {
  text-align: left;
}
.content-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--rp-glass-border-soft);
}
.content-meta {
  flex: 1;
  min-width: 0;
}
.file-title {
  margin: 0 0 4px;
  font-size: 18px;
  word-break: break-all;
  color: var(--rp-text-1);
}
.file-sub {
  margin: 0;
  color: var(--rp-text-2);
  font-size: 13px;
}
.dir-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 14px 0 6px;
  font-size: 13px;
  flex-wrap: wrap;
  .crumb {
    color: var(--rp-text-2);
    cursor: pointer;
    &:hover {
      color: var(--rp-primary);
    }
    &.current {
      color: var(--rp-text-1);
      font-weight: 600;
    }
  }
  .sep {
    color: var(--rp-text-3);
  }
}
.dir-list {
  margin-top: 8px;
  max-height: 52vh;
  overflow-y: auto;
}
.dir-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: var(--rp-radius-sm);
  &:hover {
    background: rgba(255, 255, 255, 0.14);
  }
}
.dir-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dir-size {
  color: var(--rp-text-3);
  font-size: 12px;
  width: 90px;
  text-align: right;
}
.dir-actions {
  width: 170px;
  display: flex;
  justify-content: flex-end;
}
.single-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding-top: 22px;
}
</style>
