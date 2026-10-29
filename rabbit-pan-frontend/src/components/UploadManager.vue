<template>
  <div v-if="uploadStore.panelVisible && uploadStore.items.length" class="upload-panel glass-1">
    <div class="panel-header">
      <span class="panel-title">上传列表</span>
      <span class="panel-summary">
        {{ summaryText }}
      </span>
      <el-button text size="small" @click="uploadStore.panelVisible = false">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>
    <div class="panel-body">
      <div v-for="item in uploadStore.items" :key="item.id" class="upload-item">
        <div class="item-main">
          <div class="item-top">
            <span class="item-name" :title="item.name">{{ item.name }}</span>
            <span class="item-size">{{ formatSize(item.size) }}</span>
          </div>
          <el-progress
            :percentage="item.progress"
            :status="progressStatus(item)"
            :stroke-width="6"
            :show-text="false"
          />
          <div class="item-bottom">
            <span :class="['item-status', item.status]">{{ statusText(item) }}</span>
            <span v-if="item.status === 'uploading'" class="item-speed">
              {{ formatSpeed(item.speed) }} · 剩余 {{ formatEta(item.eta) }}
            </span>
            <span v-else-if="item.error" class="item-error">{{ item.error }}</span>
          </div>
        </div>
        <div class="item-actions">
          <el-button
            v-if="item.status === 'uploading' || item.status === 'hashing' || item.status === 'checking'"
            text size="small" title="暂停" @click="uploadStore.pause(item)">
            <el-icon><VideoPause /></el-icon>
          </el-button>
          <el-button v-if="item.status === 'paused'" text size="small" title="继续"
            @click="uploadStore.resume(item)">
            <el-icon><VideoPlay /></el-icon>
          </el-button>
          <el-button text size="small" title="取消" @click="uploadStore.cancel(item)">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
    <div class="panel-footer">
      <el-button text size="small" @click="uploadStore.clearFinished">清除已完成</el-button>
    </div>
  </div>
</template>

<script setup>
/**
 * UploadManager 上传管理浮窗：实时进度 / 速度 / 剩余时间 / 暂停续传取消
 */
import { computed } from 'vue'
import { useUploadStore } from '@/stores/upload'
import { formatSize, formatSpeed, formatEta } from '@/utils/format'

const uploadStore = useUploadStore()

const summaryText = computed(() => {
  const items = uploadStore.items
  const done = items.filter((i) => i.status === 'done').length
  const active = items.filter((i) => ['uploading', 'hashing', 'checking', 'merging'].includes(i.status)).length
  return `${done}/${items.length} 完成 · ${active} 进行中`
})

function statusText(item) {
  switch (item.status) {
    case 'hashing':
      return '正在计算 MD5…'
    case 'checking':
      return '秒传校验中…'
    case 'uploading':
      return `上传中 ${item.progress}%`
    case 'merging':
      return '合并分片中…'
    case 'done':
      return item.instant ? '秒传完成' : '上传完成'
    case 'paused':
      return '已暂停'
    case 'error':
      return '上传失败'
    default:
      return item.status
  }
}

function progressStatus(item) {
  if (item.status === 'done') return 'success'
  if (item.status === 'error') return 'exception'
  return undefined
}
</script>

<style scoped lang="scss">
.upload-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 360px;
  border-radius: var(--rp-radius-lg);
  z-index: 2000;
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--rp-glass-border-soft);
}
.panel-title {
  font-weight: 700;
  font-size: 14px;
}
.panel-summary {
  flex: 1;
  font-size: 12px;
  color: var(--rp-text-2);
}
.panel-body {
  max-height: 320px;
  overflow-y: auto;
  padding: 6px 14px;
}
.upload-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--rp-glass-border-soft);
  display: flex;
  align-items: center;
  gap: 8px;
  &:last-child {
    border-bottom: none;
  }
}
.item-main {
  flex: 1;
  min-width: 0;
}
.item-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.item-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-size {
  font-size: 12px;
  color: var(--rp-text-3);
  flex-shrink: 0;
}
.item-bottom {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--rp-text-2);
}
.item-status.done {
  color: var(--rp-success);
}
.item-status.error {
  color: var(--rp-danger);
}
.item-error {
  color: var(--rp-danger);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-actions {
  display: flex;
  flex-direction: column;
}
.panel-footer {
  padding: 6px 14px;
  border-top: 1px solid var(--rp-glass-border-soft);
  text-align: right;
}
</style>
