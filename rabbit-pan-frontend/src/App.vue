<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
// 全局中文语言包（改为按需引入后，不再通过 app.use(ElementPlus) 注入 locale）
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>
