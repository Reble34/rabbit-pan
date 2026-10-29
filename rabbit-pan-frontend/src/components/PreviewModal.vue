<template>
  <el-dialog
    v-model="visible"
    :title="file && file.fileName"
    width="min(880px, 92vw)"
    top="6vh"
    destroy-on-close
    class="preview-dialog"
  >
    <div class="preview-body">
      <!-- 图片 -->
      <div v-if="kind === 'image'" class="img-wrap">
        <img :src="src" :alt="file && file.fileName" @error="onImgError" />
      </div>

      <!-- 视频 -->
      <video v-else-if="kind === 'video'" :src="src" controls autoplay class="media"></video>

      <!-- 音频 -->
      <div v-else-if="kind === 'audio'" class="audio-wrap">
        <el-icon :size="64" color="var(--rp-primary)"><Headset /></el-icon>
        <audio :src="src" controls autoplay class="audio"></audio>
      </div>

      <!-- PDF -->
      <iframe v-else-if="kind === 'pdf'" :src="src" class="pdf-frame"></iframe>

      <!-- 文本 / Markdown -->
      <div v-else-if="kind === 'text'" class="text-wrap">
        <pre v-if="fileType === 'txt'" class="text-pre">{{ textContent }}</pre>
        <div v-else class="md-body markdown-body" v-html="renderedMd"></div>
      </div>

      <!-- ZIP 目录树 -->
      <div v-else-if="kind === 'zip'" class="zip-wrap">
        <el-tree
          v-if="zipTree.length"
          :data="zipTree"
          :props="{ label: 'label', children: 'children' }"
          default-expand-all
        >
          <template #default="{ data }">
            <span class="zip-node">
              <el-icon v-if="data.isDir" color="#ffd60a"><Folder /></el-icon>
              <el-icon v-else color="#8e8e93"><Document /></el-icon>
              {{ data.label }}
              <span v-if="!data.isDir" class="zip-size">{{ formatSize(data.size) }}</span>
              <el-button text size="small" type="primary" @click="extractEntry(data)">提取</el-button>
            </span>
          </template>
        </el-tree>
        <el-empty v-else description="暂无可预览内容" />
      </div>

      <!-- 不支持 -->
      <div v-else class="unsupported">
        <el-empty :description="unsupportedText">
          <el-button v-if="canDownload !== false" type="primary" @click="download">下载文件</el-button>
        </el-empty>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * PreviewModal 多格式预览：图片 / 音视频 / PDF / TXT / Markdown / ZIP 目录树
 * 媒体请求带 token（或分享 shareToken）查询参数鉴权
 */
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import DOMPurify from 'dompurify'
import { ElMessage } from 'element-plus/es/components/message/index'
import { marked } from 'marked'
import { previewUrl } from '@/api/file'
import { typeMeta, formatSize } from '@/utils/format'
import service, { getMediaToken, downloadFile } from '@/utils/request'

