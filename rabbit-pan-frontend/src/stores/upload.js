import { defineStore } from 'pinia'
import SparkMD5 from 'spark-md5'
import { uploadApi } from '@/api/upload'

/** 分片上传引擎：MD5 计算 → 秒传校验 → 分片并发上传 → 合并；支持暂停/续传/取消 */
export const useUploadStore = defineStore('upload', {
  state: () => ({
    items: [],
    panelVisible: true
  }),
  actions: {
    /** 添加一批文件开始上传 */
    addFiles(fileList, parentDirId) {
      const files = Array.from(fileList || [])
      for (const file of files) {
        const item = {
          id: 'u' + Date.now() + Math.random().toString(36).slice(2, 8),
          name: file.name,
          size: file.size,
          file,
          parentDirId,
          status: 'hashing', // hashing -> checking -> uploading -> merging -> done | paused | error | instant
          progress: 0,
          uploadedBytes: 0,
          speed: 0,
          eta: 0,
          md5: null,
          uploadId: null,
          chunkSize: 0,
          chunksTotal: 0,
          error: null,
          paused: false,
          cancelled: false,
          instant: false
        }
        this.items.unshift(item)
        this.run(item)
      }
    },

    async run(item) {
      try {
        // 1) MD5（已计算过则跳过，断点续传复用）
        if (!item.md5) {
          item.md5 = await this.computeMd5(item)
        }
        if (item.paused || item.cancelled) return
        // 2) 秒传校验
        item.status = 'checking'
        const check = await uploadApi.check({
          fileName: item.name,
          fileSize: item.size,
          md5: item.md5,
          parentDirId: item.parentDirId
        })
        if (check && check.finished) {
          item.status = 'done'
          item.progress = 100
          item.instant = true
          return
        }
        // 3) 初始化分片
        const init = await uploadApi.init({
          fileName: item.name,
          fileSize: item.size,
          md5: item.md5,
          parentDirId: item.parentDirId
        })
        item.uploadId = init.uploadId
        item.chunkSize = init.chunkSize
        item.chunksTotal = Math.ceil(item.size / init.chunkSize)
        // 4) 并发上传分片（跳过已传）
        item.status = 'uploading'
        const uploaded = new Set(init.uploadedChunks || [])
        await this.uploadChunks(item, uploaded)
        if (item.paused || item.cancelled) return
        // 5) 合并
        item.status = 'merging'
        const merge = await uploadApi.merge({
          uploadId: item.uploadId,
          fileName: item.name,
          fileSize: item.size,
          md5: item.md5,
          parentDirId: item.parentDirId
        })
        item.progress = 100
        item.uploadedBytes = item.size
        item.status = 'done'
        item.fileId = merge && merge.fileId
      } catch (e) {
        if (item.cancelled) return
        item.status = item.paused ? 'paused' : 'error'
        item.error = (e && e.message) || '上传失败'
      }
    },

    /** 分片计算文件 MD5（8MB 步长增量读取） */
    computeMd5(item) {
      return new Promise((resolve, reject) => {
        const spark = new SparkMD5.ArrayBuffer()
        const sliceSize = 8 * 1024 * 1024
        let index = 0
        const reader = new FileReader()
        const next = () => {
          if (item.paused || item.cancelled) {
            reject(new Error('paused'))
            return
          }
          reader.readAsArrayBuffer(item.file.slice(index, index + sliceSize))
        }
        reader.onload = (e) => {
          spark.append(e.target.result)
          index += sliceSize
          item.progress = Math.min(99, Math.round((index / item.size) * 40)) // 哈希阶段映射到 0-40%
          if (index < item.size) {
            next()
          } else {
            resolve(spark.end())
          }
        }
        reader.onerror = () => reject(new Error('文件读取失败'))
        next()
      })
    },

    /** 并发上传分片（并发数 3） */
    async uploadChunks(item, uploaded) {
      const queue = []
      for (let i = 0; i < item.chunksTotal; i++) {
        if (!uploaded.has(i)) queue.push(i)
      }
      let done = uploaded.size
      const speedSamples = [{ t: Date.now(), bytes: uploaded.size * item.chunkSize }]
      const updateProgress = () => {
        const bytes = Math.min(item.size, done * item.chunkSize)
        item.uploadedBytes = bytes
        item.progress = 40 + Math.round((bytes / item.size) * 59) // 上传阶段映射到 40-99%
        const now = Date.now()
        speedSamples.push({ t: now, bytes })
        while (speedSamples.length > 2 && now - speedSamples[0].t > 3000) {
          speedSamples.shift()
        }
        const first = speedSamples[0]
        const dt = (now - first.t) / 1000
        item.speed = dt > 0 ? (bytes - first.bytes) / dt : 0
        const remain = item.size - bytes
        item.eta = item.speed > 0 ? remain / item.speed : 0
      }
      const worker = async () => {
        while (queue.length > 0) {
          if (item.paused || item.cancelled) return
          const idx = queue.shift()
          if (uploaded.has(idx)) continue
          try {
            const start = idx * item.chunkSize
            const blob = item.file.slice(start, Math.min(start + item.chunkSize, item.size))
            await uploadApi.chunk(item.uploadId, idx, blob)
            uploaded.add(idx)
            done++
            updateProgress()
          } catch (e) {
            if (item.paused || item.cancelled) return
            throw e
          }
        }
      }
      await Promise.all(Array.from({ length: Math.min(3, queue.length) }, worker))
      if (!item.paused && !item.cancelled && done < item.chunksTotal) {
        throw new Error('分片上传不完整')
      }
    },

    /** 暂停（进行中的分片完成后停止） */
    pause(item) {
      item.paused = true
      if (['uploading', 'hashing', 'checking', 'merging'].includes(item.status)) {
        item.status = 'paused'
      }
    },

    /** 续传：重新走流程（MD5 已缓存；服务端返回已传分片，秒传幂等） */
    resume(item) {
      item.paused = false
      item.error = null
      item.status = 'checking'
      this.run(item)
    },

    /** 取消并清理服务端分片 */
    async cancel(item) {
      item.cancelled = true
      item.paused = true
      if (item.uploadId) {
        try {
          await uploadApi.cancel(item.uploadId)
        } catch (e) {
          /* 忽略清理异常 */
        }
      }
      this.items = this.items.filter((i) => i.id !== item.id)
    },

    clearFinished() {
      this.items = this.items.filter((i) => !['done', 'error', 'instant'].includes(i.status) || i.status === 'done')
    }
  }
})
