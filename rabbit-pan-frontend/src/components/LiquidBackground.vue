<template>
  <canvas ref="canvasRef" class="liquid-bg" aria-hidden="true"></canvas>
</template>

<script setup>
/**
 * LiquidBackground 液态动态背景
 * 模仿 macOS Sonoma 液态壁纸：缓慢流动融合的渐变色块（canvas 实现，无 WebGL 依赖）
 * - 动画周期 20-30s，页面切后台自动暂停
 * - 低性能设备（核数 <= 4 或内存有限）渲染静态一帧降级
 */
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useAppStore } from '@/stores'

const canvasRef = ref(null)
const appStore = useAppStore()

const LIGHT_COLORS = ['255, 214, 224', '199, 224, 255', '217, 204, 255', '255, 233, 201']
const DARK_COLORS = ['27, 54, 106', '38, 34, 62', '52, 33, 82', '18, 60, 55']

let ctx = null
let rafId = 0
let blobs = []
let running = false
let lowPerf = false
let t = 0

function palette() {
  return appStore.dark ? DARK_COLORS : LIGHT_COLORS
}

function setup() {
  const canvas = canvasRef.value
  if (!canvas) return
  ctx = canvas.getContext('2d')
  resize()
  // 低性能检测：关闭动态动画，静态渲染一帧
  lowPerf =
    (navigator.hardwareConcurrency || 8) <= 4 ||
    (navigator.deviceMemory || 8) <= 4 ||
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  blobs = palette().map((color, i) => ({
    color,
    baseX: 0.18 + i * 0.22,
    baseY: 0.25 + (i % 2) * 0.4,
    radius: 0.45 + (i % 3) * 0.12,
    phaseX: i * 1.3,
    phaseY: i * 0.9,
    speed: 0.00018 + i * 0.00005 // 约 20-30s 一个循环
  }))
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = Math.min(window.devicePixelRatio || 1, 1.5)
  canvas.width = window.innerWidth * dpr
  canvas.height = window.innerHeight * dpr
  canvas.style.width = window.innerWidth + 'px'
  canvas.style.height = window.innerHeight + 'px'
  if (ctx) {
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  }
}

function drawFrame() {
  if (!ctx) return
  const w = window.innerWidth
  const h = window.innerHeight
  // 底色
  ctx.fillStyle = appStore.dark ? '#0b1020' : '#eef2f7'
  ctx.fillRect(0, 0, w, h)
  ctx.globalCompositeOperation = appStore.dark ? 'source-over' : 'lighter'
  for (const b of blobs) {
    const x = (b.baseX + Math.sin(t * b.speed + b.phaseX) * 0.18) * w
    const y = (b.baseY + Math.cos(t * b.speed + b.phaseY) * 0.16) * h
    const r = b.radius * Math.min(w, h)
    const gradient = ctx.createRadialGradient(x, y, 0, x, y, r)
    gradient.addColorStop(0, `rgba(${b.color}, ${appStore.dark ? 0.5 : 0.45})`)
    gradient.addColorStop(1, `rgba(${b.color}, 0)`)
    ctx.fillStyle = gradient
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.globalCompositeOperation = 'source-over'
}

function loop() {
  if (!running) return
  t += 16.7
  drawFrame()
  rafId = requestAnimationFrame(loop)
}

function start() {
  if (lowPerf) {
    drawFrame()
    return
  }
  running = true
  rafId = requestAnimationFrame(loop)
}

function stop() {
  running = false
  cancelAnimationFrame(rafId)
}

function onVisibility() {
  if (document.hidden) {
    stop()
  } else {
    start()
  }
}

watch(
  () => appStore.dark,
  () => {
    if (lowPerf) drawFrame()
  }
)

onMounted(() => {
  setup()
  start()
  window.addEventListener('resize', resize)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  stop()
  window.removeEventListener('resize', resize)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<style scoped>
.liquid-bg {
  position: fixed;
  inset: 0;
  z-index: -1;
  display: block;
}
</style>
