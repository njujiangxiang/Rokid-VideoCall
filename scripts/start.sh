#!/bin/bash

# Rokid Glasses 视频连线系统 - 启动脚本

echo "╔═══════════════════════════════════════════════════════════╗"
echo "║                                                           ║"
echo "║   🦞 Rokid Glasses 电力场景视频连线系统                      ║"
echo "║                                                           ║"
echo "║   快速启动脚本                                             ║"
echo "║                                                           ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "❌ 错误：未找到 Node.js"
    echo "请先安装 Node.js (https://nodejs.org/)"
    exit 1
fi

echo "✅ Node.js 版本：$(node -v)"
echo ""

# 启动后端服务器
echo "🚀 启动后端服务器..."
cd backend-server

if [ ! -d "node_modules" ]; then
    echo "📦 安装依赖..."
    npm install
fi

echo ""
echo "启动命令：npm run dev"
echo ""
echo "服务器地址："
echo "  - WebSocket: ws://localhost:3000/ws"
echo "  - REST API:  http://localhost:3000/api/v1"
echo "  - 管理台：   http://localhost:3000"
echo ""
echo "按 Ctrl+C 停止服务器"
echo ""

npm run dev
