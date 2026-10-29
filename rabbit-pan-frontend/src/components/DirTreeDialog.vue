<template>
  <el-dialog v-model="visible" :title="title" width="420px">
    <el-tree
      ref="treeRef"
      node-key="id"
      :data="treeData"
      :props="{ label: 'label', children: 'children', isLeaf: 'leaf' }"
      lazy
      :load="loadNode"
      :current-node-key="0"
      highlight-current
      default-expand-all
      @node-click="onSelect"
    >
      <template #default="{ data }">
        <span class="dir-node">
          <el-icon color="#0a84ff"><Folder /></el-icon>
          {{ data.label }}
        </span>
      </template>
    </el-tree>
    <div class="selected-path">
      目标：<b>{{ selectedLabel }}</b>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="disabledRoot && selectedId === 0" @click="confirm">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/** DirTreeDialog 目录选择弹窗（移动/复制目标选择，懒加载目录树） */
import { computed, ref, watch } from 'vue'
import { fileApi } from '@/api/file'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '选择目标位置' },
  // 禁选根目录（如"移动到根目录"应允许，复制时也允许；默认允许）
  disabledRoot: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'select'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const treeData = ref([{ id: 0, label: '我的网盘', leaf: false }])
const selectedId = ref(0)
const selectedLabel = ref('我的网盘')

watch(visible, (v) => {
  if (v) {
    selectedId.value = 0
    selectedLabel.value = '我的网盘'
  }
})

/** 懒加载子目录 */
async function loadNode(node, resolve) {
  if (node.level === 0) {
    return resolve([{ id: 0, label: '我的网盘', leaf: false }])
  }
  try {
    const children = await fileApi.dirChildren(node.data.id)
    resolve(children.map((c) => ({ ...c, leaf: false })))
  } catch (e) {
    resolve([])
  }
}

function onSelect(data) {
  selectedId.value = data.id
  selectedLabel.value = data.label
}

function confirm() {
  emit('select', { id: selectedId.value, label: selectedLabel.value })
  visible.value = false
}
</script>

<style scoped lang="scss">
.dir-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.selected-path {
  margin-top: 12px;
  font-size: 13px;
  color: var(--rp-text-2);
}
</style>
