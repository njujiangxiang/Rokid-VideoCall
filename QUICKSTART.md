# 🚀 快速开始指南

> 5 分钟启动后端服务器

---

## 方式一：一键启动（推荐）

```bash
# 进入项目目录
cd ~/Desktop/Rokid-VideoCall-Demo/backend-server

# 运行启动脚本
./start.sh
```

脚本会自动：
- ✅ 创建 Python 虚拟环境
- ✅ 安装所有依赖
- ✅ 启动服务器

**访问**：http://localhost:8765

---

## 方式二：手动部署

### 1. 检查环境

```bash
# Python 版本检查（需要 3.10+）
python3 --version

# 如果版本过低，升级 Python
# macOS: brew install python@3.10
# Ubuntu: sudo apt install python3.10
```

### 2. 安装依赖

```bash
cd ~/Desktop/Rokid-VideoCall-Demo/backend-server

# 创建虚拟环境
python3 -m venv venv

# 激活虚拟环境
source venv/bin/activate  # macOS/Linux
# 或
venv\Scripts\activate     # Windows

# 安装依赖
pip install -r requirements.txt
```

### 3. 启动服务器

```bash
cd app
python -m uvicorn main:app --host 0.0.0.0 --port 8765 --reload
```

---

## 验证安装

### 检查服务器状态

打开浏览器访问：http://localhost:8765/health

应该看到：
```json
{
  "status": "healthy",
  "timestamp": "2026-03-11T20:00:00",
  "active_sessions": 0,
  "total_sessions": 0
}
```

### 测试 API

```bash
# 创建会话
curl -X POST http://localhost:8765/api/sessions \
  -H "Content-Type: application/json" \
  -d '{"operator_id": "test_001", "device_id": "ROKID_001"}'

# 获取会话列表
curl http://localhost:8765/api/sessions
```

---

## 下一步

### ✅ 后端已启动

现在可以：

1. **打开 Web 管理后台**
   - 访问 http://localhost:8765
   - 创建新会话
   - 测试视频通话功能

2. **编译 Android 应用**
   - 参考 `android-app/README.md`
   - 使用 Android Studio 打开项目
   - 编译并安装到手机

3. **阅读详细文档**
   - 技术方案：`docs/技术方案文档.md`
   - 完整说明：`README.md`

---

## 故障排查

### 问题：端口被占用

```bash
# 检查端口占用
lsof -i :8765

# 杀死占用端口的进程
kill -9 <PID>

# 或者修改端口
python -m uvicorn main:app --port 8766
```

### 问题：依赖安装失败

```bash
# 升级 pip
pip install --upgrade pip

# 清除缓存重试
pip cache purge
pip install -r requirements.txt

# 如果 aiortc 安装失败（可选组件）
# 可以暂时注释掉 requirements.txt 中的 aiortc
```

### 问题：WebSocket 连接失败

```bash
# 检查防火墙
sudo ufw status
sudo ufw allow 8765/tcp

# 检查服务器日志
# 启动时添加 --log-level debug
python -m uvicorn main:app --log-level debug
```

---

## 常用命令

```bash
# 启动服务器
./start.sh

# 查看日志
tail -f backend-server/app/logs/*.log

# 停止服务器
# Ctrl + C

# 清理虚拟环境
rm -rf venv

# 重新安装依赖
pip install -r requirements.txt --force-reinstall
```

---

## 联系支持

遇到问题？

- 📖 查看完整文档：`README.md`
- 🐛 提交 Issue: [GitHub Issues]
- 💬 开发者论坛：https://forum.rokid.com

---

**祝开发顺利！** 🦞
