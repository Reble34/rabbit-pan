<template>
  <div class="profile-page rp-page">
    <div class="profile-grid">
      <!-- ===== 存储空间 ===== -->
      <div class="storage-card glass-2">
        <h3 class="card-title">存储空间</h3>
        <template v-if="userStore.storage">
          <div class="storage-numbers">
            <span class="used">{{ formatSize(userStore.storage.used) }}</span>
            <span class="total">/ {{ formatSize(userStore.storage.total) }}</span>
          </div>
          <el-progress :percentage="Math.min(100, userStore.storage.percent || 0)" :stroke-width="12" />
          <div class="storage-meta">
            <span>剩余 {{ formatSize(userStore.storage.remain) }}</span>
            <span>文件 {{ userStore.storage.fileCount }} 个</span>
          </div>
        </template>
        <el-skeleton v-else :rows="2" animated />
      </div>

      <!-- ===== 个人信息 ===== -->
      <div class="info-card glass-2">
        <h3 class="card-title">个人信息</h3>
        <div class="avatar-row">
          <el-avatar :size="64" class="big-avatar"
            :src="userStore.userInfo && userStore.userInfo.avatar ? userStore.userInfo.avatar : ''">
            {{ (userStore.userInfo && userStore.userInfo.nickname || 'U').slice(0, 1) }}
          </el-avatar>
          <div>
            <div class="username">{{ userStore.userInfo && userStore.userInfo.username }}</div>
            <el-button size="small" :icon="Upload" @click="avatarInputRef.click()">更换头像</el-button>
            <input ref="avatarInputRef" type="file" accept="image/*" hidden @change="onAvatarPicked" />
          </div>
        </div>
        <el-form label-width="60px" style="margin-top: 16px">
          <el-form-item label="昵称">
            <div class="inline-row">
              <el-input v-model="nickname" maxlength="32" />
              <el-button type="primary" plain @click="saveNickname">保存</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <!-- ===== 修改密码 ===== -->
      <div class="password-card glass-2">
        <h3 class="card-title">修改密码</h3>
        <el-form label-position="top">
          <el-form-item label="原密码">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码（6-32 位）">
            <el-input v-model="pwdForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-button type="primary" style="width: 100%" @click="changePassword">确认修改</el-button>
          <p class="pwd-hint">修改成功后所有设备将被强制下线</p>
        </el-form>
      </div>

      <!-- ===== 操作日志 ===== -->
      <div class="log-card glass-2">
        <h3 class="card-title">操作日志</h3>
        <el-table :data="logs" size="small" max-height="320">
          <el-table-column prop="action" label="动作" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="actionTagType(row.action)">{{ row.action }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="160" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" width="120" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="150" />
        </el-table>
        <div class="pagination-row">
          <el-pagination small background layout="prev, pager, next" :total="logTotal"
            :page-size="logPageSize" :current-page="logPageNum" @current-change="loadLogs" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/** ProfileView 个人中心：存储用量 / 头像昵称 / 密码 / 操作日志 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { Upload } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores'
import { formatSize } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const avatarInputRef = ref()
const nickname = ref('')
const logs = ref([])
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = ref(10)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

onMounted(async () => {
  await userStore.fetchInfo().catch(() => {})
  nickname.value = (userStore.userInfo && userStore.userInfo.nickname) || ''
  await Promise.all([userStore.fetchStorage().catch(() => {}), loadLogs()])
})

async function loadLogs() {
  const page = await authApi.logs(logPageNum.value, logPageSize.value)
  logs.value = page.records || []
  logTotal.value = Number(page.total || 0)
}

async function onAvatarPicked(e) {
  const file = e.target.files[0]
  if (!file) return
  await authApi.uploadAvatar(file)
  await userStore.fetchInfo()
  ElMessage.success('头像已更新')
  e.target.value = ''
}

async function saveNickname() {
  await authApi.updateNickname(nickname.value.trim())
  await userStore.fetchInfo()
  ElMessage.success('昵称已更新')
}

async function changePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  await authApi.changePassword(pwdForm)
  ElMessage.success('密码已修改，请重新登录')
  setTimeout(() => {
    userStore.logout().finally(() => router.push('/login'))
  }, 800)
}

function actionTagType(action) {
  if (['DELETE', 'DELETE_FOREVER', 'LOGIN_FAIL'].includes(action)) return 'danger'
  if (['LOGIN', 'REGISTER', 'RESTORE'].includes(action)) return 'success'
  if (['SHARE', 'UPLOAD'].includes(action)) return 'primary'
  return 'info'
}
</script>

<style scoped lang="scss">
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}
.glass-2 {
  border-radius: var(--rp-radius-md);
  padding: 18px;
}
.card-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 700;
}
.storage-numbers {
  margin-bottom: 10px;
  .used {
    font-size: 26px;
    font-weight: 800;
  }
  .total {
    color: var(--rp-text-3);
    margin-left: 6px;
  }
}
.storage-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  color: var(--rp-text-2);
  font-size: 13px;
}
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.big-avatar {
  background: linear-gradient(135deg, #0a84ff, #5ac8fa);
  font-size: 24px;
  font-weight: 700;
}
.username {
  color: var(--rp-text-2);
  margin-bottom: 8px;
  font-size: 13px;
}
.inline-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.pwd-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--rp-text-3);
  text-align: center;
}
.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: 10px;
}
@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
