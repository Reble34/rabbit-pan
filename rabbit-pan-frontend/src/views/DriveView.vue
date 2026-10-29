<template>
  <div
    class="drive-page rp-page"
    @dragover.prevent="dragOverlay = true"
    @dragleave.prevent="onDragLeave"
    @drop.prevent="onDrop"
  >
    <!-- ===== 工具栏（二级玻璃） ===== -->
    <div class="toolbar glass-2">
      <div class="toolbar-left">
        <el-button type="primary" :icon="FolderAdd" @click="openCreateFolder">新建文件夹</el-button>
        <el-button :icon="Upload" @click="pickFiles">上传</el-button>
        <input ref="fileInputRef" type="file" multiple hidden @change="onFilePicked" />
        <el-button v-if="selected.size > 0" :icon="Share" @click="shareSelected">分享</el-button>
        <el-button v-if="selected.size > 0" :icon="Download" @click="downloadSelected">打包下载</el-button>
        <el-button v-if="selected.size > 0" :icon="Position" @click="openMove">移动到</el-button>
        <el-button v-if="selected.size > 0" :icon="CopyDocument" @click="openCopy">复制到</el-button>
        <el-button v-if="selected.size > 0" type="danger" plain :icon="Delete" @click="deleteSelected">删除</el-button>
      </div>
      <div class="toolbar-right">
        <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 110px" @change="reload">
          <el-option label="图片" value="image" />
          <el-option label="视频" value="video" />
          <el-option label="音频" value="audio" />
          <el-option label="文档" value="doc" />
          <el-option label="PDF" value="pdf" />
          <el-option label="文本" value="txt" />
          <el-option label="Markdown" value="md" />
          <el-option label="压缩包" value="zip" />
        </el-select>
        <el-select v-model="sortField" style="width: 110px" @change="reload">
          <el-option label="修改时间" value="updateTime" />
          <el-option label="创建时间" value="createTime" />
          <el-option label="文件名" value="fileName" />
          <el-option label="大小" value="size" />
        </el-select>
        <el-button :icon="sortOrder === 'desc' ? 'Bottom' : 'Top'" circle @click="toggleSort" />
        <el-button-group class="view-toggle">
          <el-button :type="viewMode === 'list' ? 'primary' : ''" :icon="Grid" @click="viewMode = 'list'" title="列表视图" />
          <el-button :type="viewMode === 'grid' ? 'primary' : ''" :icon="Menu" @click="viewMode = 'grid'" title="网格视图" />
        </el-button-group>
      </div>
    </div>

    <!-- ===== 面包屑 ===== -->
    <div class="breadcrumb glass-2">
      <template v-for="(crumb, idx) in breadcrumbs" :key="crumb.id">
        <span v-if="idx > 0" class="crumb-sep">/</span>
        <a class="crumb" :class="{ current: idx === breadcrumbs.length - 1 }" @click="enterFolder(crumb.id, idx)">
          {{ crumb.name }}
        </a>
      </template>
      <span v-if="searching" class="search-tag">
        搜索"{{ keyword }}" 结果
        <el-button text size="small" @click="clearSearch">清除</el-button>
      </span>
    </div>

    <!-- ===== 文件列表 ===== -->
    <div class="file-area" v-loading="loadingData">
      <!-- 列表视图 -->
      <div v-if="viewMode === 'list'" class="file-table glass-2">
        <div class="table-head">
          <el-checkbox :model-value="allChecked" :indeterminate="indeterminate" @change="toggleAll" />
          <span class="col-name">文件名</span>
          <span class="col-size">大小</span>
          <span class="col-time">修改时间</span>
          <span class="col-actions"></span>
        </div>
        <div v-for="item in records" :key="item.id" class="table-row" :class="{ selected: selected.has(item.id) }"
          @dblclick="onActivate(item)" @click="toggleSelect(item, $event)" @contextmenu.prevent="openContextMenu(item, $event)">
          <el-checkbox :model-value="selected.has(item.id)" @click.stop @change="toggleSelect(item, $event)" />
          <span class="col-name file-name">
            <el-icon :size="20" :color="isDir(item) ? '#0a84ff' : typeMeta(item.fileType).color">
              <component :is="isDir(item) ? 'Folder' : typeMeta(item.fileType).icon" />
            </el-icon>
            <span class="name-text" :title="item.fileName">{{ item.fileName }}</span>
          </span>
          <span class="col-size">{{ isDir(item) ? '-' : formatSize(item.size) }}</span>
          <span class="col-time">{{ formatTime(item.updateTime) }}</span>
          <span class="col-actions" @click.stop>
            <el-button text size="small" :icon="View" title="预览" @click="previewItem(item)" />
            <el-button text size="small" :icon="Share" title="分享" @click="shareItem(item)" />
            <el-button text size="small" :icon="MoreFilled" title="更多" @click="openContextMenu(item, $event)" />
          </span>
        </div>
        <el-empty v-if="!loadingData && records.length === 0" description="空空如也，拖入文件即可上传" />
      </div>

      <!-- 网格视图 -->
      <div v-else class="file-grid">
        <div v-for="item in records" :key="item.id" class="grid-card glass-2 rp-hover-lift"
          :class="{ selected: selected.has(item.id) }"
          @dblclick="onActivate(item)" @click="toggleSelect(item, $event)" @contextmenu.prevent="openContextMenu(item, $event)">
          <div class="grid-icon">
            <el-image v-if="!isDir(item) && item.fileType === 'image'" :src="thumbUrl(item)" fit="cover"
              class="grid-thumb" lazy />
            <el-icon v-else :size="44" :color="isDir(item) ? '#0a84ff' : typeMeta(item.fileType).color">
              <component :is="isDir(item) ? 'Folder' : typeMeta(item.fileType).icon" />
            </el-icon>
          </div>
          <div class="grid-name" :title="item.fileName">{{ item.fileName }}</div>
          <div class="grid-meta">{{ isDir(item) ? '文件夹' : formatSize(item.size) }}</div>
          <div class="grid-actions" @click.stop>
            <el-button text size="small" :icon="View" title="预览" @click="previewItem(item)" />
            <el-button text size="small" :icon="Share" title="分享" @click="shareItem(item)" />
            <el-button text size="small" :icon="Delete" title="删除" @click="deleteItems([item])" />
          </div>
        </div>
        <el-empty v-if="!loadingData && records.length === 0" description="空空如也，拖入文件即可上传" />
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination-row">
        <el-pagination background layout="prev, pager, next, total" :total="total"
          :page-size="pageSize" :current-page="pageNum" @current-change="onPageChange" />
      </div>

      <!-- 底部状态栏 -->
      <div class="status-bar">
        共 {{ total }} 项 · 已选 {{ selected.size }} 项
        <span v-if="userStore.storage"> · 剩余空间 {{ formatSize(userStore.storage.remain) }}</span>
      </div>
    </div>

    <!-- 拖拽上传遮罩 -->
    <div v-if="dragOverlay" class="drag-overlay">
      <div class="drag-box glass-1 breathing">
        <el-icon :size="52" color="var(--rp-primary)"><UploadFilled /></el-icon>
        <p>松开鼠标上传到当前目录</p>
      </div>
    </div>

    <!-- 右键菜单（三级玻璃） -->
    <teleport to="body">
      <div v-if="contextMenu.visible" class="ctx-menu glass-3" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
        <div v-for="action in contextActions" :key="action.key" class="ctx-item" :class="{ danger: action.danger }"
          @click="action.run(); contextMenu.visible = false">
          <el-icon><component :is="action.icon" /></el-icon>{{ action.label }}
        </div>
      </div>
    </teleport>

    <!-- 各类弹窗 -->
    <ShareDialog v-model="shareDialog.visible" :file="shareDialog.file" />
    <PreviewModal v-model="previewModal.visible" :file="previewModal.file" />
    <DirTreeDialog v-model="dirDialog.visible" :title="dirDialog.title" @select="onDirSelected" />

    <el-dialog v-model="renameDialog.visible" title="重命名" width="380px">
      <el-input v-model="renameDialog.name" placeholder="新名称" @keyup.enter="doRename" />
      <template #footer>
        <el-button @click="renameDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="doRename">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** DriveView 网盘主页：目录浏览/搜索/排序/批量操作/拖拽上传/右键菜单/分享/预览 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  FolderAdd, Upload, Share, Download, Position, CopyDocument, Delete,
  View, MoreFilled, Grid, Menu, EditPen, FolderOpened, InfoFilled
} from '@element-plus/icons-vue'
import { fileApi, downloadApi } from '@/api/file'
import { useUploadStore } from '@/stores/upload'
import { useUserStore } from '@/stores'
import { formatSize, formatTime, typeMeta, isDir } from '@/utils/format'
import service, { tokenStore, getMediaToken, downloadFile } from '@/utils/request'
import ShareDialog from '@/components/ShareDialog.vue'
import PreviewModal from '@/components/PreviewModal.vue'
import DirTreeDialog from '@/components/DirTreeDialog.vue'

