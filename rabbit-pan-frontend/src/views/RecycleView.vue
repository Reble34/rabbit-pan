<template>
  <div class="recycle-page rp-page">
    <div class="toolbar glass-2">
      <div class="toolbar-left">
        <el-input v-model="keyword" placeholder="搜索回收站" clearable style="width: 220px"
          :prefix-icon="Search" @keyup.enter="reload" @clear="reload" />
      </div>
      <div class="toolbar-right">
        <el-button v-if="selected.size > 0" type="primary" :icon="RefreshLeft" @click="restoreSelected">恢复</el-button>
        <el-button v-if="selected.size > 0" type="danger" :icon="Delete" @click="deleteSelected">永久删除</el-button>
        <el-popconfirm title="确定清空回收站？此操作不可恢复！" confirm-button-text="清空" cancel-button-text="取消"
          @confirm="clearAll">
          <template #reference>
            <el-button type="danger" plain :icon="DeleteFilled">清空回收站</el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>

    <div class="recycle-list glass-2" v-loading="loadingData">
      <div class="table-head">
        <el-checkbox :model-value="allChecked" @change="toggleAll" />
        <span class="col-name">文件名</span>
        <span class="col-size">大小</span>
        <span class="col-time">删除时间</span>
        <span class="col-left">剩余保留</span>
        <span class="col-actions">操作</span>
      </div>
      <div v-for="item in records" :key="item.id" class="table-row" :class="{ selected: selected.has(item.id) }"
        @click="toggleSelect(item)">
        <el-checkbox :model-value="selected.has(item.id)" @click.stop @change="toggleSelect(item)" />
        <span class="col-name file-name">
          <el-icon :size="20" :color="isDir(item) ? '#0a84ff' : typeMeta(item.fileType).color">
            <component :is="isDir(item) ? 'Folder' : typeMeta(item.fileType).icon" />
          </el-icon>
          {{ item.fileName }}
        </span>
        <span class="col-size">{{ isDir(item) ? '-' : formatSize(item.size) }}</span>
        <span class="col-time time-tag">{{ formatTime(item.deleteTime) }}</span>
        <span class="col-left">
          <el-tag v-if="item.recycleDaysLeft !== undefined" :type="item.recycleDaysLeft <= 3 ? 'danger' : 'info'"
            size="small" effect="plain">
            {{ item.recycleDaysLeft }} 天
          </el-tag>
        </span>
        <span class="col-actions" @click.stop>
          <el-button text size="small" type="primary" :icon="RefreshLeft" @click="restoreItems([item])">恢复</el-button>
          <el-button text size="small" type="danger" :icon="Delete" @click="deleteItems([item])">删除</el-button>
        </span>
      </div>
      <el-empty v-if="!loadingData && records.length === 0" description="回收站是空的" />
    </div>

    <div v-if="total > pageSize" class="pagination-row">
      <el-pagination background layout="prev, pager, next, total" :total="total"
        :page-size="pageSize" :current-page="pageNum" @current-change="onPageChange" />
    </div>
  </div>
</template>

<script setup>
/** RecycleView 回收站：恢复 / 永久删除 / 清空 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { Search, RefreshLeft, Delete, DeleteFilled } from '@element-plus/icons-vue'
import { recycleApi } from '@/api/admin'
import { formatSize, formatTime, typeMeta, isDir } from '@/utils/format'

const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(50)
const keyword = ref('')
const loadingData = ref(false)
const selected = ref(new Set())

const allChecked = computed(() => records.value.length > 0 && records.value.every((r) => selected.value.has(r.id)))

async function reload() {
  loadingData.value = true
  try {
    const page = await recycleApi.list({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined
    })
    records.value = page.records || []
    total.value = Number(page.total || 0)
    selected.value = new Set()
  } finally {
    loadingData.value = false
  }
}

function onPageChange(p) {
  pageNum.value = p
  reload()
}

function toggleAll(checked) {
  selected.value = checked ? new Set(records.value.map((r) => r.id)) : new Set()
}

function toggleSelect(item) {
  const next = new Set(selected.value)
  if (next.has(item.id)) next.delete(item.id)
  else next.add(item.id)
  selected.value = next
}

async function restoreItems(items) {
  try {
    await recycleApi.restore(items.map((i) => i.id))
    ElMessage.success('恢复成功')
    reload()
  } catch (e) {
    /* 重名等错误由拦截器提示 */
  }
}

async function deleteItems(items) {
  await ElMessageBox.confirm(
    `永久删除 ${items.length} 项并释放空间？此操作不可恢复！`,
    '永久删除',
    { type: 'error', confirmButtonText: '永久删除', cancelButtonText: '取消' }
  )
  await recycleApi.deleteForever(items.map((i) => i.id))
  ElMessage.success('已永久删除')
  reload()
}

function restoreSelected() {
  restoreItems(records.value.filter((r) => selected.value.has(r.id)))
}

function deleteSelected() {
  deleteItems(records.value.filter((r) => selected.value.has(r.id)))
}

async function clearAll() {
  await recycleApi.clear()
  ElMessage.success('回收站已清空')
  reload()
}

onMounted(reload)
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: var(--rp-radius-md);
  flex-wrap: wrap;
  gap: 10px;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.recycle-list {
  margin-top: 12px;
  border-radius: var(--rp-radius-md);
  padding: 6px 8px;
  min-height: 240px;
}
.table-head,
.table-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 100px 170px 110px 180px;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
}
.table-head {
  color: var(--rp-text-3);
  font-size: 12px;
  border-bottom: 1px solid var(--rp-glass-border-soft);
}
.table-row {
  border-radius: var(--rp-radius-sm);
  transition: background 0.2s var(--rp-ease);
  cursor: default;
  &:hover {
    background: rgba(255, 255, 255, 0.14);
  }
  &.selected {
    background: var(--rp-primary-soft);
  }
}
.file-name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.col-size,
.col-time {
  color: var(--rp-text-2);
  font-size: 13px;
}
.time-tag {
  color: var(--rp-text-3);
  font-size: 12px;
}
.col-actions {
  display: flex;
  justify-content: flex-end;
}
.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