const props = defineProps({
  modelValue: Boolean,
  file: { type: Object, default: null },
  // 公开分享模式：shareToken 必传，替代用户 token
  shareToken: { type: String, default: '' },
  shareCode: { type: String, default: '' },
  canDownload: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const textContent = ref('')
const zipTree = ref([])

const fileType = computed(() => (props.file ? props.file.fileType : ''))
const kind = computed(() => {
  if (!props.file) return 'unsupported'
  switch (props.file.fileType) {
    case 'image':
      return props.shareToken ? 'image' : 'image'
    case 'video':
      return 'video'
    case 'audio':
      return 'audio'
    case 'pdf':
      return 'pdf'
    case 'txt':
    case 'md':
      return 'text'
    case 'zip':
      // 分享模式暂不支持 ZIP 在线目录树（走个人鉴权体系）
      return props.shareToken ? 'unsupported' : 'zip'
    default:
      return 'unsupported'
  }
})

const unsupportedText = computed(() =>
  props.canDownload
    ? '该格式暂不支持在线预览，可下载后查看'
    : '该格式暂不支持在线预览，且当前分享不允许下载'
)

/** 预览地址：个人模式用 5 分钟有效媒体签名令牌（mu=）；分享模式走公开预览接口 */
const src = ref('')

async function buildSrc() {
  if (!props.file) {
    src.value = ''
    return
  }
  if (props.shareToken) {
    const qs = new URLSearchParams({ shareToken: props.shareToken })
    src.value = `/api/v1/share/${props.shareCode}/preview/${props.file.id}?${qs}`
    return
  }
  try {
    const mt = await getMediaToken()
    if (kind.value === 'image') src.value = previewUrl('image', props.file.id, mt, { thumb: 0 })
    else if (kind.value === 'video') src.value = previewUrl('video', props.file.id, mt)
    else if (kind.value === 'audio') src.value = previewUrl('audio', props.file.id, mt)
    else if (kind.value === 'pdf') src.value = previewUrl('doc', props.file.id, mt)
    else src.value = ''
  } catch (e) {
    src.value = ''
  }
}

// Markdown 渲染必须消毒：marked 不做任何净化，未消毒直接 v-html 构成存储型 XSS
const renderedMd = computed(() =>
  fileType.value === 'md' ? DOMPurify.sanitize(marked.parse(textContent.value || '')) : '')

watch(visible, async (v) => {
  if (!v || !props.file) return
  textContent.value = ''
  zipTree.value = []
  await buildSrc()
  if (kind.value === 'text') {
    await loadText()
  } else if (kind.value === 'zip') {
    await loadZipTree()
  }
})

async function loadText() {
  try {
    // 媒体地址（mu= 短时令牌），无需额外鉴权头
    const res = await axios.get(src.value, { responseType: 'text', transformResponse: [(d) => d] })
    textContent.value = typeof res.data === 'string' ? res.data : ''
    if (textContent.value.length > 1024 * 1024) {
      textContent.value = textContent.value.slice(0, 1024 * 1024) + '\n…（内容过长，已截断）'
    }
  } catch (e) {
    ElMessage.error('文本加载失败')
  }
}

async function loadZipTree() {
  try {
    // 走统一 request 实例：Authorization 头鉴权，不再把 token 放进 URL
    const entries = await service.get(`/preview/zip/${props.file.id}`)
    if (!Array.isArray(entries)) return
    // 扁平 entries -> 树
    const root = { label: '/', children: [], isDir: true }
    const dirMap = new Map([['', root]])
    for (const e of entries) {
      const parts = e.name.split('/').filter(Boolean)
      let path = ''
      let parent = root
      for (let i = 0; i < parts.length; i++) {
        path = path ? path + '/' + parts[i] : parts[i]
        const isLast = i === parts.length - 1
        const isDirNode = isLast ? !!e.isDir : true
        if (!dirMap.has(path)) {
          const node = { label: parts[i], isDir: isDirNode, size: e.size, name: e.name, children: [] }
          dirMap.set(path, node)
          parent.children.push(node)
        }
        parent = dirMap.get(path)
      }
    }
    zipTree.value = [root]
  } catch (e) {
    ElMessage.error(e.response && e.response.data && e.response.data.message || '压缩包读取失败')
  }
}

function onImgError(e) {
  e.target.style.display = 'none'
  ElMessage.warning('图片加载失败')
}

/** 压缩包单文件提取下载（Authorization 头鉴权） */
async function extractEntry(data) {
  if (data.isDir) return
  try {
    const res = await service.get('/preview/zip/extract', {
      params: { fileId: props.file.id, entry: data.name },
      responseType: 'blob'
    })
    const url = URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = data.label
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('提取失败')
  }
}

function download() {
  // 个人模式：Blob 下载（Authorization 头），不在 URL 暴露令牌
  if (!props.shareToken) {
    downloadFile(`/download/${props.file.id}`, props.file.fileName)
    return
  }
  const a = document.createElement('a')
  a.href = `/api/v1/share/${props.shareCode}/download/${props.file.id}?shareToken=${encodeURIComponent(props.shareToken)}`
  a.download = props.file.fileName
  a.click()
}
</script>

<style scoped lang="scss">
.preview-body {
  min-height: 200px;
  max-height: 72vh;
  overflow: auto;
}
.img-wrap {
  display: flex;
  justify-content: center;
  background: rgba(0, 0, 0, 0.85);
  border-radius: var(--rp-radius-md);
  padding: 12px;
  img {
    max-width: 100%;
    max-height: 64vh;
    object-fit: contain;
  }
}
.media {
  width: 100%;
  max-height: 64vh;
  background: #000;
  border-radius: var(--rp-radius-md);
}
.audio-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 40px 0;
}
.audio {
  width: 90%;
}
.pdf-frame {
  width: 100%;
  height: 66vh;
  border: none;
  border-radius: var(--rp-radius-md);
  background: #f5f5f5;
}
.text-wrap {
  max-height: 66vh;
  overflow: auto;
  border-radius: var(--rp-radius-md);
  background: var(--rp-glass-2-bg);
  padding: 16px;
}
.text-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}
.md-body {
  line-height: 1.7;
  font-size: 14px;
  :deep(h1),
  :deep(h2),
  :deep(h3) {
    border-bottom: 1px solid var(--rp-glass-border-soft);
    padding-bottom: 6px;
  }
  :deep(pre) {
    background: rgba(0, 0, 0, 0.06);
    padding: 12px;
    border-radius: 8px;
    overflow: auto;
  }
  :deep(code) {
    font-family: 'SF Mono', Menlo, Consolas, monospace;
  }
}
.zip-wrap {
  max-height: 64vh;
  overflow: auto;
}
.zip-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.zip-size {
  color: var(--rp-text-3);
  font-size: 12px;
}
.unsupported {
  padding: 40px 0;
}
</style>
