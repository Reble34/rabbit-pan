import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 组件按需自动引入（见 vite.config.mjs 的自定义解析器），
// 样式统一引入完整 CSS：gzip 后仅约 50KB，换来主题覆盖与命令式组件样式无需逐个维护。
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import { ElLoading } from 'element-plus/es/components/loading/index'

import App from './App.vue'
import router from './router'
import { useAppStore } from './stores'
import './styles/index.scss'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
// v-loading 指令（按需引入后不再由 app.use(ElementPlus) 注册）
app.use(ElLoading)

// 初始化主题（明/暗）
useAppStore().init()

app.mount('#app')