const route = useRoute()
const router = useRouter()
const uploadStore = useUploadStore()
const userStore = useUserStore()

// ===== 列表状态 =====
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(60)
const loadingData = ref(false)
const currentParentId = ref(0)
const sortField = ref('updateTime')
const sortOrder = ref('desc')
const typeFilter = ref('')
const viewMode = ref(localStorage.getItem('rp_view') || 'list')
const selected = ref(new Set())
const fileInputRef = ref()

watch(viewMode, (v) => localStorage.setItem('rp_view', v))

const keyword = ref(route.query.keyword || '')
const searching = computed(() => !!(route.query.keyword || typeFilter.value))
const recentMode = computed(() => route.name === 'Recent')

const breadcrumbs = ref([{ id: 0, name: '我的网盘' }])

// ===== 数据加载 =====
async function reload(keepSelection = false) {
  loadingData.value = true
  try {
    if (!keepSelection) selected.value = new Set()
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sortField: recentMode.value ? 'updateTime' : sortField.value,
      sortOrder: sortOrder.value,
      keyword: route.query.keyword || undefined,
      type: typeFilter.value || undefined
    }
    if (!recentMode.value && !route.query.keyword) {
      params.parentId = currentParentId.value
    }
    const api = route.query.keyword || typeFilter.value || recentMode.value ? fileApi.search : fileApi.list
    const page = await api(params)
    records.value = page.records || []
    total.value = Number(page.total || 0)
  } finally {
    loadingData.value = false
  }
  userStore.fetchStorage().catch(() => {})
}

