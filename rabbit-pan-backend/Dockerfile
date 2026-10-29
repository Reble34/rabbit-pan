# ============ 构建阶段 ============
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先复制 pom 下载依赖（利用 Docker 层缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -q -B

# 再复制源码打包
COPY src ./src
RUN mvn package -DskipTests -q -B

# ============ 运行阶段 ============
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai

COPY --from=build /app/target/rabbit-pan-backend.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx1024m", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
