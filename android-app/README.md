# Rokid VideoCall - Android 端

## 📱 项目结构

```
app/
├── src/main/
│   ├── java/com/rokid/videocall/
│   │   ├── MainActivity.kt          # 主界面
│   │   ├── RokidManager.kt          # Rokid 设备管理
│   │   ├── WebRTCManager.kt         # WebRTC 管理
│   │   └── SignalingClient.kt       # WebSocket 信令
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml    # 主界面布局
│   │   └── values/
│   │       └── strings.xml          # 字符串资源
│   └── AndroidManifest.xml          # 应用配置
├── build.gradle                     # 构建配置
└── proguard-rules.pro              # 混淆规则
```

## 🔧 开发环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 33 (Android 13)
- Kotlin 1.9+

## 📦 依赖库

### 核心依赖
- **CXR-M SDK** (Rokid 官方) - 眼镜通信
- **org.webrtc:libwebrtc** - WebRTC 实现
- **com.squareup.okhttp3:okhttp** - WebSocket 客户端
- **com.google.code.gson:gson** - JSON 序列化

### 如何获取 CXR-M SDK

1. 访问 Rokid 开发者平台：https://ar.rokid.com/sdk
2. 登录/注册开发者账号
3. 下载 CXR-M SDK (Android)
4. 将 SDK 的 `.aar` 文件放到 `app/libs/` 目录
5. 在 `build.gradle` 中添加依赖

## 🚀 构建步骤

### 1. 克隆项目
```bash
cd android-app
```

### 2. 添加 Rokid SDK
```bash
# 将下载好的 SDK 放到 libs 目录
cp ~/Downloads/cxm-sdk-1.0.9.aar app/libs/
```

### 3. 配置 build.gradle
确保 `app/build.gradle` 包含：
```gradle
dependencies {
    // Rokid CXR-M SDK
    implementation files('libs/cxm-sdk-1.0.9.aar')
    
    // WebRTC
    implementation 'org.webrtc:libwebrtc:125.6422.06'
    
    // OkHttp
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // Gson
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // AndroidX
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
```

### 4. 打开项目
```bash
# 使用 Android Studio 打开
open -a "Android Studio" .
```

### 5. 同步 Gradle
在 Android Studio 中点击 "Sync Project with Gradle Files"

### 6. 修改服务器地址
在 `MainActivity.kt` 中修改：
```kotlin
signalingClient = SignalingClient("ws://YOUR_SERVER_IP:8765", ...)
```

### 7. 运行应用
- 连接 Android 手机或启动模拟器
- 点击 Run 按钮
- 授予所有权限（摄像头、麦克风、蓝牙等）

## 📱 使用说明

### 首次使用
1. 打开应用
2. 授予所有权限请求
3. 确保 Rokid Glasses 已开机并在附近
4. 点击 "连接眼镜"

### 发起通话
1. 确保眼镜已连接（状态显示 "已连接 Rokid Glasses"）
2. 点击 "发起呼叫"
3. 等待后端接受
4. 通话建立后可见视频画面

### 通话中操作
- **切换摄像头**: 点击切换按钮
- **结束通话**: 点击 "结束通话" 按钮

## 🐛 常见问题

### Q: 无法连接到眼镜
A: 
- 确保眼镜已充电并开机
- 检查手机蓝牙已开启
- 在 Rokid 开发者论坛查询设备兼容性

### Q: 视频黑屏
A:
- 检查摄像头权限已授予
- 确认 WebRTC 初始化成功
- 查看 Logcat 日志排查错误

### Q: 通话延迟高
A:
- 检查网络连接质量
- 降低视频分辨率（修改 `startCapture` 参数）
- 使用 WiFi 而非移动数据

## 📞 技术支持

- Rokid 开发者论坛：https://forum.rokid.com
- 项目 Issues: [GitHub Issues]
- 联系方式：developer@rokid.com

---

**版本**: 1.0.0  
**创建时间**: 2026-03-11  
**作者**: 大龙虾 🦞
