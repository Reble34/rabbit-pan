<template>
  <el-dialog v-model="visible" title="分享文件" width="460px" :close-on-click-modal="false">
    <div class="share-file glass-2">
      <el-icon :size="28" :color="meta.color"><component :is="meta.icon" /></el-icon>
      <span class="share-file-name">{{ file.fileName }}</span>
      <span class="share-file-size">{{ isDirFile ? '文件夹' : formatSize(file.size) }}</span>
    </div>

    <el-form label-position="top" class="share-form">
      <el-form-item label="有效期">
        <el-radio-group v-model="form.expireDays">
          <el-radio-button :value="1">1 天</el-radio-button>
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
          <el-radio-button :value="0">永久</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="提取码">
        <div class="pwd-row">
          <el-switch v-model="usePassword" />
          <template v-if="usePassword">
            <el-input v-model="form.password" class="pwd-input" maxlength="6" placeholder="4-6 位字母或数字" />
            <el-button size="small" @click="genPassword">随机</el-button>
          </template>
        </div>
      </el-form-item>
      <el-form-item label="访问权限">
        <div class="perm-row">
          <el-switch v-model="form.canDownload" active-text="可下载" inactive-text="仅预览" />
          <el-input-number v-model="form.maxView" :min="-1" :step="10" controls-position="right"
            placeholder="-1 不限" style="width: 140px" />
          <span class="perm-hint">最大访问次数（-1 不限）</span>
        </div>
      </el-form-item>
    </el-form>

    <div v-if="result" class="share-result glass-2">
      <el-input :model-value="result.shortUrl" readonly class="share-link">
        <template #append>
          <el-button @click="copyLink">
            <el-icon v-if="copied" color="var(--rp-success)"><Select /></el-icon>
            <span v-else>复制</span>
          </el-button>
        </template>
      </el-input>
      <div class="share-meta">
        短码 <b>{{ result.shortCode }}</b>
        <span v-if="usePassword && form.password"> · 提取码 <b>{{ form.password }}</b></span>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="creating" @click="create">
        {{ result ? '重新生成' : '创建分享链接' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/** ShareDialog 分享弹窗：有效期 / 提取码 / 权限 / 12 位短链一键复制 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { shareApi } from '@/api/share'
import { typeMeta, formatSize, isDir } from '@/utils/format'

const props = defineProps({
  modelValue: Boolean,
  file: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'created'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const isDirFile = computed(() => isDir(props.file))
const meta = computed(() => (isDirFile.value ? { icon: 'Folder', color: '#0a84ff' } : typeMeta(props.file && props.file.fileType)))

const form = reactive({
  expireDays: 7,
  password: '',
  canDownload: true,
  maxView: -1
})
const usePassword = ref(false)
const creating = ref(false)
const result = ref(null)
const copied = ref(false)

watch(visible, (v) => {
  if (v) {
    result.value = null
    copied.value = false
    usePassword.value = false
    form.password = ''
    form.expireDays = 7
    form.canDownload = true
    form.maxView = -1
  }
})

function genPassword() {
  const chars = 'abcdefghjkmnpqrstuvwxyz23456789'
  form.password = Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

async function create() {
  if (usePassword.value && !/^[a-zA-Z0-9]{4,6}$/.test(form.password)) {
    ElMessage.warning('提取码需为 4-6 位字母或数字')
    return
  }
  creating.value = true
  try {
    result.value = await shareApi.create({
      fileId: props.file.id,
      expireDays: form.expireDays,
      password: usePassword.value ? form.password : '',
      maxView: form.maxView,
      canDownload: form.canDownload
    })
    emit('created', result.value)
  } finally {
    creating.value = false
  }
}

async function copyLink() {
  await navigator.clipboard.writeText(result.value.shortUrl)
  copied.value = true
  ElMessage.success('链接已复制')
  setTimeout(() => (copied.value = false), 2000)
}
</script>

<style scoped lang="scss">
.share-file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: var(--rp-radius-md);
  margin-bottom: 16px;
}
.share-file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.share-file-size {
  color: var(--rp-text-3);
  font-size: 12px;
}
.pwd-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.pwd-input {
  width: 180px;
}
.perm-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.perm-hint {
  font-size: 12px;
  color: var(--rp-text-3);
}
.share-result {
  padding: 12px;
  border-radius: var(--rp-radius-md);
}
.share-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--rp-text-2);
}
</style>
