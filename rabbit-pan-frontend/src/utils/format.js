/** 格式化工具：文件大小 / 时间 / 文件类型图标与颜色 */

/** 字节转可读大小 */
export function formatSize(bytes) {
  if (bytes === null || bytes === undefined) return '-'
  const n = Number(bytes)
  if (n === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let v = n
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

/** 时间格式化 yyyy-MM-dd HH:mm */
export function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 文件类型 -> 图标（Element Plus 图标名）与色调 */
const TYPE_META = {
  image: { icon: 'Picture', color: '#5ac8fa' },
  video: { icon: 'VideoPlay', color: '#ff9f0a' },
  audio: { icon: 'Headset', color: '#bf5af2' },
  pdf: { icon: 'Document', color: '#ff453a' },
  txt: { icon: 'Document', color: '#8e8e93' },
  md: { icon: 'Document', color: '#64d2ff' },
  doc: { icon: 'Tickets', color: '#0a84ff' },
  zip: { icon: 'Files', color: '#ffd60a' },
  other: { icon: 'Document', color: '#8e8e93' }
}

export function typeMeta(fileType) {
  return TYPE_META[fileType] || TYPE_META.other
}

/** 文件夹图标 */
export function isDir(item) {
  return item && (item.isDir === true || item.isDir === 1)
}

/** 上传速度格式化 */
export function formatSpeed(bytesPerSecond) {
  if (!bytesPerSecond || bytesPerSecond <= 0) return '0 B/s'
  return formatSize(bytesPerSecond) + '/s'
}

/** 剩余时间格式化 */
export function formatEta(seconds) {
  if (!seconds || !Number.isFinite(seconds) || seconds <= 0) return '--'
  if (seconds < 60) return `${Math.ceil(seconds)} 秒`
  if (seconds < 3600) return `${Math.ceil(seconds / 60)} 分钟`
  return `${Math.ceil(seconds / 3600)} 小时`
}
