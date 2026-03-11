#!/bin/bash

# Rokid VideoCall Server 启动脚本

echo "🦞 Rokid Glasses 远程视频连线系统 - 后端服务器"
echo "================================================"
echo ""

# 检查 Python 版本
python_version=$(python3 --version 2>&1 | awk '{print $2}')
echo "✅ Python 版本：$python_version"

# 创建虚拟环境（如果不存在）
if [ ! -d "venv" ]; then
    echo "📦 创建虚拟环境..."
    python3 -m venv venv
fi

# 激活虚拟环境
echo "🔌 激活虚拟环境..."
source venv/bin/activate

# 安装依赖
echo "📥 安装依赖..."
pip install -q -r requirements.txt

# 创建必要的目录
mkdir -p recordings
mkdir -p static

# 启动服务器
echo ""
echo "🚀 启动服务器..."
echo ""

cd app
python -m uvicorn main:app --host 0.0.0.0 --port 8765 --reload
