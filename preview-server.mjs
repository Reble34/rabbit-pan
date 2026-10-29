/**
 * Rabbit 网盘 · UI 预览服务器（仅本地预览用）
 *
 * 托管前端 dist 产物，并用内存 Mock 数据模拟后端 /api/v1 接口，
 * 使登录、网盘、回收站、分享、管理后台等页面 UI 可完整浏览与交互。
 * 真实功能请按 docs/部署文档.md 使用 Docker 部署。
 *
 * 启动：node preview-server.mjs  →  http://localhost:4173
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DIST = path.join(__dirname, 'rabbit-pan-frontend', 'dist')
const PORT = 4173
const BASE = `http://localhost:${PORT}`

// ==================== 工具 ====================
const pad = (n) => String(n).padStart(2, '0')
const fmt = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
const nowStr = () => fmt(new Date())
const daysLater = (n) => fmt(new Date(Date.now() + n * 86400000))

function json(res, data, code = 200) {
  const body = JSON.stringify({ code, message: code === 200 ? 'success' : 'fail', data })
  res.writeHead(code === 200 ? 200 : 200, { 'Content-Type': 'application/json;charset=UTF-8' })
  res.end(body)
}
function bizFail(res, errCode, message) {
  res.writeHead(200, { 'Content-Type': 'application/json;charset=UTF-8' })
  res.end(JSON.stringify({ code: errCode, message, data: null }))
}
function readBody(req) {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (c) => (raw += c))
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
  })
}
function pageOf(list, pageNum, pageSize) {
  const total = list.length
  const records = list.slice((pageNum - 1) * pageSize, pageNum * pageSize)
  return { records, total, size: pageSize, current: pageNum, pages: Math.ceil(total / pageSize) || 1 }
}
function svgPlaceholder(label, hue) {
  const h1 = hue % 360
  const h2 = (h1 + 60) % 360
  const text = label.replace(/&/g, '&amp;').replace(/</g, '&lt;')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="420">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
<stop offset="0" stop-color="hsl(${h1},70%,60%)"/><stop offset="1" stop-color="hsl(${h2},70%,45%)"/>
</linearGradient></defs>
<rect width="640" height="420" fill="url(#g)"/>
<circle cx="520" cy="80" r="120" fill="rgba(255,255,255,0.18)"/>
<circle cx="120" cy="340" r="90" fill="rgba(255,255,255,0.12)"/>
<text x="320" y="225" font-size="30" fill="rgba(255,255,255,0.92)" text-anchor="middle"
 font-family="PingFang SC,Microsoft YaHei,sans-serif">${text}</text></svg>`
}
function hashHue(str) {
  let h = 0
  for (const c of str) h = (h * 31 + c.charCodeAt(0)) % 997
  return (h * 7) % 360
}

// ==================== 内存数据 ====================
let nextId = 5000
const mkFile = (id, parentId, fileName, isDir, size, fileType, ext) => ({
  id, parentId, fileName, isDir, size, fileType, ext,
  md5: isDir ? null : 'mock' + id,
  createTime: nowStr(), updateTime: nowStr()
})

const files = [
  mkFile(1, 0, '项目资料', true, 0, null, null),
  mkFile(11, 1, '需求文档.md', false, 18432, 'md', 'md'),
  mkFile(12, 1, '架构图.png', false, 482300, 'image', 'png'),
  mkFile(13, 1, '发布计划.xlsx', false, 35840, 'doc', 'xlsx'),
  mkFile(2, 0, '照片备份', true, 0, null, null),
  mkFile(21, 2, '海边日落.jpg', false, 2411000, 'image', 'jpg'),
  mkFile(22, 2, '城市夜景.png', false, 3355000, 'image', 'png'),
  mkFile(23, 2, '2026 春季', true, 0, null, null),
  mkFile(31, 23, '樱花.jpg', false, 1980000, 'image', 'jpg'),
  mkFile(32, 23, '踏青.png', false, 2210000, 'image', 'png'),
  mkFile(3, 0, '产品演示.mp4', false, 158544000, 'video', 'mp4'),
  mkFile(4, 0, '背景音乐.mp3', false, 5242000, 'audio', 'mp3'),
  mkFile(5, 0, '年度报告.pdf', false, 2831104, 'pdf', 'pdf'),
  mkFile(6, 0, '学习笔记.md', false, 12288, 'md', 'md'),
  mkFile(7, 0, '数据导出.csv', false, 458752, 'txt', 'csv'),
  mkFile(8, 0, '安装包.zip', false, 104857600, 'zip', 'zip'),
  mkFile(9, 0, '使用说明.txt', false, 2048, 'txt', 'txt'),
  mkFile(10, 0, '头像.webp', false, 88320, 'image', 'webp')
]
const recycle = []
// 注册口令码（Mock 预置，管理员后台可再生成）
const inviteCodes = [
  { id: 1, code: 'Demo2026Code', status: 1, usedBy: null, usedTime: null, createTime: nowStr() },
  { id: 2, code: 'Rabbit2026A', status: 1, usedBy: null, usedTime: null, createTime: nowStr() },
  { id: 3, code: 'Pan2026CodeX', status: 0, usedBy: 2, usedTime: nowStr(), createTime: nowStr() }
]
let inviteNextId = 100
const TOTAL_GB = 30
const USER_GB = 5
const shares = [
  {
    id: 1, shareId: 189234567890123456, shortCode: '8xQ2kP9zR4aL', fileId: 2,
    expire: daysLater(7), password: null, maxView: -1, viewCount: 23,
    canDownload: true, status: 1, createTime: nowStr()
  }
]
const shareTokens = new Set(['mock-share-token'])

const findFile = (id) => files.find((f) => f.id === Number(id))
const childrenOf = (pid) => files.filter((f) => f.parentId === Number(pid) && !recycle.includes(f))
const vo = (f) => ({ ...f, isDir: !!f.isDir })

// ==================== 静态资源 ====================
const MIME = {
  '.html': 'text/html;charset=utf-8', '.js': 'application/javascript', '.css': 'text/css',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
  '.woff2': 'font/woff2', '.woff': 'font/woff'
}
function serveStatic(res, urlPath) {
  let filePath = path.join(DIST, decodeURIComponent(urlPath.split('?')[0]))
  if (!filePath.startsWith(DIST)) filePath = path.join(DIST, 'index.html')
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(DIST, 'index.html') // history 路由 fallback
  }
  const ext = path.extname(filePath).toLowerCase()
  res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' })
  fs.createReadStream(filePath).pipe(res)
}

// ==================== 路由 ====================
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, BASE)
  const p = url.pathname
  const q = url.searchParams
  const method = req.method

  // ---------- 短链 302 ----------
  if (p.startsWith('/s/')) {
    const code = p.slice(3)
    if (shares.some((s) => s.shortCode === code && s.status === 1)) {
      res.writeHead(302, { Location: `/share/${code}` })
      return res.end()
    }
    res.writeHead(302, { Location: `/share/${code}` })
    return res.end()
  }

  // ---------- 公开分享 ----------
  const shareCodeOf = (path) => {
    const m = path.match(/^\/api\/v1\/share\/([^/]+)/)
    return m ? m[1] : null
  }
  if (method === 'GET' && /^\/api\/v1\/share\/[^/]+\/info$/.test(p)) {
    const s = shares.find((x) => x.shortCode === shareCodeOf(p))
    if (!s) return bizFail(res, 1009, '分享链接不存在或已失效')
    const f = findFile(s.fileId)
    return json(res, {
      shortCode: s.shortCode, fileId: f.id, fileName: f.fileName, fileSize: f.size,
      isDir: !!f.isDir, needPassword: !!s.password, expireTime: s.expire,
      canDownload: !!s.canDownload, ownerName: '管理员'
    })
  }
  if (method === 'POST' && /^\/api\/v1\/share\/[^/]+\/verify$/.test(p)) {
    const s = shares.find((x) => x.shortCode === shareCodeOf(p))
    if (s && s.password) {
      const body = await readBody(req)
      if (body.password !== s.password) return bizFail(res, 1011, '提取码错误')
    }
    return json(res, { shareToken: 'mock-share-token' })
  }
  if (method === 'GET' && /^\/api\/v1\/share\/[^/]+\/list$/.test(p)) {
    const s = shares.find((x) => x.shortCode === shareCodeOf(p))
    if (!s) return bizFail(res, 1009, '分享链接不存在或已失效')
    const dirId = Number(q.get('dirId') || 0)
    const root = findFile(s.fileId)
    const target = dirId === 0 ? root : findFile(dirId)
    const list = childrenOf(target.id).map(vo).sort((a, b) => (b.isDir - a.isDir))
    return json(res, list)
  }
  if (method === 'GET' && /^\/api\/v1\/share\/[^/]+\/download\/\d+$/.test(p)) {
    res.writeHead(200, {
      'Content-Type': 'text/plain;charset=utf-8',
      'Content-Disposition': 'attachment; filename="mock-download.txt"'
    })
    return res.end('这是 Rabbit 网盘 UI 预览的模拟下载文件。真实部署后此处为 MinIO 文件流。')
  }
  if (method === 'GET' && /\/preview\/\d+$/.test(p)) {
    const fileId = Number(p.split('/').pop())
    const f = findFile(fileId)
    if (f && f.fileType === 'image') {
      res.writeHead(200, { 'Content-Type': 'image/svg+xml' })
      return res.end(svgPlaceholder(f.fileName, hashHue(f.fileName)))
    }
    if (f && (f.fileType === 'txt' || f.fileType === 'md')) {
      res.writeHead(200, { 'Content-Type': 'text/plain;charset=utf-8' })
      return res.end(
        `# ${f.fileName}\n\n这是 **Rabbit 网盘 UI 预览** 的模拟文本内容。\n\n` +
        `- Markdown 由前端 marked.js 渲染\n- 真实部署后由后端从 MinIO 读取文件流\n\n` +
        '## 列表\n\n1. 支持在线预览\n2. 支持断点续传\n3. 支持 12 位短链分享\n'
      )
    }
    // pdf / video / audio：返回占位 HTML（video 标签会显示空白，重点展示预览弹窗 UI）
    res.writeHead(200, { 'Content-Type': 'text/html;charset=utf-8' })
    return res.end(
      `<body style="margin:0;display:flex;align-items:center;justify-content:center;height:100vh;
       background:#111;color:#9ad;font-family:sans-serif">媒体预览占位（UI 预览模式）</body>`
    )
  }

  // ---------- 认证 ----------
  if (method === 'POST' && p === '/api/v1/user/login') {
    return json(res, {
      accessToken: 'mock-access-token', refreshToken: 'mock-refresh-token', accessExpiresIn: 7200,
      user: mockUser()
    })
  }
  if (method === 'POST' && p === '/api/v1/user/register') {
    const body = await readBody(req)
    const invite = inviteCodes.find((i) => i.code === (body.inviteCode || '').trim() && i.status === 1)
    if (!invite) return bizFail(res, 1017, '口令码无效或已被使用（预览环境可用 Demo2026Code）')
    invite.status = 0
    invite.usedBy = 99
    invite.usedTime = nowStr()
    return json(res, null)
  }
  if (method === 'GET' && p === '/api/v1/user/info') return json(res, mockUser())
  if (method === 'GET' && p === '/api/v1/user/avatar/1') {
    res.writeHead(200, { 'Content-Type': 'image/svg+xml' })
    return res.end(svgPlaceholder('管', 210))
  }
  if (method === 'GET' && p === '/api/v1/user/logs') {
    return json(res, pageOf([
      { id: 3, action: 'LOGIN', detail: '用户登录: admin', ip: '127.0.0.1', createTime: nowStr() },
      { id: 2, action: 'UPLOAD', detail: '直传文件: 年度报告.pdf', ip: '127.0.0.1', createTime: nowStr() },
      { id: 1, action: 'SHARE', detail: '创建分享: 照片备份', ip: '127.0.0.1', createTime: nowStr() }
    ], Number(q.get('pageNum') || 1), Number(q.get('pageSize') || 10)))
  }
  if (p === '/api/v1/user/nickname' || p === '/api/v1/user/password') return json(res, null)

  // ---------- 存储 ----------
  if (method === 'GET' && p === '/api/v1/storage/usage') {
    const used = files.filter((f) => !f.isDir && !recycle.includes(f)).reduce((s, f) => s + f.size, 0)
    const total = TOTAL_GB * 1024 ** 3
    return json(res, {
      used, total, remain: total - used,
      fileCount: files.filter((f) => !f.isDir && !recycle.includes(f)).length,
      percent: Math.round((used / total) * 10000) / 100
    })
  }

  // ---------- 文件 ----------
  if (method === 'GET' && (p === '/api/v1/file/list' || p === '/api/v1/file/search')) {
    const keyword = q.get('keyword')
    const type = q.get('type')
    let list = files.filter((f) => !recycle.includes(f))
    if (p.endsWith('/search') && (keyword || type || q.get('recent'))) {
      if (keyword) list = list.filter((f) => f.fileName.includes(keyword))
      if (type) list = list.filter((f) => f.fileType === type)
      list = list.slice().reverse() // 最近：按时间倒序
    } else if (p.endsWith('/search')) {
      if (keyword) list = list.filter((f) => f.fileName.includes(keyword))
      if (type) list = list.filter((f) => f.fileType === type)
    } else {
      const parentId = Number(q.get('parentId') || 0)
      list = childrenOf(parentId)
      list = list.slice().sort((a, b) => Number(b.isDir) - Number(a.isDir))
    }
    return json(res, pageOf(list, Number(q.get('pageNum') || 1), Number(q.get('pageSize') || 60)))
  }
  if (method === 'POST' && p === '/api/v1/file/folder') {
    const body = await readBody(req)
    const f = mkFile(++nextId, Number(body.parentId || 0), body.fileName, true, 0, null, null)
    files.push(f)
    return json(res, vo(f))
  }
  if (method === 'PUT' && p === '/api/v1/file/rename') {
    const body = await readBody(req)
    const f = findFile(body.fileId)
    if (f) f.fileName = body.fileName
    return json(res, null)
  }
  if ((method === 'PUT' && p === '/api/v1/file/move') || (method === 'POST' && p === '/api/v1/file/copy')) {
    const body = await readBody(req)
    const target = Number(body.targetParentId || 0)
    for (const id of body.fileIds || []) {
      const f = findFile(id)
      if (!f) continue
      if (method === 'PUT') f.parentId = target
      else {
        const copy = { ...f, id: ++nextId, parentId: target, fileName: f.fileName + ' (副本)' }
        files.push(copy)
      }
    }
    return json(res, method === 'PUT' ? null : { copied: (body.fileIds || []).length })
  }
  if (method === 'DELETE' && p === '/api/v1/file/delete') {
    const ids = (q.get('fileIds') || '').split(',').map(Number).filter(Boolean)
    for (const id of ids) if (!recycle.includes(id)) recycle.push(id)
    return json(res, { deleted: ids.length })
  }
  if (method === 'POST' && p === '/api/v1/file/batch') {
    const body = await readBody(req)
    if (body.operation === 'delete') {
      for (const id of body.fileIds || []) if (!recycle.includes(id)) recycle.push(id)
      return json(res, { deleted: (body.fileIds || []).length })
    }
    const target = Number(body.targetParentId || 0)
    for (const id of body.fileIds || []) {
      const f = findFile(id)
      if (!f) continue
      if (body.operation === 'move') f.parentId = target
      else files.push({ ...f, id: ++nextId, parentId: target, fileName: f.fileName + ' (副本)' })
    }
    return json(res, { ok: true })
  }
  if (method === 'GET' && /^\/api\/v1\/file\/\d+$/.test(p)) return json(res, vo(findFile(Number(p.split('/').pop()))))

  // ---------- 上传（秒传演示） ----------
  if (method === 'POST' && p === '/api/v1/upload/check') {
    const body = await readBody(req)
    const f = mkFile(++nextId, Number(body.parentDirId || 0), body.fileName, false,
      body.fileSize || 1024, guessType(body.fileName), (body.fileName.split('.').pop() || '').toLowerCase())
    f.md5 = body.md5
    files.push(f)
    return json(res, { finished: true, fileId: f.id })
  }
  if (method === 'POST' && p === '/api/v1/upload/init') {
    return json(res, { uploadId: 'mock-upload', chunkSize: 5242880, uploadedChunks: [] })
  }
  if (p === '/api/v1/upload/chunk') return json(res, null)
  if (method === 'POST' && p === '/api/v1/upload/merge') return json(res, { fileId: ++nextId })
  if (method === 'DELETE' && /^\/api\/v1\/upload\//.test(p)) return json(res, null)

  // ---------- 下载/打包 ----------
  if (method === 'GET' && /^\/api\/v1\/download\/\d+$/.test(p)) {
    res.writeHead(200, {
      'Content-Type': 'text/plain;charset=utf-8',
      'Content-Disposition': 'attachment; filename="mock-download.txt"'
    })
    return res.end('Rabbit 网盘 UI 预览的模拟下载。')
  }
  if (method === 'POST' && p === '/api/v1/download/batch') return json(res, { taskId: 'mockzip' })
  if (method === 'GET' && p === '/api/v1/download/zip/mockzip') {
    return json(res, { status: 1, url: '/api/v1/download/zip/file/mockzip' })
  }
  if (method === 'GET' && p === '/api/v1/download/zip/file/mockzip') {
    res.writeHead(200, {
      'Content-Type': 'application/zip',
      'Content-Disposition': 'attachment; filename="mock.zip"'
    })
    return res.end('PK\x05\x06' + 'Rabbit 网盘 UI 预览打包占位')
  }

  // ---------- 预览（个人） ----------
  if (method === 'GET' && /^\/api\/v1\/preview\/image\/\d+$/.test(p)) {
    const f = findFile(Number(p.split('/').pop()))
    res.writeHead(200, { 'Content-Type': 'image/svg+xml' })
    return res.end(svgPlaceholder(f ? f.fileName : '图片', f ? hashHue(f.fileName) : 210))
  }
  if (method === 'GET' && /^\/api\/v1\/preview\/(video|doc)\/\d+$/.test(p)) {
    const f = findFile(Number(p.split('/').pop()))
    if (f && (f.fileType === 'txt' || f.fileType === 'md')) {
      res.writeHead(200, { 'Content-Type': 'text/plain;charset=utf-8' })
      return res.end(
        `# ${f.fileName}\n\n这是 **Rabbit 网盘 UI 预览** 的模拟文本内容。\n\n` +
        '## 要点\n\n- 前端 marked 渲染 Markdown\n- 后端从 MinIO 流式读取\n- 支持大文件分片上传\n'
      )
    }
    if (f && f.fileType === 'pdf') {
      res.writeHead(200, { 'Content-Type': 'text/html;charset=utf-8' })
      return res.end(
        `<body style="margin:0;display:flex;align-items:center;justify-content:center;height:100vh;
         background:#f4f6fa;color:#555;font-family:sans-serif">PDF 预览占位（UI 预览模式下返回占位页）</body>`
      )
    }
    res.writeHead(200, { 'Content-Type': 'video/mp4' })
    return res.end()
  }
  if (method === 'GET' && /^\/api\/v1\/preview\/zip\/\d+$/.test(p)) {
    return json(res, [
      { name: 'docs/', isDir: true, size: 0 },
      { name: 'docs/README.md', isDir: false, size: 2048 },
      { name: 'bin/', isDir: true, size: 0 },
      { name: 'bin/app.jar', isDir: false, size: 52428800 },
      { name: 'config.yml', isDir: false, size: 1024 }
    ])
  }
  if (method === 'GET' && p === '/api/v1/preview/zip/extract') {
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Content-Disposition': 'attachment' })
    return res.end('mock entry')
  }

  // ---------- 回收站 ----------
  if (method === 'GET' && p === '/api/v1/recycle/list') {
    const list = recycle.map((id) => {
      const f = findFile(id)
      return f ? { ...vo(f), deleteTime: nowStr(), recycleDaysLeft: 30 } : null
    }).filter(Boolean)
    return json(res, pageOf(list, Number(q.get('pageNum') || 1), Number(q.get('pageSize') || 50)))
  }
  if (method === 'POST' && p === '/api/v1/recycle/restore') {
    const body = await readBody(req)
    for (const id of body.fileIds || []) {
      const i = recycle.indexOf(Number(id))
      if (i >= 0) recycle.splice(i, 1)
    }
    return json(res, { restored: (body.fileIds || []).length })
  }
  if (method === 'DELETE' && p === '/api/v1/recycle/delete') {
    const body = await readBody(req)
    for (const id of body || []) {
      const i = recycle.indexOf(Number(id))
      if (i >= 0) recycle.splice(i, 1)
      const fi = files.findIndex((f) => f.id === Number(id))
      if (fi >= 0) files.splice(fi, 1)
    }
    return json(res, { deleted: (body || []).length })
  }
  if (method === 'DELETE' && p === '/api/v1/recycle/clear') {
    for (const id of recycle) {
      const fi = files.findIndex((f) => f.id === id)
      if (fi >= 0) files.splice(fi, 1)
    }
    recycle.length = 0
    return json(res, { cleared: 0 })
  }

  // ---------- 分享管理 ----------
  if (method === 'POST' && p === '/api/v1/share/create') {
    const body = await readBody(req)
    const f = findFile(body.fileId)
    const shortCode = Math.random().toString(36).replace(/[^a-z0-9]/g, '').padEnd(12, 'x').slice(0, 12)
    const s = {
      id: shares.length + 1, shareId: Date.now() * 1000, shortCode, fileId: body.fileId,
      expire: body.expireDays > 0 ? daysLater(body.expireDays) : null,
      password: body.password || null, maxView: body.maxView ?? -1, viewCount: 0,
      canDownload: body.canDownload !== false, status: 1, createTime: nowStr()
    }
    shares.push(s)
    return json(res, {
      id: s.id, shareId: s.shareId, shortCode: s.shortCode, shortUrl: `${BASE}/s/${shortCode}`,
      fileId: s.fileId, fileName: f ? f.fileName : '未知', fileSize: f ? f.size : 0,
      isDir: f ? !!f.isDir : false, shareType: f && f.isDir ? 2 : 1,
      hasPassword: !!s.password, maxView: s.maxView, viewCount: 0,
      canDownload: s.canDownload, expireTime: s.expire, status: 1, createTime: s.createTime
    })
  }
  if (method === 'GET' && p === '/api/v1/share/list') {
    const list = shares.map((s) => {
      const f = findFile(s.fileId)
      return {
        id: s.id, shareId: s.shareId, shortCode: s.shortCode, shortUrl: `${BASE}/s/${s.shortCode}`,
        fileId: s.fileId, fileName: f ? f.fileName : '已删除', fileSize: f ? f.size : 0,
        isDir: f ? !!f.isDir : false, hasPassword: !!s.password, maxView: s.maxView,
        viewCount: s.viewCount, canDownload: !!s.canDownload, expireTime: s.expire,
        status: s.status, createTime: s.createTime
      }
    })
    return json(res, pageOf(list, Number(q.get('pageNum') || 1), Number(q.get('pageSize') || 10)))
  }
  if (method === 'DELETE' && /^\/api\/v1\/share\/\d+$/.test(p)) {
    const id = Number(p.split('/').pop())
    const i = shares.findIndex((s) => s.id === id)
    if (i >= 0) shares.splice(i, 1)
    return json(res, null)
  }
  if (method === 'PUT' && /^\/api\/v1\/share\/\d+$/.test(p)) {
    const body = await readBody(req)
    const s = shares.find((x) => x.id === Number(p.split('/').pop()))
    if (s) {
      if (body.expireDays !== undefined) s.expire = body.expireDays > 0 ? daysLater(body.expireDays) : null
      if (body.password) s.password = body.password
      if (body.maxView !== undefined) s.maxView = body.maxView
      if (body.canDownload !== undefined) s.canDownload = body.canDownload
    }
    return json(res, null)
  }

  // ---------- 管理后台 ----------
  if (method === 'GET' && p === '/api/v1/admin/statistics') {
    const used = files.filter((f) => !f.isDir).reduce((s, f) => s + f.size, 0)
    return json(res, {
      userCount: 3, fileCount: files.filter((f) => !f.isDir).length,
      storageUsed: used, storageTotal: TOTAL_GB * 1024 ** 3,
      todayOpCount: 42, todayLoginCount: 5
    })
  }
  if (method === 'GET' && p === '/api/v1/admin/user/list') {
    const list = [
      { id: 1, username: 'admin', nickname: '管理员', avatar: null, role: 'ADMIN', status: 1, storageUsed: 3.2 * 1024 ** 3, storageTotal: TOTAL_GB * 1024 ** 3 },
      { id: 2, username: 'tom', nickname: '汤姆', avatar: null, role: 'USER', status: 1, storageUsed: 1.1 * 1024 ** 3, storageTotal: USER_GB * 1024 ** 3 },
      { id: 3, username: 'jerry', nickname: '杰瑞', avatar: null, role: 'USER', status: 0, storageUsed: 0.4 * 1024 ** 3, storageTotal: USER_GB * 1024 ** 3 }
    ]
    return json(res, pageOf(list, Number(q.get('pageNum') || 1), Number(q.get('pageSize') || 10)))
  }
  if (p.startsWith('/api/v1/admin/user')) return json(res, 1)
  // ---------- 口令码 ----------
  if (method === 'GET' && p === '/api/v1/admin/invite/list') {
    return json(res, pageOf(inviteCodes, Number(q.get('pageNum') || 1), Number(q.get('pageSize') || 10)))
  }
  if (method === 'POST' && p === '/api/v1/admin/invite/generate') {
    const body = await readBody(req)
    const created = []
    for (let i = 0; i < Math.min(50, Number(body.count || 1)); i++) {
      const code = Array.from({ length: 12 }, () =>
        '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'[Math.floor(Math.random() * 62)]).join('')
      const item = { id: ++inviteNextId, code, status: 1, usedBy: null, usedTime: null, createTime: nowStr() }
      inviteCodes.push(item)
      created.push(item)
    }
    return json(res, created)
  }
  if (method === 'DELETE' && /^\/api\/v1\/admin\/invite\/\d+$/.test(p)) {
    const invite = inviteCodes.find((i) => i.id === Number(p.split('/').pop()) && i.status === 1)
    if (invite) invite.status = 0
    return json(res, null)
  }
  if (method === 'GET' && p === '/api/v1/admin/config') {
    return json(res, {
      maxFileMb: 2048,
      allowedExts: 'jpg,jpeg,png,gif,webp,mp4,mp3,wav,pdf,txt,md,csv,zip,doc,docx,xls,xlsx,ppt,pptx',
      recycleKeepDays: 30, defaultQuotaGb: USER_GB, totalStorageGb: TOTAL_GB
    })
  }
  if (method === 'PUT' && p === '/api/v1/admin/config') return json(res, null)
  if (method === 'GET' && p === '/api/v1/admin/file/search') {
    return json(res, pageOf(files.filter((f) => !f.isDir).map((f) => ({ ...f, userId: 1 })),
      Number(q.get('pageNum') || 1), Number(q.get('pageSize') || 20)))
  }

  // 兜底：其余 API 一律成功，避免 UI 报错
  if (p.startsWith('/api/')) return json(res, null)

  return serveStatic(res, p)
})

function mockUser() {
  const used = files.filter((f) => !f.isDir && !recycle.includes(f)).reduce((s, f) => s + f.size, 0)
  const total = TOTAL_GB * 1024 ** 3
  return {
    id: 1, username: 'admin', nickname: '管理员',
    avatar: '/api/v1/user/avatar/1?v=mock', role: 'ADMIN', status: 1,
    storageUsed: used, storageTotal: total,
    fileCount: files.filter((f) => !f.isDir && !recycle.includes(f)).length,
    createTime: '2026-10-24 12:00:00'
  }
}

function guessType(name) {
  const ext = (name.split('.').pop() || '').toLowerCase()
  const map = { jpg: 'image', jpeg: 'image', png: 'image', gif: 'image', webp: 'image', bmp: 'image',
    mp4: 'video', webm: 'video', mov: 'video', mp3: 'audio', wav: 'audio', flac: 'audio',
    pdf: 'pdf', txt: 'txt', md: 'md', zip: 'zip', doc: 'doc', docx: 'doc', xls: 'doc', xlsx: 'doc' }
  return map[ext] || 'other'
}

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Rabbit 网盘 UI 预览已启动: ${BASE}`)
  console.log('提示: 登录页任意输入账号密码即可进入（Mock 数据，重启还原）')
})
