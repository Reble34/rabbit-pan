# ============ 构建阶段 ============
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --registry=https://registry.npmmirror.com --no-audit --no-fund

COPY . .
RUN npm run build

# ============ 运行阶段：Nginx 托管静态资源并反代后端 ============
FROM nginx:alpine
ENV TZ=Asia/Shanghai

# 站点配置（静态资源 + /api 反代 + /s 短链反代）
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