function onPageChange(p) {
  pageNum.value = p
  reload()
}

function toggleSort() {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  reload()
}

function clearSearch() {
  typeFilter.value = ''
  router.push({ path: recentMode.value ? '/recent' : '/' })
  pageNum.value = 1
  reload()
}

watch(() => route.query, () => {
  keyword.value = route.query.keyword || ''
  pageNum.value = 1
  if (!recentMode.value) {
    breadcrumbs.value = [{ id: 0, name: '我的网盘' }]
    currentParentId.value = 0
  }
  reload()
})

onMounted(() => {
  reload()
  refreshMediaToken()
})

// ===== 目录导航 =====
function enterFolder(parentId, crumbIndex) {
  if (!isDirById(parentId) && crumbIndex === undefined) return
  if (crumbIndex !== undefined) {
    breadcrumbs.value = breadcrumbs.value.slice(0, crumbIndex + 1)
  } else {
    const item = records.value.find((r) => r.id === parentId)
    if (!item) return
    breadcrumbs.value.push({ id: item.id, name: item.fileName })
  }
  currentParentId.value = breadcrumbs.value[breadcrumbs.value.length - 1].id
  pageNum.value = 1
  reload()
}

function isDirById(id) {
  const item = records.value.find((r) => r.id === id)
  return item ? isDir(item) : false
}

function onActivate(item) {
  if (isDir(item)) {
    if (searching.value || recentMode.value) {
      // 搜索结果中进入文件夹：重置导航栈直达该目录
      breadcrumbs.value = [{ id: 0, name: '我的网盘' }, { id: item.id, name: item.fileName }]
      router.push({ path: '/' })
      currentParentId.value = item.id
      pageNum.value = 1
      reload()
    } else {
      enterFolder(item.id)
    }
  } else {
    previewItem(item)
  }
}

