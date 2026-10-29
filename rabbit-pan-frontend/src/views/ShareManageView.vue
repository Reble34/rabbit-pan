<template>
  <div class="share-manage-page rp-page">
    <div class="share-list glass-2" v-loading="loadingData">
      <div class="table-head">
        <span class="col-file">文件</span>
        <span class="col-link">短链接</span>
        <span class="col-visit">访问次数</span>
        <span class="col-expire">有效期</span>
        <span class="col-status">状态</span>
        <span class="col-actions">操作</span>
      </div>
      <div v-for="item in records" :key="item.id" class="table-row">
        <span class="col-file file-name">
          <el-icon :size="20" :color="isDir(item) ? '#0a84ff' : typeMeta(item.fileType && fileTypeOf(item)).color">
            <component :is="isDir(item) ? 'Folder' : 'Document'" />
          </el-icon>
          <span class="name-text" :title="item.fileName">{{ item.fileName }}</span>
        </span>
        <span class="col-link">
          <el-link type="primary" :underline="false" @click="openShare(item)">
            {{ item.shortUrl }}
            <el-icon class="copy-icon" title="复制" @click.stop="copyLink(item)"><CopyDocument /></el-icon>
          </el-link>
        </span>
        <span class="col-visit">
          {{ item.viewCount }}{{ item.maxView >= 0 ? ' / ' + item.maxView : '' }} 次
        </span>
        <span class="col-expire">
          <el-tag v-if="!item.expireTime" type="success" size="small" effect="plain">永久有效</el-tag>
          <span v-else>{{ formatTime(item.expireTime) }}</span>
        </span>
        <span class="col-status">
          <el-tag :type="shareStatus(item).type" size="small" effect="plain">{{ shareStatus(item).label }}</el-tag>
          <el-tag v-if="item.hasPassword" size="small" type="warning" effect="plain">提取码</el-tag>
        </span>
        <span class="col-actions">
          <el-button text size="small" :icon="EditPen" @click="openEdit(item)">编辑</el-button>
          <el-popconfirm title="撤销后短链立即失效，确定？" @confirm="revokeShare(item)">
            <template #reference>
              <el-button text size="small" type="danger" :icon="CircleClose">撤销</el-button>
            </template>
          </el-popconfirm>
        </span>
      </div>
      <el-empty v-if="!loadingData && records.length === 0" description="还没有创建过分享" />
    </div>

    <div v-if="total > pageSize" class="pagination-row">
      <el-pagination background layout="prev, pager, next" :total="total"
        :page-size="pageSize" :current-page="pageNum" @current-change="onPageChange" />
    </div>

    <!-- 编辑分享 -->
    <el-dialog v-model="editDialog.visible" title="编辑分享" width="420px">
      <el-form label-position="top">
        <el-form-item label="有效期">
          <el-radio-group v-model="editDialog.form.expireDays">
            <el-radio-button :value="1">1 天</el-radio-button>
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
            <el-radio-button :value="0">永久</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="提取码（留空清除）">
          <el-input v-model="editDialog.form.password" placeholder="4-6 位，留空则清除提取码" maxlength="6" clearable />
        </el-form-item>
        <el-form-item label="下载权限">
          <el-switch v-model="editDialog.form.canDownload" active-text="可下载" inactive-text="仅预览" />
        </el-form-item>
        <el-form-item label="最大访问次数">
          <el-input-number v-model="editDialog.form.maxView" :min="-1" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** ShareManageView 我的分享：访问统计 / 复制 / 续期改码 / 撤销 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { EditPen, CircleClose, CopyDocument } from '@element-plus/icons-vue'
import { shareApi } from '@/api/share'
import { formatTime, isDir, typeMeta } from '@/utils/format'

const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loadingData = ref(false)

const editDialog = reactive({
  visible: false,
  id: null,
  form: { expireDays: 7, password: '', canDownload: true, maxView: -1 }
})

function fileTypeOf(item) {
  return item.isDir ? null : 'other'
}

function shareStatus(item) {
  if (item.status === 0) return { label: '已撤销', type: 'info' }
  if (item.expireTime && new Date(item.expireTime).getTime() < Date.now())
    return { label: '已过期', type: 'danger' }
  if (item.maxView >= 0 && item.viewCount >= item.maxView)
    return { label: '已达上限', type: 'danger' }
  return { label: '有效', type: 'success' }
}

async function reload() {
  loadingData.value = true
  try {
    const page = await shareApi.list(pageNum.value, pageSize.value)
    records.value = page.records || []
    total.value = Number(page.total || 0)
  } finally {
    loadingData.value = false
  }
}

function onPageChange(p) {
  pageNum.value = p
  reload()
}

async function copyLink(item) {
  await navigator.clipboard.writeText(item.shortUrl)
  ElMessage.success('已复制')
}

function openShare(item) {
  window.open(item.shortUrl, '_blank')
}

async function revokeShare(item) {
  await shareApi.revoke(item.id)
  ElMessage.success('分享已撤销')
  reload()
}

function openEdit(item) {
  editDialog.id = item.id
  editDialog.form = {
    expireDays: item.expireTime
      ? Math.max(1, Math.ceil((new Date(item.expireTime).getTime() - Date.now()) / 86400000))
      : 0,
    password: '',
    canDownload: item.canDownload,
    maxView: item.maxView
  }
  editDialog.visible = true
}

async function saveEdit() {
  const f = editDialog.form
  const payload = { expireDays: f.expireDays, canDownload: f.canDownload, maxView: f.maxView }
  if (f.password) payload.password = f.password
  await shareApi.update(editDialog.id, payload)
  ElMessage.success('分享配置已更新')
  editDialog.visible = false
  reload()
}

onMounted(reload)
</script>

<style scoped lang="scss">
.share-list {
  border-radius: var(--rp-radius-md);
  padding: 6px 8px;
  min-height: 240px;
}
.table-head,
.table-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.6fr) 120px 150px 140px 170px;
  align-items: center;
  gap: 8px;
  padding: 10px;
}
.table-head {
  color: var(--rp-text-3);
  font-size: 12px;
  border-bottom: 1px solid var(--rp-glass-border-soft);
}
.table-row {
  border-radius: var(--rp-radius-sm);
  &:hover {
    background: rgba(255, 255, 255, 0.12);
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
  }
}
.col-link {
  font-size: 13px;
  min-width: 0;
  overflow: hidden;
  .copy-icon {
    margin-left: 6px;
    cursor: pointer;
  }
}
.col-visit,
.col-expire {
  color: var(--rp-text-2);
  font-size: 13px;
}
.col-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
