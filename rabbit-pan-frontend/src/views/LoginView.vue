<template>
  <div class="login-page">
    <LiquidBackground />
    <div class="login-card glass-1">
      <div class="brand">
        <svg viewBox="0 0 100 100" width="56" height="56" aria-hidden="true">
          <circle cx="50" cy="58" r="36" fill="#0a84ff" />
          <ellipse cx="32" cy="24" rx="13" ry="19" fill="#0a84ff" />
          <ellipse cx="62" cy="20" rx="11" ry="17" fill="#0a84ff" />
          <circle cx="40" cy="55" r="4" fill="#fff" />
          <circle cx="62" cy="55" r="4" fill="#fff" />
        </svg>
        <h1 class="brand-title">Rabbit 网盘</h1>
        <p class="brand-sub">私有云盘 · 安全可靠 · 一句话分享</p>
      </div>

      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="rules" size="large" @keyup.enter="doLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="密码" :prefix-icon="Lock" />
            </el-form-item>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="doLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form ref="regFormRef" :model="regForm" :rules="rules" size="large" @keyup.enter="doRegister">
            <el-form-item prop="username">
              <el-input v-model="regForm.username" placeholder="用户名（4-32 位字母数字）" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="inviteCode">
              <el-input v-model="regForm.inviteCode" placeholder="口令码（向管理员获取）" maxlength="12" :prefix-icon="Key" />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="regForm.nickname" placeholder="昵称（可选）" :prefix-icon="Postcard" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="regForm.password" type="password" show-password placeholder="密码（6-32 位）" :prefix-icon="Lock" />
            </el-form-item>
            <el-form-item prop="confirm">
              <el-input v-model="regForm.confirm" type="password" show-password placeholder="确认密码" :prefix-icon="Lock" />
            </el-form-item>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="doRegister">
              注 册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
/** 登录/注册页：全屏液态背景 + 居中悬浮玻璃卡片 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock, Postcard, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores'
import LiquidBackground from '@/components/LiquidBackground.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const tab = ref('login')
const loading = ref(false)
const loginFormRef = ref()
const regFormRef = ref()

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', inviteCode: '', nickname: '', password: '', confirm: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  inviteCode: [{ required: true, message: '请输入口令码（向管理员获取）', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirm: [
    {
      validator: (rule, value, callback) => {
        if (value !== regForm.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function doLogin() {
  await loginFormRef.value.validate()
  loading.value = true
  try {
    await userStore.login({ username: loginForm.username, password: loginForm.password })
    ElMessage.success('欢迎回来')
    router.push(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  await regFormRef.value.validate()
  loading.value = true
  try {
    await userStore.register({
      username: regForm.username,
      password: regForm.password,
      nickname: regForm.nickname || undefined,
      inviteCode: regForm.inviteCode.trim()
    })
    ElMessage.success('注册成功，请登录')
    tab.value = 'login'
    loginForm.username = regForm.username
    loginForm.password = ''
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-card {
  width: 400px;
  border-radius: 20px;
  padding: 32px 36px 24px;
}
.brand {
  text-align: center;
  margin-bottom: 8px;
}
.brand-title {
  margin: 10px 0 4px;
  font-size: 22px;
  font-weight: 800;
  color: var(--rp-text-1);
}
.brand-sub {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--rp-text-3);
}
.submit-btn {
  width: 100%;
  margin-top: 4px;
  height: 42px;
  font-size: 15px;
  border-radius: 10px;
}
</style>
