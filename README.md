# Rabbit 网盘（Rabbit Pan）

私有云盘系统 前后端分离，Docker Compose 一键部署。

![技术栈](https://img.shields.io/badge/Vue3-Element%20Plus-0a84ff)

![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.2.x-6db33f)

![Storage](https://img.shields.io/badge/MinIO-S3%20兼容-red)

## 核心特性

| 模块       | 能力                                                                   |
| -------- | -------------------------------------------------------------------- |
| 文件管理     | 多级目录、批量移动/复制/删除/下载、列表/网格视图、排序、模糊搜索 + 类型/时间筛选                         |
| 12 位加密短链 | 雪花 ID + 盐值混淆 + Base62 定长 12 位，不可枚举不可逆；提取码 / 有效期 / 访问上限 / 仅预览权限       |
| 大文件上传    | 分片上传 + 断点续传 + 文件秒传（MD5）+ 实时进度/速度/剩余时间 + 暂停/取消 + 批量打包 ZIP             |
| 多格式预览    | 图片（缩略图）、音视频（Range 拖动）、PDF/TXT/Markdown、ZIP 目录树与单文件提取                 |
| 回收站      | 删除进回收站不释放空间、恢复原路径、永久删除、按保留天数自动清理                                     |
| 存储管控     | 系统总空间 30GB 共享上限 + 用户默认 5GB 配额（双重校验）、已用/总/剩余可视化、上传前配额校验、管理员单独分配用户配额   |
| 注册口令码    | 注册必须持有管理员生成的口令码（与短链同款加密算法：雪花+混淆+Base62 定长 12 位，不可枚举），一码一用，后台可批量生成/作废 |
| 账号体系     | 注册/登录/双令牌 JWT/修改密码/头像昵称；登录失败锁定、接口限流、全量操作日志                           |
| 管理后台     | 用户增删禁用配额、全局文件检索与违规删除、系统配置、数据看板                                       |

## 技术栈

- **前端**：Vue 3 + Vite 5 + Element Plus（按需引入）+ Pinia + axios，macOS 液态玻璃风格 UI（`LiquidBackground` 动态背景 + 三级玻璃材质）
- **后端**：Spring Boot 3.2 + Spring Security 6 + MyBatis-Plus + JWT 双令牌 + Knife4j 接口文档
- **存储**：MySQL 8.0（Flyway 迁移）+ Redis 7（短链/登录态/分片状态/限流）+ MinIO（S3 兼容对象存储）
- **部署**：Docker Compose 一键编排（Nginx / 前端 / 后端 / MySQL / Redis / MinIO），`restart: always`

## 快速开始（Docker 一键部署）

```bash
# 1. 准备环境变量
cp .env.example .env
# 编辑 .env：修改所有 CHANGE-ME 密钥（可用 openssl rand -hex 32 生成）

# 2. 一键构建并启动（首次构建约 3-8 分钟）
docker compose up -d --build

# 3. 查看后端启动日志（等待 Flyway 建表 + 管理员初始化完成）
docker compose logs -f backend
```

启动完成后访问：

- 网盘首页：`http://你的服务器IP/`
- 默认管理员：`admin / Admin@123456`（来自 `.env` 中 `ADMIN_PASSWORD`，**登录后请立即修改**）
- 存储策略：系统总空间 30GB（全部用户共享上限），新用户默认 5GB，均可在管理后台调整
- 新用户注册需口令码：管理后台 → 口令码 → 生成，把 12 位口令码发给需要注册的人
- 接口文档：`http://你的服务器IP/doc.html`（Knife4j）

## 本地开发

```bash
# 依赖：JDK 17、Maven 3.8+、Node 18+、MySQL8/Redis7/MinIO（可用 docker 单独起）
# 1. 起中间件
docker run -d --name rabbit-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8.0
docker run -d --name rabbit-redis -p 6379:6379 redis:7
docker run -d --name rabbit-minio -p 9000:9000 -p 9001:9001 minio/minio server /data --console-address ":9001"

# 2. 启动后端（默认 dev 配置连 localhost，MinIO 账号 minioadmin/minioadmin）
cd rabbit-pan-backend && mvn spring-boot:run

# 3. 启动前端（Vite 代理 /api -> 8080）
cd rabbit-pan-frontend && npm install && npm run dev
```

## 文档

| 文档                           | 内容                                  |
| ---------------------------- | ----------------------------------- |
| [docs/部署文档.md](docs/部署文档.md) | 环境准备、Docker 部署、HTTPS、备份恢复、监控告警、常见问题 |
| [docs/代码文档.md](docs/代码文档.md) | 架构设计、目录结构、12 位短链算法、分片上传流程、二次开发指南    |
| [docs/接口文档.md](docs/接口文档.md) | 全量 REST API 说明、请求/响应示例、错误码表         |

## 目录结构

```
rabbit-pan/
├── rabbit-pan-backend/       # Spring Boot 后端
│   └── src/main/resources/db/migration/   # Flyway 建表脚本
├── rabbit-pan-frontend/      # Vue3 前端（deploy/ 内含 Nginx 配置）
├── docker-compose.yml        # 一键编排
├── .env.example              # 环境变量样例
└── docs/                     # 部署 / 代码 / 接口文档
```

## 安全基线（生产必读）

1. 修改 `.env` 中所有默认密钥：`JWT_SECRET`、`SHARE_SALT`、`ADMIN_PASSWORD`、数据库与 MinIO 密码
2. 服务器只开放 80/443；MySQL/Redis/MinIO 不对公网暴露（compose 已默认仅内网）
3. 登录后立即修改 admin 密码；开启 HTTPS（见部署文档）
4. 定期备份：MySQL 每日全量 + MinIO 数据盘（见部署文档备份章节）
