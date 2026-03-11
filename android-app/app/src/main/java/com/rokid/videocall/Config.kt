package com.rokid.videocall

/**
 * 应用配置
 * 
 * 请根据实际部署环境修改以下配置
 */
object Config {
    
    // ==================== 服务器配置 ====================
    
    /**
     * 信令服务器 WebSocket 地址
     * 开发环境：使用本地 IP
     * 生产环境：使用域名或公网 IP
     */
    const val SIGNALING_SERVER_URL = "ws://192.168.1.100:3000/ws"
    
    /**
     * REST API 基础地址
     */
    const val REST_API_BASE_URL = "http://192.168.1.100:3000/api/v1"
    
    // ==================== WebRTC 配置 ====================
    
    /**
     * STUN 服务器（用于 NAT 穿透）
     * Google 公共 STUN 服务器
     */
    const val STUN_SERVER = "stun:stun.l.google.com:19302"
    
    /**
     * TURN 服务器（如果 STUN 无法穿透）
     * 格式：turn:username:password@host:port
     * 示例：turn:user:pass@turn.example.com:3478
     */
    const val TURN_SERVER = ""
    
    /**
     * 视频编码格式
     * 可选：H264, VP8, VP9
     */
    const val VIDEO_CODEC = "H264"
    
    /**
     * 视频分辨率
     * 可选：720p (1280x720), 1080p (1920x1080)
     */
    const val VIDEO_RESOLUTION = "720p"
    
    /**
     * 视频帧率
     */
    const val VIDEO_FPS = 30
    
    /**
     * 视频码率 (kbps)
     */
    const val VIDEO_BITRATE = 2000
    
    /**
     * 音频码率 (kbps)
     */
    const val AUDIO_BITRATE = 64
    
    // ==================== 设备配置 ====================
    
    /**
     * 心跳间隔（毫秒）
     */
    const val HEARTBEAT_INTERVAL = 30000L
    
    /**
     * 连接超时（毫秒）
     */
    const val CONNECTION_TIMEOUT = 10000L
    
    /**
     * 重连间隔（毫秒）
     */
    const val RECONNECT_INTERVAL = 5000L
    
    /**
     * 最大重连次数
     */
    const val MAX_RECONNECT_ATTEMPTS = 5
    
    // ==================== 日志配置 ====================
    
    /**
     * 是否启用调试日志
     */
    const val DEBUG_MODE = true
    
    /**
     * 日志标签
     */
    const val LOG_TAG = "RokidVideoCall"
}