// ===== 选择 =====
const allChecked = computed(() => records.value.length > 0 && records.value.every((r) => selected.value.has(r.id)))
const indeterminate = computed(() => selected.value.size > 0 && !allChecked.value)

function toggleAll(checked) {
  selected.value = checked ? new Set(records.value.map((r) => r.id)) : new Set()
}

function toggleSelect(item, e) {
  const next = new Set(selected.value)
  if (next.has(item.id)) next.delete(item.id)
  else next.add(item.id)
  selected.value = next
}

function selectedItems() {
  return records.value.filter((r) => selected.value.has(r.id))
}

// ===== 上传 =====
function pickFiles() {
  fileInputRef.value.click()
}

function onFilePicked(e) {
  uploadStore.addFiles(e.target.files, currentParentId.value)
  uploadStore.panelVisible = true
  e.target.value = ''
  setTimeout(reload, 1200)
}

const dragOverlay = ref(false)
let dragDepth = 0
function onDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragOverlay.value = false
}
function onDrop(e) {
  dragDepth = 0
  dragOverlay.value = false
  if (e.dataTransfer && e.dataTransfer.files.length) {
    uploadStore.addFiles(e.dataTransfer.files, currentParentId.value)
    uploadStore.panelVisible = true
    setTimeout(reload, 1500)
  }
}

// ===== 新建 / 重命名 =====
const createDialog = reactive({ visible: false })
function openCreateFolder() {
  ElMessageBox.prompt('请输入文件夹名称', '新建文件夹', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空'
  })
    .then(async ({ value }) => {
      await fileApi.createFolder({ parentId: currentParentId.value, fileName: value.trim() })
      ElMessage.success('创建成功')
      reload()
    })
    .catch(() => {})
}

const renameDialog = reactive({ visible: false, id: null, name: '' })
function openRename(item) {
  renameDialog.id = item.id
  renameDialog.name = item.fileName
  renameDialog.visible = true
}
async function doRename() {
  if (!renameDialog.name.trim()) return
  await fileApi.rename({ fileId: renameDialog.id, fileName: renameDialog.name.trim() })
  renameDialog.visible = false
  ElMessage.success('重命名成功')
  reload()
}

// ===== 移动 / 复制 / 删除 =====
const dirDialog = reactive({ visible: false, title: '', mode: 'move' })
function openMove() {
  dirDialog.mode = 'move'
  dirDialog.title = `移动 ${selected.value.size} 项到…`
  dirDialog.visible = true
}
function openCopy() {
  dirDialog.mode = 'copy'
  dirDialog.title = `复制 ${selected.value.size} 项到…`
  dirDialog.visible = true
}
async function onDirSelected(target) {
  const ids = Array.from(selected.value)
  if (dirDialog.mode === 'move') {
    await fileApi.move({ fileIds: ids, targetParentId: target.id })
    ElMessage.success('移动成功')
  } else {
    await fileApi.copy({ fileIds: ids, targetParentId: target.id })
    ElMessage.success('复制成功')
  }
  reload()
}

