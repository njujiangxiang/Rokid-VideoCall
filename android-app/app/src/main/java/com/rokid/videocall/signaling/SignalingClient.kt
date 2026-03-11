package com.rokid.videocall.signaling

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.rokid.videocall.Config
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket 信令客户端
 * 
 * 负责与信令服务器建立 WebSocket 连接，处理信令消息的发送和接收
 */
class SignalingClient {
    
    companion object {
        private const val TAG = "SignalingClient"
        
        @Volatile
        private var instance: SignalingClient? = null
        
        fun getInstance(): SignalingClient {
            return instance ?: synchronized(this) {
                instance ?: SignalingClient().also { instance = it }
            }
        }
    }
    
    private val gson: Gson = GsonBuilder().create()
    private var webSocket: WebSocket? = null
    private var listener: SignalingListener? = null
    private var clientId: String? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private var isReconnecting = false
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(Config.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)  // 不设置读取超时，保持长连接
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)  // 心跳
        .build()
    
    private val request = Request.Builder()
        .url(Config.SIGNALING_SERVER_URL)
        .build()
    
    /**
     * 设置信令监听器
     */
    fun setListener(listener: SignalingListener) {
        this.listener = listener
    }
    
    /**
     * 连接到信令服务器
     */
    fun connect() {
        if (isConnected || isReconnecting) {
            Log.w(TAG, "已在连接中或正在重连")
            return
        }
        
        Log.d(TAG, "开始连接信令服务器：${Config.SIGNALING_SERVER_URL}")
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 连接已建立")
                isConnected = true
                reconnectAttempts = 0
                listener?.onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到消息：$text")
                handleMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket 正在关闭：$code - $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 已关闭：$code - $reason")
                isConnected = false
                listener?.onDisconnected()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 错误：${t.message}", t)
                isConnected = false
                listener?.onError(t.message ?: "Unknown error")
                
                // 自动重连
                if (!isReconnecting) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        isReconnecting = false
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected = false
        clientId = null
        Log.d(TAG, "已断开连接")
    }
    
    /**
     * 发送消息
     */
    fun send(message: SignalingMessage) {
        if (!isConnected) {
            Log.e(TAG, "无法发送消息：未连接")
            listener?.onError("Not connected")
            return
        }
        
        val json = gson.toJson(message)
        Log.d(TAG, "发送消息：$json")
        webSocket?.send(json)
    }
    
    /**
     * 认证（用户名密码）
     */
    fun login(username: String, password: String) {
        val message = AuthRequest(
            data = AuthData(username = username, password = password)
        )
        send(SignalingMessage(
            type = message.type,
            data = message.data
        ))
    }
    
    /**
     * 认证（Token）
     */
    fun loginWithToken(token: String) {
        val message = AuthRequest(
            data = AuthData(token = token)
        )
        send(SignalingMessage(
            type = message.type,
            data = message.data
        ))
    }
    
    /**
     * 加入房间
     */
    fun joinRoom(roomId: String? = null, roomName: String? = null) {
        val message = RoomJoinRequest(
            data = RoomJoinData(roomId = roomId, roomName = roomName)
        )
        send(SignalingMessage(
            type = message.type,
            roomId = roomId,
            data = message.data
        ))
    }
    
    /**
     * 离开房间
     */
    fun leaveRoom(roomId: String) {
        send(SignalingMessage(
            type = MessageType.ROOM_LEAVE.value,
            roomId = roomId,
            data = mapOf("roomId" to roomId)
        ))
    }
    
    /**
     * 发起呼叫
     */
    fun initiateCall(targetId: String, roomId: String, type: String = "video") {
        val callId = java.util.UUID.randomUUID().toString()
        send(SignalingMessage(
            type = MessageType.CALL_INITIATE.value,
            roomId = roomId,
            to = targetId,
            data = mapOf(
                "callId" to callId,
                "callerId" to (clientId ?: ""),
                "type" to type
            )
        ))
    }
    
    /**
     * 接受呼叫
     */
    fun acceptCall(callId: String, roomId: String) {
        send(SignalingMessage(
            type = MessageType.CALL_ACCEPT.value,
            roomId = roomId,
            data = mapOf("callId" to callId)
        ))
    }
    
    /**
     * 结束呼叫
     */
    fun endCall(callId: String, roomId: String) {
        send(SignalingMessage(
            type = MessageType.CALL_END.value,
            roomId = roomId,
            data = mapOf("callId" to callId)
        ))
    }
    
    /**
     * 发送 SDP Offer
     */
    fun sendSDPOffer(to: String, sdp: String, roomId: String) {
        send(SignalingMessage(
            type = MessageType.SDP_OFFER.value,
            roomId = roomId,
            to = to,
            data = mapOf(
                "sdp" to sdp,
                "type" to "offer"
            )
        ))
    }
    
    /**
     * 发送 SDP Answer
     */
    fun sendSDPAnswer(to: String, sdp: String, roomId: String) {
        send(SignalingMessage(
            type = MessageType.SDP_ANSWER.value,
            roomId = roomId,
            to = to,
            data = mapOf(
                "sdp" to sdp,
                "type" to "answer"
            )
        ))
    }
    
    /**
     * 发送 ICE Candidate
     */
    fun sendICECandidate(to: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?, roomId: String) {
        send(SignalingMessage(
            type = MessageType.ICE_CANDIDATE.value,
            roomId = roomId,
            to = to,
            data = mapOf(
                "candidate" to candidate,
                "sdpMid" to sdpMid,
                "sdpMLineIndex" to sdpMLineIndex
            )
        ))
    }
    
    /**
     * 注册设备
     */
    fun registerDevice(sn: String, name: String? = null) {
        send(SignalingMessage(
            type = MessageType.DEVICE_REGISTER.value,
            data = mapOf(
                "sn" to sn,
                "name" to (name ?: "Device-${sn.takeLast(6)}")
            )
        ))
    }
    
    /**
     * 上报设备状态
     */
    fun reportDeviceStatus(battery: Int, signal: Int, temperature: Float? = null) {
        val data = mutableMapOf<String, Any>(
            "battery" to battery,
            "signal" to signal
        )
        temperature?.let { data["temperature"] = it }
        
        send(SignalingMessage(
            type = MessageType.DEVICE_STATUS.value,
            data = data
        ))
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(text: String) {
        try {
            val jsonElement = JsonParser.parseString(text)
            val jsonObject = jsonElement.asJsonObject
            val type = jsonObject.get("type")?.asString ?: return
            
            when (type) {
                MessageType.AUTH_RESPONSE.value -> {
                    val data = jsonObject.getAsJsonObject("data")
                    clientId = data.get("clientId")?.asString
                    listener?.onAuthResponse(
                        success = data.get("success")?.asBoolean ?: false,
                        userId = data.get("userId")?.asString,
                        role = data.get("role")?.asString,
                        token = data.get("token")?.asString,
                        error = data.get("error")?.asString,
                        clientId = clientId
                    )
                }
                
                MessageType.ROOM_JOINED.value -> {
                    val roomId = jsonObject.get("roomId")?.asString ?: return
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onRoomJoined(
                        roomId = roomId,
                        roomName = data.get("roomName")?.asString ?: "",
                        participants = data.getAsJsonArray("participants")
                            ?.map { it.asString } ?: emptyList()
                    )
                }
                
                MessageType.ROOM_LEFT.value -> {
                    val roomId = jsonObject.get("roomId")?.asString ?: return
                    listener?.onRoomLeft(roomId)
                }
                
                MessageType.CALL_INITIATE.value -> {
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onCallInitiated(
                        callId = data.get("callId")?.asString ?: "",
                        callerId = data.get("callerId")?.asString ?: "",
                        type = data.get("type")?.asString ?: "video"
                    )
                }
                
                MessageType.CALL_ACCEPT.value -> {
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onCallAccepted(data.get("callId")?.asString ?: "")
                }
                
                MessageType.CALL_END.value -> {
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onCallEnded(data.get("callId")?.asString ?: "")
                }
                
                MessageType.SDP_OFFER.value -> {
                    val from = jsonObject.get("from")?.asString ?: return
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onSDPOfferReceived(
                        from = from,
                        sdp = data.get("sdp")?.asString ?: ""
                    )
                }
                
                MessageType.SDP_ANSWER.value -> {
                    val from = jsonObject.get("from")?.asString ?: return
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onSDPAnswerReceived(
                        from = from,
                        sdp = data.get("sdp")?.asString ?: ""
                    )
                }
                
                MessageType.ICE_CANDIDATE.value -> {
                    val from = jsonObject.get("from")?.asString ?: return
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onICECandidateReceived(
                        from = from,
                        candidate = data.get("candidate")?.asString ?: "",
                        sdpMid = data.get("sdpMid")?.asString,
                        sdpMLineIndex = data.get("sdpMLineIndex")?.asInt
                    )
                }
                
                MessageType.DEVICE_STATUS.value -> {
                    val data = jsonObject.getAsJsonObject("data")
                    listener?.onDeviceStatusUpdate(
                        deviceId = data.get("deviceId")?.asString ?: "",
                        sn = data.get("sn")?.asString ?: "",
                        name = data.get("name")?.asString ?: "",
                        online = data.get("online")?.asBoolean ?: false,
                        battery = data.get("battery")?.asInt ?: 0,
                        signal = data.get("signal")?.asInt ?: 0
                    )
                }
                
                MessageType.ERROR.value -> {
                    val data = jsonObject.getAsJsonObject("data")
                    val errorMessage = data.get("message")?.asString ?: "Unknown error"
                    Log.e(TAG, "服务器错误：$errorMessage")
                    listener?.onError(errorMessage)
                }
                
                else -> {
                    Log.w(TAG, "未知消息类型：$type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "消息解析错误", e)
            listener?.onError("Message parse error: ${e.message}")
        }
    }
    
    /**
     * 调度重连
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= Config.MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "达到最大重连次数，停止重连")
            listener?.onError("Max reconnect attempts reached")
            return
        }
        
        isReconnecting = true
        reconnectAttempts++
        
        val delay = Config.RECONNECT_INTERVAL * reconnectAttempts
        Log.d(TAG, "计划重连：${delay}ms 后 (尝试 $reconnectAttempts/${Config.MAX_RECONNECT_ATTEMPTS})")
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isConnected) {
                Log.d(TAG, "开始重连...")
                connect()
            }
        }, delay)
    }
    
    /**
     * 获取客户端 ID
     */
    fun getClientId(): String? = clientId
    
    /**
     * 检查是否已连接
     */
    fun is_connected(): Boolean = isConnected
}
