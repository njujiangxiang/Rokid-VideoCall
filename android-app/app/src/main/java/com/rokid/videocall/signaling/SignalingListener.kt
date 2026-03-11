package com.rokid.videocall.signaling

/**
 * 信令事件监听器接口
 * 
 * 实现此接口以接收信令客户端的各种事件回调
 */
interface SignalingListener {
    
    /**
     * WebSocket 连接成功
     */
    fun onConnected() {}
    
    /**
     * WebSocket 断开连接
     */
    fun onDisconnected() {}
    
    /**
     * 认证响应
     * 
     * @param success 是否成功
     * @param userId 用户 ID
     * @param role 用户角色
     * @param token JWT Token
     * @param error 错误信息
     * @param clientId 客户端 ID
     */
    fun onAuthResponse(
        success: Boolean,
        userId: String?,
        role: String?,
        token: String?,
        error: String?,
        clientId: String?
    ) {
        if (!success) {
            onError(error ?: "Authentication failed")
        }
    }
    
    /**
     * 加入房间成功
     * 
     * @param roomId 房间 ID
     * @param roomName 房间名称
     * @param participants 参与者列表
     */
    fun onRoomJoined(roomId: String, roomName: String, participants: List<String>) {}
    
    /**
     * 离开房间
     * 
     * @param roomId 房间 ID
     */
    fun onRoomLeft(roomId: String) {}
    
    /**
     * 收到呼叫发起
     * 
     * @param callId 呼叫 ID
     * @param callerId 呼叫方 ID
     * @param type 呼叫类型 (video/audio)
     */
    fun onCallInitiated(callId: String, callerId: String, type: String) {}
    
    /**
     * 呼叫被接受
     * 
     * @param callId 呼叫 ID
     */
    fun onCallAccepted(callId: String) {}
    
    /**
     * 呼叫被拒绝
     * 
     * @param callId 呼叫 ID
     * @param reason 拒绝原因
     */
    fun onCallRejected(callId: String, reason: String?) {}
    
    /**
     * 呼叫结束
     * 
     * @param callId 呼叫 ID
     */
    fun onCallEnded(callId: String) {}
    
    /**
     * 收到 SDP Offer
     * 
     * @param from 发送方 ID
     * @param sdp SDP 内容
     */
    fun onSDPOfferReceived(from: String, sdp: String) {}
    
    /**
     * 收到 SDP Answer
     * 
     * @param from 发送方 ID
     * @param sdp SDP 内容
     */
    fun onSDPAnswerReceived(from: String, sdp: String) {}
    
    /**
     * 收到 ICE Candidate
     * 
     * @param from 发送方 ID
     * @param candidate ICE Candidate
     * @param sdpMid SDP Media ID
     * @param sdpMLineIndex SDP Media Line Index
     */
    fun onICECandidateReceived(from: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {}
    
    /**
     * 设备状态更新
     * 
     * @param deviceId 设备 ID
     * @param sn 序列号
     * @param name 设备名称
     * @param online 在线状态
     * @param battery 电量 (0-100)
     * @param signal 信号强度
     */
    fun onDeviceStatusUpdate(
        deviceId: String,
        sn: String,
        name: String,
        online: Boolean,
        battery: Int,
        signal: Int
    ) {}
    
    /**
     * 发生错误
     * 
     * @param message 错误信息
     */
    fun onError(message: String) {}
}
