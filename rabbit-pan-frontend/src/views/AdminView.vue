<template>
  <div class="admin-page rp-page">
    <el-tabs v-model="tab" class="admin-tabs">
      <!-- ===== 数据概览 ===== -->
      <el-tab-pane label="数据概览" name="stats">
        <div class="stat-grid">
          <div v-for="card in statCards" :key="card.label" class="stat-card glass-2 rp-hover-lift">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== 用户管理 ===== -->
      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar glass-2">
          <el-input v-model="userKeyword" placeholder="按用户名搜索" clearable style="width: 220px"
            :prefix-icon="Search" @keyup.enter="loadUsers" @clear="loadUsers" />
          <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
        </div>
        <div class="user-table glass-2">
          <el-table :data="users" v-loading="loadingUsers">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="nickname" label="昵称" min-width="120" />
            <el-table-column prop="role" label="角色" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="plain">
                  {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="存储用量" min-width="180">
              <template #default="{ row }">
                <el-progress :percentage="quotaPercent(row)" :stroke-width="8"
                  :format="() => `${formatSize(row.storageUsed)} / ${formatSize(row.storageTotal)}`" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-switch :model-value="row.status === 1" :disabled="row.role === 'ADMIN'"
                  @change="(v) => toggleStatus(row, v)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button text size="small" :icon="Coin" @click="openQuota(row)">配额</el-button>
                <el-popconfirm title="删除用户将级联删除其全部文件，确定？" @confirm="removeUser(row)">
                  <template #reference>
                    <el-button text size="small" type="danger" :icon="Delete"
                      :disabled="row.role === 'ADMIN'">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination background layout="prev, pager, next, total" :total="userTotal"
              :page-size="userPageSize" :current-page="userPageNum" @current-change="onUserPage" />
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== 系统配置 ===== -->
      <el-tab-pane label="系统配置" name="config">
        <div class="config-card glass-2">
          <el-form label-position="top" style="max-width: 480px">
            <el-form-item label="单文件大小上限（MB）">
              <el-input-number v-model="configForm.maxFileMb" :min="1" :max="102400" style="width: 100%" />
            </el-form-item>
            <el-form-item label="允许上传的扩展名（逗号分隔，exe/bat/js 等高危类型始终拦截）">
              <el-input v-model="configForm.allowedExts" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="回收站保留天数">
              <el-input-number v-model="configForm.recycleKeepDays" :min="1" :max="365" style="width: 100%" />
            </el-form-item>
            <el-form-item label="新用户默认配额（GB）">
              <el-input-number v-model="configForm.defaultQuotaGb" :min="1" :max="102400" style="width: 100%" />
            </el-form-item>
            <el-form-item label="系统总存储空间（GB，全用户共享上限）">
              <el-input-number v-model="configForm.totalStorageGb" :min="1" :max="102400" style="width: 100%" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" @click="saveConfig">保存配置</el-button>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- ===== 文件检索 ===== -->
      <el-tab-pane label="文件检索" name="files">
        <div class="toolbar glass-2">
          <el-input v-model="fileKeyword" placeholder="全局文件名搜索（跨用户）" clearable style="width: 280px"
            :prefix-icon="Search" @keyup.enter="loadFiles" @clear="loadFiles" />
        </div>
        <div class="user-table glass-2">
          <el-table :data="files" v-loading="loadingFiles">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="userId" label="所属用户ID" width="110" />
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column label="大小" width="110">
              <template #default="{ row }">{{ isDir(row) ? '-' : formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column prop="updateTime" label="修改时间" width="160" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-popconfirm title="强制删除该文件？" @confirm="removeFile(row)">
                  <template #reference>
                    <el-button text size="small" type="danger" :icon="Delete">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination background layout="prev, pager, next" :total="fileTotal"
              :page-size="filePageSize" :current-page="filePageNum" @current-change="onFilePage" />
          </div>
        </div>
      </el-tab-pane>
      <!-- ===== 口令码管理 ===== -->
      <el-tab-pane label="口令码" name="invite">
        <div class="toolbar glass-2">
          <div class="toolbar-left">
            <span class="invite-hint">注册口令码与 12 位短链同款加密算法（雪花 + 混淆 + Base62），一码一用</span>
          </div>
          <div class="toolbar-right">
            <el-input-number v-model="inviteCount" :min="1" :max="50" controls-position="right" style="width: 130px" />
            <el-button type="primary" :icon="Plus" :loading="generating" @click="generateInvites">
              生成口令码
            </el-button>
          </div>
        </div>

        <div v-if="lastGenerated.length" class="generated-box glass-2">
          <div class="generated-title">
            本次生成 {{ lastGenerated.length }} 个（点击复制）
            <el-button text size="small" type="primary" @click="copyAllInvites">复制全部</el-button>
          </div>
          <div class="generated-codes">
            <el-tag v-for="c in lastGenerated" :key="c.id" class="invite-tag" effect="plain"
              @click="copyText(c.code)">{{ c.code }}</el-tag>
          </div>
        </div>

        <div class="user-table glass-2">
          <el-table :data="invites" v-loading="loadingInvites">
            <el-table-column prop="code" label="口令码" width="180">
              <template #default="{ row }">
                <span class="code-text">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.status === 1 ? '可用' : (row.usedBy ? '已使用' : '已作废') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="usedBy" label="使用者ID" width="110">
              <template #default="{ row }">{{ row.usedBy || '-' }}</template>
            </el-table-column>
            <el-table-column prop="usedTime" label="使用时间" width="160">
              <template #default="{ row }">{{ row.usedTime || '-' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-popconfirm v-if="row.status === 1" title="作废后该口令码无法用于注册，确定？"
                  @confirm="revokeInvite(row)">
                  <template #reference>
                    <el-button text size="small" type="danger" :icon="Delete">作废</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination background layout="prev, pager, next" :total="inviteTotal"
              :page-size="invitePageSize" :current-page="invitePageNum" @current-change="onInvitePage" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增用户 -->
    <el-dialog v-model="createDialog.visible" title="新增用户" width="420px">
      <el-form label-position="top">
        <el-form-item label="用户名（4-32 位）">
          <el-input v-model="createDialog.form.username" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createDialog.form.nickname" />
        </el-form-item>
        <el-form-item label="初始密码（6-32 位）">
          <el-input v-model="createDialog.form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="存储配额（GB，留空用系统默认）">
          <el-input-number v-model="createDialog.form.quotaGb" :min="0" :max="102400" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 修改配额 -->
    <el-dialog v-model="quotaDialog.visible" title="修改存储配额" width="360px">
      <el-form label-position="top">
        <el-form-item :label="`用户：${quotaDialog.username}，新配额（GB）`">
          <el-input-number v-model="quotaDialog.quotaGb" :min="1" :max="102400" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitQuota">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** AdminView 管理后台：统计 / 用户管理 / 系统配置 / 全局文件检索 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Delete, Coin } from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import { useUserStore } from '@/stores'
import { formatSize, isDir } from '@/utils/format'

const userStore = useUserStore()
const tab = ref('stats')

// 统计
const stats = ref(null)
const statCards = computed(() =>
  stats.value
    ? [
        { label: '用户总数', value: stats.value.userCount },
        { label: '文件总量', value: stats.value.fileCount },
        { label: '存储已用', value: formatSize(stats.value.storageUsed) },
        { label: '系统总空间', value: formatSize(stats.value.storageTotal) },
        { label: '今日操作', value: stats.value.todayOpCount },
        { label: '今日登录', value: stats.value.todayLoginCount }
      ]
    : []
)

// 用户管理
const users = ref([])
const userTotal = ref(0)
const userPageNum = ref(1)
const userPageSize = ref(10)
const userKeyword = ref('')
const loadingUsers = ref(false)

// 系统配置
const configForm = ref({ maxFileMb: 2048, allowedExts: '', recycleKeepDays: 30, defaultQuotaGb: 5, totalStorageGb: 30 })

// 文件检索
const files = ref([])
const fileTotal = ref(0)
const filePageNum = ref(1)
const filePageSize = ref(20)
const fileKeyword = ref('')
const loadingFiles = ref(false)

// 口令码
const invites = ref([])
const inviteTotal = ref(0)
const invitePageNum = ref(1)
const invitePageSize = ref(10)
const loadingInvites = ref(false)
const inviteCount = ref(5)
const generating = ref(false)
const lastGenerated = ref([])

const createDialog = reactive({ visible: false, form: { username: '', nickname: '', password: '', quotaGb: undefined } })
const quotaDialog = reactive({ visible: false, userId: null, username: '', quotaGb: 50 })

onMounted(async () => {
  stats.value = await adminApi.statistics()
  await loadUsers()
  configForm.value = await adminApi.getConfig()
  await loadFiles()
  await loadInvites()
})

// ===== 口令码 =====
async function loadInvites() {
  loadingInvites.value = true
  try {
    const page = await adminApi.inviteList({
      pageNum: invitePageNum.value,
      pageSize: invitePageSize.value
    })
    invites.value = page.records || []
    inviteTotal.value = Number(page.total || 0)
  } finally {
    loadingInvites.value = false
  }
}

function onInvitePage(p) {
  invitePageNum.value = p
  loadInvites()
}

async function generateInvites() {
  generating.value = true
  try {
    lastGenerated.value = await adminApi.inviteGenerate(inviteCount.value)
    ElMessage.success(`已生成 ${lastGenerated.value.length} 个口令码`)
    loadInvites()
  } finally {
    generating.value = false
  }
}

async function revokeInvite(row) {
  await adminApi.inviteRevoke(row.id)
  ElMessage.success('口令码已作废')
  loadInvites()
}

async function copyText(text) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

async function copyAllInvites() {
  await navigator.clipboard.writeText(lastGenerated.value.map((c) => c.code).join('\n'))
  ElMessage.success('全部口令码已复制')
}

async function loadUsers() {
  loadingUsers.value = true
  try {
    const page = await adminApi.userList({
      pageNum: userPageNum.value,
      pageSize: userPageSize.value,
      keyword: userKeyword.value || undefined
    })
    users.value = page.records || []
    userTotal.value = Number(page.total || 0)
  } finally {
    loadingUsers.value = false
  }
}

function onUserPage(p) {
  userPageNum.value = p
  loadUsers()
}

function quotaPercent(row) {
  if (!row.storageTotal) return 0
  return Math.min(100, Math.round(((row.storageUsed || 0) / row.storageTotal) * 100))
}

async function toggleStatus(row, enabled) {
  await adminApi.updateStatus({ userId: row.id, status: enabled ? 1 : 0 })
  ElMessage.success(enabled ? '已启用' : '已禁用（并强制下线）')
  loadUsers()
}

function openQuota(row) {
  quotaDialog.userId = row.id
  quotaDialog.username = row.username
  quotaDialog.quotaGb = Math.max(1, Math.round((row.storageTotal || 0) / 1024 ** 3))
  quotaDialog.visible = true
}

async function submitQuota() {
  await adminApi.updateQuota({ userId: quotaDialog.userId, quotaGb: quotaDialog.quotaGb })
  ElMessage.success('配额已更新')
  quotaDialog.visible = false
  loadUsers()
}

function openCreate() {
  createDialog.form = { username: '', nickname: '', password: '', quotaGb: undefined }
  createDialog.visible = true
}

async function submitCreate() {
  const f = createDialog.form
  await adminApi.createUser({
    username: f.username,
    password: f.password,
    nickname: f.nickname || undefined,
    quotaGb: f.quotaGb || undefined
  })
  ElMessage.success('用户已创建')
  createDialog.visible = false
  loadUsers()
}

async function removeUser(row) {
  await adminApi.deleteUser(row.id)
  ElMessage.success('用户已删除')
  loadUsers()
  stats.value = await adminApi.statistics()
}

async function saveConfig() {
  await adminApi.updateConfig(configForm.value)
  ElMessage.success('配置已保存')
}

async function loadFiles() {
  loadingFiles.value = true
  try {
    const page = await adminApi.fileSearch({
      pageNum: filePageNum.value,
      pageSize: filePageSize.value,
      keyword: fileKeyword.value || undefined
    })
    files.value = page.records || []
    fileTotal.value = Number(page.total || 0)
  } finally {
    loadingFiles.value = false
  }
}

function onFilePage(p) {
  filePageNum.value = p
  loadFiles()
}

async function removeFile(row) {
  await adminApi.deleteFile(row.id)
  ElMessage.success('已删除')
  loadFiles()
}
</script>

<style scoped lang="scss">
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 14px;
}
.stat-card {
  border-radius: var(--rp-radius-md);
  padding: 22px;
  text-align: center;
}
.stat-value {
  font-size: 28px;
  font-weight: 800;
}
.stat-label {
  margin-top: 6px;
  color: var(--rp-text-3);
  font-size: 13px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-radius: var(--rp-radius-md);
  margin-bottom: 12px;
}
.user-table,
.config-card {
  border-radius: var(--rp-radius-md);
  padding: 12px;
}
.config-card {
  padding: 20px;
}
.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
.invite-hint {
  font-size: 12px;
  color: var(--rp-text-3);
}
.generated-box {
  border-radius: var(--rp-radius-md);
  padding: 14px;
  margin-bottom: 12px;
}
.generated-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--rp-text-2);
  margin-bottom: 10px;
}
.generated-codes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.invite-tag {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  cursor: pointer;
  letter-spacing: 1px;
}
.code-text {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  letter-spacing: 1px;
}
</style>
