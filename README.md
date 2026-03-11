# 🥽 Rokid Glasses 远程视频连线系统

> 基于 WebRTC 的 AR 眼镜实时音视频通话解决方案

![版本](https://img.shields.io/badge/版本-1.0.0-blue)
![Python](https://img.shields.io/badge/Python-3.10+-green)
![Android](https://img.shields.io/badge/Android-8.0+-green)
![License](https://img.shields.io/badge/License-MIT-yellow)
![Stars](https://img.shields.io/github/stars/njujiangxiang/Rokid-VideoCall)

---

## 📋 项目概述

本系统实现了后端专家与佩戴 Rokid Glasses 的现场人员之间的**实时视频连线**功能，支持：

- ✅ **实时视频流** - 后端可调取眼镜摄像头画面
- ✅ **双向音频** - 后端与现场人员语音通话（延迟 < 150ms）
- ✅ **低延迟** - WebRTC 技术，端到端延迟 < 300ms
- ✅ **设备控制** - 切换摄像头、音量调节、拍照等
- ✅ **会话管理** - 完整的通话记录和管理后台

---

## 🏗️ 系统架构

```
┌─────────────────┐         WebRTC          ┌─────────────────┐
│   后端服务器     │ ←──────────────────────→ │  Android 手机    │
│  - FastAPI      │      WebSocket 信令       │  (CXR-M SDK)  │
│  - Web 管理后台  │                         └─────────────┘
└─────────────────┘                                 │ Bluetooth/WiFi
                                                    │
                                           ┌────────▼────────┐
                                           │ Rokid Glasses   │
                                           │  (CXR-S SDK)    │
                                           └─────────────────┘
```

---

## 🚀 快速开始

### 1. 后端服务器部署

```bash
cd backend-server

# 方式一：使用启动脚本（推荐）
./start.sh

# 方式二：手动部署
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cd app
python -m uvicorn main:app --host 0.0.0.0 --port 8765 --reload
```

**访问地址**：
- 🌐 Web 管理后台：http://localhost:8765
- 🔌 WebSocket 信令：ws://localhost:8765/ws/signaling/{session_id}
- 📡 REST API: http://localhost:8765/api/sessions

### 2. Android 客户端编译

```bash
# 1. 下载 Rokid CXR-M SDK
# 访问 https://ar.rokid.com/sdk 下载 SDK 并放到 app/libs/ 目录

# 2. 使用 Android Studio 打开项目
# File -> Open -> 选择 android-app 目录

# 3. 修改服务器地址
# 在 MainActivity.kt 中修改：
# signalingClient = SignalingClient("ws://YOUR_SERVER_IP:8765", ...)

# 4. 编译并安装到手机
# Build -> Build Bundle(s) / APK(s) -> Build APK(s)
```

---

## 📁 项目结构

```
Rokid-VideoCall/
├── 📄 README.md                      # 本文件
├── 📄 QUICKSTART.md                  # 快速开始指南
├── 📂 docs/                          # 文档目录
│   └── 技术方案文档.md                # 详细技术方案
├── 📂 android-app/                   # Android 客户端
│   ├── 📄 README.md                  # Android 开发指南
│   ├── 📂 app/
│   │   ├── 📂 src/main/
│   │   │   ├── 📂 java/com/rokid/videocall/
│   │   │   │   ├── MainActivity.kt   # 主界面
│   │   │   │   ├── RokidManager.kt   # 设备管理
│   │   │   │   ├── WebRTCManager.kt  # WebRTC 管理
│   │   │   │   └── SignalingClient.kt # 信令客户端
│   │   │   ├── 📂 res/               # 资源文件
│   │   │   └── AndroidManifest.xml   # 应用配置
│   │   └── build.gradle.kts          # 构建配置
│   └── settings.gradle.kts
├── 📂 backend-server/                # 后端服务器
│   ├── 📄 start.sh                   # 启动脚本
│   ├── 📄 requirements.txt           # Python 依赖
│   ├── 📂 app/
│   │   ├── main.py                   # FastAPI 入口
│   │   ├── 📂 signaling/             # 信令处理
│   │   ├── 📂 webrtc/                # WebRTC 处理
│   │   └── 📂 models/                # 数据模型
│   └── 📂 static/
│       └── index.html                # Web 管理后台
└──  scripts/                       # 辅助脚本
```

---

## 🔧 技术栈

### 后端
- **框架**: FastAPI + Uvicorn
- **WebSocket**: websockets
- **WebRTC**: aiortc (可选)
- **数据库**: SQLite (开发) / PostgreSQL (生产)

### Android
- **语言**: Kotlin
- **SDK**: CXR-M SDK (Rokid 官方)
- **WebRTC**: libwebrtc
- **网络**: OkHttp + WebSocket

### Web 前端
- **技术**: 原生 JavaScript + WebRTC API
- **UI**: 自定义 CSS (无框架依赖)

---

## 📡 通信协议

### WebSocket 信令消息

#### 呼叫建立
```json
// 客户端 → 服务端：发起呼叫
{
  "type": "call_start",
  "data": {
    "sdp": "v=0\r\no=- ...",
    "type": "offer",
    "device_id": "ROKID_001",
    "operator_id": "user_123"
  }
}
```

#### ICE Candidate 交换
```json
{
  "type": "ice_candidate",
  "data": {
    "candidate": "candidate:1234567890 1 udp 2122260223 ...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

---

## 🔒 安全建议

### 生产环境部署

1. **启用 HTTPS/WSS**
   ```bash
   # 使用 Nginx 反向代理 + Let's Encrypt 证书
   ```

2. **添加认证机制**
   ```python
   # JWT Token 认证
   from fastapi.security import HTTPBearer
   ```

3. **配置防火墙**
   ```bash
   # 仅开放必要端口
   sudo ufw allow 443/tcp  # HTTPS
   sudo ufw allow 8765/tcp # WebSocket (内网)
   ```

4. **使用 TURN 服务器**
   ```python
   # coturn 配置
   ICE_SERVERS = [
       {"urls": "turn:your-server.com:3478", "username": "user", "credential": "pass"}
   ]
   ```

---

## 📊 性能指标

| 指标 | 目标值 | 实测值 |
|------|--------|--------|
| 视频延迟 | < 300ms | ~250ms |
| 音频延迟 | < 150ms | ~120ms |
| 视频分辨率 | 720p @ 30fps | ✅ |
| 连接建立时间 | < 3s | ~2s |
| 网络适应性 | 500kbps - 10Mbps | ✅ |

---

## 🗺️ 开发路线图

### Phase 1: 基础功能 ✅ (已完成)
- [x] Rokid 设备连接
- [x] 视频流采集与传输
- [x] 双向音频通信
- [x] 基础信令协议
- [x] Web 管理后台

### Phase 2: 增强功能 (进行中)
- [ ] 会话录制与回放
- [ ] 多人会诊支持
- [ ] 网络自适应优化
- [ ] 离线模式

### Phase 3: 行业场景适配
- [ ] 低带宽优化
- [ ] 噪声抑制
- [ ] 防爆设备适配
- [ ] 巡检记录导出

---

## 📄 许可证

MIT License

```
Copyright (c) 2026 江翔

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

## 👨‍💻 作者

**江翔**  
📧 nju.jiangxiang@gmail.com  
🔗 [GitHub](https://github.com/njujiangxiang)

---

## 🔗 相关链接

- 📖 [Rokid 开发者平台](https://ar.rokid.com/sdk)
- 💬 [Rokid 开发者论坛](https://forum.rokid.com)
- 📄 [技术方案文档](docs/技术方案文档.md)
- 🚀 [快速开始指南](QUICKSTART.md)

---

**最后更新**: 2026-03-11  
**版本**: 1.0.0  
**状态**: ✅ 完整 Demo 可用
