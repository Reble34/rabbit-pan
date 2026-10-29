import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/** ElTableColumn -> table-column；ElConfigProvider -> config-provider */
const kebab = (s) => s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()

/** 子组件所在目录：如 ElTabPane 的实现在 components/tabs 下 */
const EP_PARENT = {
  'tab-pane': 'tabs',
  'table-column': 'table',
  'dropdown-item': 'dropdown',
  'dropdown-menu': 'dropdown',
  option: 'select',
  'option-group': 'select',
  'form-item': 'form',
  'radio-button': 'radio',
  'radio-group': 'radio',
  'button-group': 'button',
  'checkbox-group': 'checkbox',
  'menu-item': 'menu',
  'sub-menu': 'menu',
  'breadcrumb-item': 'breadcrumb',
  'timeline-item': 'timeline',
  'carousel-item': 'carousel',
  'collapse-item': 'collapse',
  'descriptions-item': 'descriptions'
}

/**
 * Element Plus 按需解析器
 *
 * 官方的 ElementPlusResolver 在 2.x 会返回 `from: 'element-plus/es'`（整包入口），
 * 入口里的 installer 引用了全部组件，导致 tree-shaking 失效（实测打入 88 个组件、920KB）。
 * 这里改为直接指向 `element-plus/es/components/<name>/index`，只保留真正用到的组件。
 */
const ElementPlusOnDemand = {
  type: 'component',
  resolve(name) {
    if (!name.startsWith('El')) return
    if (name === 'ElIcon') {
      return { name, from: 'element-plus/es/components/icon/index' }
    }
    // 图标组件（ElIconXxx）由各页面显式 import，这里兜底
    if (name.startsWith('ElIcon')) {
      return { name: name.slice(2), from: '@element-plus/icons-vue' }
    }
    const partial = kebab(name.slice(2))
    const dir = EP_PARENT[partial] || partial
    return { name, from: `element-plus/es/components/${dir}/index` }
  }
}

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 组件按需自动引入（样式统一在 main.js 引入完整 CSS，便于覆盖主题）
    Components({
      resolvers: [ElementPlusOnDemand],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 本地开发代理到后端
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1600,
    rollupOptions: {
      output: {
        // 代码分割：第三方依赖独立分包，配合 Nginx 强缓存。
        // 不能用 { 'element-plus': ['element-plus'] } 这种写法，
        // 它会强制拉入整包入口，抵消按需引入。
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('@element-plus/icons-vue')) return 'ep-icons'
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('/vue/') || id.includes('vue-router') || id.includes('pinia')) return 'vue'
        }
      }
    }
  }
})