async function deleteItems(items) {
  const names = items.map((i) => i.fileName).slice(0, 3).join('、')
  await ElMessageBox.confirm(
    `确定将「${names}${items.length > 3 ? ' 等' : ''}」${items.length} 项移入回收站？`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await fileApi.delete(items.map((i) => i.id))
  ElMessage.success('已移入回收站')
  reload()
}
function deleteSelected() {
  deleteItems(selectedItems())
}

// ===== 分享 =====
const shareDialog = reactive({ visible: false, file: null })
function shareItem(item) {
  shareDialog.file = item
  shareDialog.visible = true
}
function shareSelected() {
  if (selected.value.size !== 1) {
    ElMessage.warning('分享一次仅支持一个文件或文件夹')
    return
  }
  shareItem(selectedItems()[0])
}

// ===== 预览 =====
const previewModal = reactive({ visible: false, file: null })
function previewItem(item) {
  if (isDir(item)) {
    enterFolder(item.id)
    return
  }
  previewModal.file = item
  previewModal.visible = true
}

// ===== 下载 =====
// 缩略图：mu= 短时签名令牌（5 分钟有效），不再暴露长效 JWT；令牌由响应式 ref 持有，到期自动续期
const mediaToken = ref('')
async function refreshMediaToken() {
  try {
    mediaToken.value = await getMediaToken()
  } catch (e) {
    /* 令牌获取失败时缩略图静默降级 */
  }
}
function thumbUrl(item) {
  return `/api/v1/preview/image/${item.id}?thumb=1&mu=${encodeURIComponent(mediaToken.value)}`
}

async function downloadSelected() {
  const ids = Array.from(selected.value)
  const { taskId } = await downloadApi.batchZip(ids)
  ElMessage.info('打包任务已创建，正在打包…')
  const timer = setInterval(async () => {
    try {
      const status = await downloadApi.zipStatus(taskId)
      if (status.status === 1) {
        clearInterval(timer)
        await downloadFile(`/download/zip/file/${taskId}`, 'Rabbit网盘打包.zip')
        ElMessage.success('打包完成，开始下载')
      } else if (status.status === 2) {
        clearInterval(timer)
        ElMessage.error('打包失败')
      }
    } catch (e) {
      clearInterval(timer)
    }
  }, 1500)
}

// ===== 右键菜单 =====
const contextMenu = reactive({ visible: false, x: 0, y: 0, item: null })
function openContextMenu(item, e) {
  if (!selected.value.has(item.id)) {
    selected.value = new Set([item.id])
  }
  contextMenu.item = item
  contextMenu.x = Math.min(e.clientX, window.innerWidth - 180)
  contextMenu.y = Math.min(e.clientY, window.innerHeight - 320)
  contextMenu.visible = true
  document.addEventListener('click', closeContextMenu, { once: true })
}
function closeContextMenu() {
  contextMenu.visible = false
}

const contextActions = computed(() => {
  const item = contextMenu.item
  if (!item) return []
  const list = [
    { key: 'open', label: isDir(item) ? '打开' : '预览', icon: 'View', run: () => onActivate(item) },
    { key: 'share', label: '分享', icon: 'Share', run: () => shareItem(item) },
    { key: 'download', label: '下载', icon: 'Download', run: () => (isDir(item) ? downloadItems([item]) : singleDownload(item)) },
    { key: 'rename', label: '重命名', icon: 'EditPen', run: () => openRename(item) },
    { key: 'move', label: '移动到…', icon: 'Position', run: () => { selected.value = new Set([item.id]); openMove() } },
    { key: 'copy', label: '复制到…', icon: 'CopyDocument', run: () => { selected.value = new Set([item.id]); openCopy() } },
    { key: 'detail', label: '详情', icon: 'InfoFilled', run: () => showDetail(item) },
    { key: 'delete', label: '删除', icon: 'Delete', danger: true, run: () => deleteItems([item]) }
  ]
  return list
})

function singleDownload(item) {
  // Blob 下载走 Authorization 头，令牌不进 URL
  downloadFile(`/download/${item.id}`, item.fileName)
}

async function downloadItems(items) {
  const { taskId } = await downloadApi.batchZip(items.map((i) => i.id))
  ElMessage.info('打包任务已创建')
  const timer = setInterval(async () => {
    const status = await downloadApi.zipStatus(taskId).catch(() => null)
    if (!status) return clearInterval(timer)
    if (status.status === 1) {
      clearInterval(timer)
      await downloadFile(`/download/zip/file/${taskId}`, 'Rabbit网盘打包.zip')
    } else if (status.status === 2) {
      clearInterval(timer)
      ElMessage.error('打包失败')
    }
  }, 1500)
}

async function showDetail(item) {
  const detail = await fileApi.detail(item.id)
  ElMessageBox.alert(
    `<div style="line-height:2;font-size:13px">
      名称：${detail.fileName}<br/>
      类型：${detail.fileType || 'folder'}${detail.ext ? ' (.' + detail.ext + ')' : ''}<br/>
      大小：${isDir(detail) ? '-' : formatSize(detail.size)}<br/>
      创建：${formatTime(detail.createTime)}<br/>
      修改：${formatTime(detail.updateTime)}
      ${detail.md5 ? '<br/>MD5：' + detail.md5 : ''}
    </div>`,
    '文件详情',
    { dangerouslyUseHTMLString: true }
  )
}

onBeforeUnmount(() => document.removeEventListener('click', closeContextMenu))
</script>

<style scoped lang="scss">
.drive-page {
  position: relative;
  min-height: calc(100% - 40px);
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-radius: var(--rp-radius-md);
  flex-wrap: wrap;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.view-toggle .el-button {
  padding: 8px 10px;
}

/* 面包屑 */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  margin-top: 12px;
  border-radius: var(--rp-radius-md);
  font-size: 13px;
  flex-wrap: wrap;
}
.crumb {
  cursor: pointer;
  color: var(--rp-text-2);
  &:hover {
    color: var(--rp-primary);
  }
  &.current {
    color: var(--rp-text-1);
    font-weight: 600;
    cursor: default;
  }
}
.crumb-sep {
  color: var(--rp-text-3);
}
.search-tag {
  margin-left: auto;
  color: var(--rp-primary);
  font-size: 12px;
}

/* 列表视图 */
.file-table {
  margin-top: 12px;
  border-radius: var(--rp-radius-md);
  padding: 6px 8px;
  min-height: 200px;
}
.table-head,
.table-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 110px 160px 150px;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
}
.table-head {
  color: var(--rp-text-3);
  font-size: 12px;
  border-bottom: 1px solid var(--rp-glass-border-soft);
}
.table-row {
  border-radius: var(--rp-radius-sm);
  cursor: default;
  transition: background 0.2s var(--rp-ease), transform 0.2s var(--rp-ease);
  &:hover {
    background: rgba(255, 255, 255, 0.14);
  }
  &.selected {
    background: var(--rp-primary-soft);
    outline: 1px solid rgba(10, 132, 255, 0.4);
  }
}
.file-name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  .name-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
  }
}
.col-size,
.col-time {
  color: var(--rp-text-2);
  font-size: 13px;
}
.col-actions {
  display: flex;
  justify-content: flex-end;
}

