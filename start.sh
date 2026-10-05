#!/usr/bin/env bash
# 工厂停机原因拆分与产能损失核算系统 —— 一条命令启动
# 用法: ./start.sh          构建前端 + 打包后端 + 启动（http://localhost:8080）
#       ./start.sh --dev    跳过前端构建，以 quarkus:dev 模式启动后端（前端另行 npm run dev）
set -euo pipefail
cd "$(dirname "$0")"

# ---- 定位 JDK（优先环境变量，其次本仓库自带工具目录，最后系统 java）----
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "$HOME/tools/jdk-21" "$HOME/tools/jdk-17" /usr/lib/jvm/java-21-openjdk-* /usr/lib/jvm/java-17-openjdk-*; do
    if [ -x "$candidate/bin/java" ]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
  echo "错误: 未找到 Java。请安装 JDK 17+ 或设置 JAVA_HOME。" >&2
  exit 1
fi

# ---- 定位 Maven（优先 ./mvnw，其次本仓库工具目录，其次系统 mvn）----
MVN=""
if [ -x "$HOME/tools/maven/bin/mvn" ]; then
  MVN="$HOME/tools/maven/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
elif [ -x backend/mvnw ]; then
  MVN="backend/mvnw"
else
  echo "错误: 未找到 Maven。请安装 Maven 或放置于 ~/tools/maven。" >&2
  exit 1
fi

echo "==> Java: $(java -version 2>&1 | head -1)"

# ---- 构建前端并拷入后端静态资源 ----
if [ "${1:-}" != "--dev" ]; then
  echo "==> 构建前端..."
  cd frontend
  if [ ! -d node_modules ]; then
    npm install --no-fund --no-audit
  fi
  npm run build
  cd ..
  echo "==> 拷贝前端产物到后端静态资源目录..."
  rm -rf backend/src/main/resources/META-INF/resources
  mkdir -p backend/src/main/resources/META-INF/resources
  cp -r frontend/dist/. backend/src/main/resources/META-INF/resources/
fi

# ---- 数据目录（SQLite）----
mkdir -p backend/data

if [ "${1:-}" = "--dev" ]; then
  echo "==> 以开发模式启动后端（前端请另开终端: cd frontend && npm run dev）"
  cd backend && exec $MVN quarkus:dev
fi

echo "==> 打包后端..."
(cd backend && $MVN -B -q package -DskipTests)

echo "==> 启动系统: http://localhost:8080"
cd backend && exec java -jar target/quarkus-app/quarkus-run.jar
