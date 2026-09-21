# =================================================================
# 阶段 1: 构建阶段 (build)
# =================================================================
# Maven基础镜像
FROM maven:3.9-eclipse-temurin-17 AS build
# 设置工作目录
WORKDIR /app
# 复制pom
COPY pom.xml .
# 下载依赖
RUN mvn dependency:go-offline -B
# 复制源代码
COPY src ./src
# 构建
RUN mvn package -DskipTests

# =================================================================
# 阶段 2: 运行阶段
# =================================================================
# JDK17基础环境
FROM eclipse-temurin:17-jre-jammy
# 安装ffmpeg
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# 1. 将jar包放在一个专门的/app目录
COPY --from=build /app/target/*.jar /app/app.jar

# 2. 设置工作目录为/data。这是你的应用需要读写持久化数据的地方。
WORKDIR /data

# 3. 声明数据卷
VOLUME /data

# 4. 暴露端口
EXPOSE 8080

# 5. 启动命令使用绝对路径指向/app目录下的jar文件
ENTRYPOINT ["java", "-Duser.dir=/data", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