/* 网格视图 */
.file-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 14px;
}
.grid-card {
  border-radius: var(--rp-radius-md);
  padding: 18px 12px 10px;
  text-align: center;
  cursor: default;
  position: relative;
  &.selected {
    outline: 2px solid var(--rp-primary);
    transform: scale(1.02);
  }
  &:hover .grid-actions {
    opacity: 1;
  }
}
.grid-icon {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.grid-thumb {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  object-fit: cover;
}
.grid-name {
  margin-top: 10px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.grid-meta {
  font-size: 12px;
  color: var(--rp-text-3);
  margin-top: 2px;
}
.grid-actions {
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s var(--rp-ease);
}

.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.status-bar {
  margin-top: 14px;
  font-size: 12px;
  color: var(--rp-text-3);
  padding: 0 4px;
}

/* 拖拽遮罩 */
.drag-overlay {
  position: fixed;
  inset: 0;
  z-index: 1500;
  background: rgba(0, 0, 0, 0.25);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.drag-box {
  width: 320px;
  padding: 40px;
  border-radius: 20px;
  text-align: center;
  color: var(--rp-text-1);
  &.breathing {
    animation: breathing 1.6s infinite var(--rp-ease);
  }
  p {
    margin-top: 12px;
    font-weight: 600;
  }
}

/* 右键菜单 */
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 160px;
  border-radius: var(--rp-radius-md);
  border: 1px solid var(--rp-glass-border-soft);
  box-shadow: var(--rp-glass-shadow);
  padding: 6px;
}
.ctx-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  color: var(--rp-text-1);
  &:hover {
    background: var(--rp-primary-soft);
    color: var(--rp-primary);
  }
  &.danger {
    color: var(--rp-danger);
    &:hover {
      background: rgba(255, 69, 58, 0.12);
      color: var(--rp-danger);
    }
  }
}
</style>
