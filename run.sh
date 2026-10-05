#!/usr/bin/env bash
# =============================================================================
# 一条命令启动:构建前端(Svelte/Vite) -> 构建后端(Quarkus) -> 启动
# 访问: http://localhost:8080
# 首次运行会自动下载 JDK/Maven 之外的依赖;JDK 缺失时会提示。
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

# ---- JDK / Maven(本机已安装在用户目录时自动启用) ----
if [ -d "$HOME/tools/jdk-17.0.13+11" ]; then
  export JAVA_HOME="$HOME/tools/jdk-17.0.13+11"
  export PATH="$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.9/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
  echo "[错误] 未找到 java,请先安装 JDK 17+。" >&2
  exit 1
fi

mkdir -p data

# ---- 1. 前端(仅在 node_modules 或产物缺失时构建) ----
if [ -d frontend/node_modules ]; then :; else
  echo "[1/3] 安装前端依赖..."
  (cd frontend && npm install --no-audit --no-fund)
fi

if [ "${SKIP_FRONTEND:-0}" != "1" ]; then
  echo "[1/3] 构建 Svelte 前端 -> Quarkus 静态资源..."
  (cd frontend && npm run build)
fi

# ---- 2. 后端打包 ----
echo "[2/3] 构建 Quarkus 后端..."
mvn -B package -DskipTests

# ---- 3. 启动 ----
echo "[3/3] 启动服务: http://localhost:8080 (Ctrl+C 退出)"
exec java -jar target/quarkus-app/quarkus-run.jar
