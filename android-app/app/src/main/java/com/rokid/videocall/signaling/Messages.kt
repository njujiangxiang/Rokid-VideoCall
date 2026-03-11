package com.rokid.videocall.signaling

import com.google.gson.annotations.SerializedName

/**
 * 信令消息类型定义
 */
enum class MessageType(val value: String) {
    // 认证
    AUTH("auth"),
    AUTH_RESPONSE("auth.response"),
    
    // 房间管理
    ROOM_JOIN("room.join"),
    ROOM_LEAVE("room.leave"),
    ROOM_CREATED("room.created"),
    ROOM_JOINED("room.joined"),
    ROOM_LEFT("room.left"),
    
    // 呼叫控制
    CALL_INITIATE("call.initiate"),
    CALL_ACCEPT("call.accept"),
    CALL_REJECT("call.reject"),
    CALL_END("call.end"),
    
    // WebRTC 信令
    SDP_OFFER("webrtc.offer"),
    SDP_ANSWER("webrtc.answer"),
    ICE_CANDIDATE("webrtc.ice"),
    
    // 设备管理
    DEVICE_REGISTER("device.register"),
    DEVICE_STATUS("device.status"),
    DEVICE_CONTROL("device.control"),
    
    // 错误
    ERROR("error");
}

/**
 * 信令消息基类
 */
data class SignalingMessage(
    @SerializedName("type") val type: String,
    @SerializedName("roomId") val roomId: String? = null,
    @SerializedName("from") val from: String? = null,
    @SerializedName("to") val to: String? = null,
    @SerializedName("data") val data: Any? = null,
    @SerializedName("timestamp") val timestamp: Long? = null
)

/**
 * 认证请求
 */
data class AuthRequest(
    @SerializedName("type") val type: String = MessageType.AUTH.value,
    @SerializedName("data") val data: AuthData
)

data class AuthData(
    @SerializedName("token") val token: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null
)

/**
 * 认证响应
 */
data class AuthResponse(
    @SerializedName("type") val type: String = MessageType.AUTH_RESPONSE.value,
    @SerializedName("data") val data: AuthResponseData
)

data class AuthResponseData(
    @SerializedName("success") val success: Boolean,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("clientId") val clientId: String? = null
)

/**
 * 房间加入请求
 */
data class RoomJoinRequest(
    @SerializedName("type") val type: String = MessageType.ROOM_JOIN.value,
    @SerializedName("data") val data: RoomJoinData
)

data class RoomJoinData(
    @SerializedName("roomId") val roomId: String? = null,
    @SerializedName("roomName") val roomName: String? = null
)

/**
 * 房间加入响应
 */
data class RoomJoinedMessage(
    @SerializedName("type") val type: String = MessageType.ROOM_JOINED.value,
    @SerializedName("roomId") val roomId: String,
    @SerializedName("data") val data: RoomJoinedData
)

data class RoomJoinedData(
    @SerializedName("roomId") val roomId: String,
    @SerializedName("roomName") val roomName: String,
    @SerializedName("participants") val participants: List<String>
)

/**
 * 呼叫发起消息
 */
data class CallInitiateMessage(
    @SerializedName("type") val type: String = MessageType.CALL_INITIATE.value,
    @SerializedName("to") val to: String,
    @SerializedName("data") val data: CallInitiateData
)

data class CallInitiateData(
    @SerializedName("callId") val callId: String,
    @SerializedName("callerId") val callerId: String,
    @SerializedName("type") val type: String = "video"  // video | audio
)

/**
 * 呼叫接受消息
 */
data class CallAcceptMessage(
    @SerializedName("type") val type: String = MessageType.CALL_ACCEPT.value,
    @SerializedName("data") val data: CallAcceptData
)

data class CallAcceptData(
    @SerializedName("callId") val callId: String
)

/**
 * 呼叫结束消息
 */
data class CallEndMessage(
    @SerializedName("type") val type: String = MessageType.CALL_END.value,
    @SerializedName("data") val data: CallEndData
)

data class CallEndData(
    @SerializedName("callId") val callId: String
)

/**
 * SDP Offer 消息
 */
data class SDPOfferMessage(
    @SerializedName("type") val type: String = MessageType.SDP_OFFER.value,
    @SerializedName("to") val to: String,
    @SerializedName("data") val data: SDPData
)

/**
 * SDP Answer 消息
 */
data class SDPAnswerMessage(
    @SerializedName("type") val type: String = MessageType.SDP_ANSWER.value,
    @SerializedName("to") val to: String,
    @SerializedName("data") val data: SDPData
)

data class SDPData(
    @SerializedName("sdp") val sdp: String,
    @SerializedName("type") val sdpType: String  // offer | answer
)

/**
 * ICE Candidate 消息
 */
data class ICECandidateMessage(
    @SerializedName("type") val type: String = MessageType.ICE_CANDIDATE.value,
    @SerializedName("to") val to: String,
    @SerializedName("data") val data: ICECandidateData
)

data class ICECandidateData(
    @SerializedName("candidate") val candidate: String,
    @SerializedName("sdpMid") val sdpMid: String?,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: Int?
)

/**
 * 设备注册消息
 */
data class DeviceRegisterRequest(
    @SerializedName("type") val type: String = MessageType.DEVICE_REGISTER.value,
    @SerializedName("data") val data: DeviceRegisterData
)

data class DeviceRegisterData(
    @SerializedName("sn") val sn: String,
    @SerializedName("name") val name: String? = null
)

/**
 * 设备状态消息
 */
data class DeviceStatusMessage(
    @SerializedName("type") val type: String = MessageType.DEVICE_STATUS.value,
    @SerializedName("data") val data: DeviceStatusData
)

data class DeviceStatusData(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("sn") val sn: String,
    @SerializedName("name") val name: String,
    @SerializedName("online") val online: Boolean,
    @SerializedName("battery") val battery: Int,
    @SerializedName("signal") val signal: Int,
    @SerializedName("lastSeen") val lastSeen: Long
)

/**
 * 错误消息
 */
data class ErrorMessage(
    @SerializedName("type") val type: String = MessageType.ERROR.value,
    @SerializedName("data") val data: ErrorData
)

data class ErrorData(
    @SerializedName("message") val message: String,
    @SerializedName("code") val code: String? = null
)
