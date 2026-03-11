package com.rokid.videocall

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

/**
 * WebSocket 信令客户端
 * 
 * 功能:
 * - 与后端建立 WebSocket 连接
 * - 发送/接收信令消息
 * - 处理呼叫流程
 * 
 * 使用 OkHttp WebSocket 实现
 */
class SignalingClient(
    private val serverUrl: String,
    private val callback: SignalingCallback
) {

    interface SignalingCallback {
        fun onConnected()
        fun onMessage(message: SignalingMessage)
        fun onError(error: String)
    }

    companion object {
        private const val TAG = "SignalingClient"
    }

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // 不超时
        .pingInterval(30, TimeUnit.SECONDS) // 心跳
        .build()

    private var isConnected = false
    private var sessionId: String? = null

    /**
     * 连接到信令服务器
     */
    fun connect() {
        if (isConnected) {
            Log.w(TAG, "已连接")
            return
        }

        Log.d(TAG, "连接到信令服务器：$serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 已连接")
                isConnected = true
                callback.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到消息：$text")
                try {
                    val message = gson.fromJson(text, SignalingMessage::class.java)
                    callback.onMessage(message)
                } catch (e: Exception) {
                    Log.e(TAG, "解析消息失败：${e.message}")
                    callback.onError("解析消息失败：${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 关闭：$code - $reason")
                isConnected = false
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 错误：${t.message}")
                isConnected = false
                callback.onError("连接失败：${t.message}")
            }
        })
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        if (!isConnected) {
            return
        }

        Log.d(TAG, "断开 WebSocket 连接")
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
        sessionId = null
    }

    /**
     * 发送消息
     */
    fun send(message: SignalingMessage) {
        if (!isConnected) {
            callback.onError("未连接")
            return
        }

        val json = gson.toJson(message)
        Log.d(TAG, "发送消息：$json")
        webSocket?.send(json)
    }

    /**
     * 发送呼叫请求
     */
    fun sendCallRequest(offerSdp: SessionDescription) {
        val message = SignalingMessage(
            type = "call_start",
            data = mapOf(
                "sdp" to offerSdp.description,
                "type" to offerSdp.type.canonicalForm(),
                "device_id" to getDeviceId(),
                "operator_id" to getUserId(),
                "timestamp" to System.currentTimeMillis()
            )
        )
        send(message)
    }

    /**
     * 发送 Answer
     */
    fun sendAnswer(answerSdp: SessionDescription) {
        val message = SignalingMessage(
            type = "call_answer",
            data = mapOf(
                "sdp" to answerSdp.description,
                "type" to answerSdp.type.canonicalForm(),
                "session_id" to (sessionId ?: "")
            )
        )
        send(message)
    }

    /**
     * 发送 ICE Candidate
     */
    fun sendIceCandidate(candidate: IceCandidate) {
        val message = SignalingMessage(
            type = "ice_candidate",
            data = mapOf(
                "candidate" to candidate.sdp,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "session_id" to (sessionId ?: "")
            )
        )
        send(message)
    }

    /**
     * 发送结束呼叫
     */
    fun sendEndCall() {
        val message = SignalingMessage(
            type = "call_end",
            data = mapOf(
                "session_id" to (sessionId ?: ""),
                "reason" to "user_hangup"
            )
        )
        send(message)
    }

    /**
     * 发送摄像头切换通知
     */
    fun sendCameraSwitched(camera: Int) {
        val message = SignalingMessage(
            type = "camera_switched",
            data = mapOf(
                "camera" to (if (camera == 0) "front" else "back"),
                "session_id" to (sessionId ?: "")
            )
        )
        send(message)
    }

    /**
     * 发送电量状态
     */
    fun sendBatteryStatus(level: Int, charging: Boolean) {
        val message = SignalingMessage(
            type = "battery_status",
            data = mapOf(
                "level" to level,
                "charging" to charging,
                "session_id" to (sessionId ?: "")
            )
        )
        send(message)
    }

    /**
     * 获取设备 ID（实际使用时从 Rokid SDK 获取）
     */
    private fun getDeviceId(): String {
        // TODO: 从 Rokid SDK 获取真实设备 ID
        return "ROKID_${android.os.Build.SERIAL}"
    }

    /**
     * 获取用户 ID（实际使用时从登录系统获取）
     */
    private fun getUserId(): String {
        // TODO: 从登录系统获取用户 ID
        return "user_${android.os.Build.ID}"
    }
}

/**
 * 信令消息数据类
 */
data class SignalingMessage(
    val type: String,
    val data: Map<String, Any>
)
