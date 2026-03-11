package com.rokid.videocall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rokid.videocall.signaling.SignalingClient
import com.rokid.videocall.signaling.SignalingListener
import com.rokid.videocall.webrtc.WebRTCManager
import com.rokid.videocall.webrtc.WebRTCListener
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

/**
 * 主界面
 * 
 * 功能：
 * - 用户登录
 * - 设备连接
 * - 发起/接听呼叫
 * - 视频通话界面
 */
class MainActivity : AppCompatActivity(), SignalingListener, WebRTCListener {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
        
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    }
    
    // UI 组件
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnConnectDevice: Button
    private lateinit var btnCall: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceStatus: TextView
    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var layoutCall: LinearLayout
    private lateinit var layoutLogin: LinearLayout
    
    // 管理器
    private val signalingClient = SignalingClient.getInstance()
    private lateinit var webRTCManager: WebRTCManager
    
    // 状态
    private var isLoggedIn = false
    private var isConnectedToDevice = false
    private var isInCall = false
    private var currentRoomId: String? = null
    private var currentCallId: String? = null
    private var remotePeerId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        checkPermissions()
        
        // 初始化信令客户端
        signalingClient.setListener(this)
        
        // 初始化 WebRTC 管理器
        webRTCManager = WebRTCManager.getInstance(this)
        webRTCManager.setListener(this)
    }
    
    private fun initViews() {
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnConnectDevice = findViewById(R.id.btn_connect_device)
        btnCall = findViewById(R.id.btn_call)
        tvStatus = findViewById(R.id.tv_status)
        tvDeviceStatus = findViewById(R.id.tv_device_status)
        localVideoView = findViewById(R.id.local_video_view)
        remoteVideoView = findViewById(R.id.remote_video_view)
        layoutCall = findViewById(R.id.layout_call)
        layoutLogin = findViewById(R.id.layout_login)
        
        // 登录按钮
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            
            if (username.isNotEmpty() && password.isNotEmpty()) {
                login(username, password)
            } else {
                showToast("请输入用户名和密码")
            }
        }
        
        // 连接设备按钮
        btnConnectDevice.setOnClickListener {
            connectToDevice()
        }
        
        // 呼叫按钮
        btnCall.setOnClickListener {
            if (isInCall) {
                endCall()
            } else {
                startCall()
            }
        }
        
        // 初始状态
        updateUI()
    }
    
    /**
     * 检查权限
     */
    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.d(TAG, "所有权限已授予")
            } else {
                showToast("需要所有权限才能正常使用")
            }
        }
    }
    
    /**
     * 登录
     */
    private fun login(username: String, password: String) {
        showStatus("正在登录...")
        signalingClient.login(username, password)
    }
    
    /**
     * 连接 Rokid Glasses 设备
     */
    private fun connectToDevice() {
        showStatus("正在连接设备...")
        
        // TODO: 使用 Rokid CXR-M SDK 连接眼镜
        // 这里只是模拟
        android.os.Handler(mainLooper).postDelayed({
            isConnectedToDevice = true
            tvDeviceStatus.text = "设备状态：已连接 (电量：85%)"
            showStatus("设备已连接")
            updateUI()
        }, 2000)
    }
    
    /**
     * 发起呼叫
     */
    private fun startCall() {
        if (currentRoomId == null) {
            // 创建/加入房间
            val roomId = java.util.UUID.randomUUID().toString()
            signalingClient.joinRoom(roomId, "VideoCall-${roomId.takeLast(6)}")
        } else {
            // 发起呼叫
            remotePeerId?.let { peerId ->
                signalingClient.initiateCall(peerId, currentRoomId!!, "video")
            } ?: showToast("未选择呼叫对象")
        }
    }
    
    /**
     * 结束呼叫
     */
    private fun endCall() {
        currentCallId?.let { callId ->
            currentRoomId?.let { roomId ->
                signalingClient.endCall(callId, roomId)
            }
        }
        
        // 关闭 WebRTC
        webRTCManager.close()
        
        isInCall = false
        currentCallId = null
        updateUI()
        showStatus("通话已结束")
    }
    
    /**
     * 更新 UI 状态
     */
    private fun updateUI() {
        runOnUiThread {
            if (isLoggedIn) {
                layoutLogin.visibility = View.GONE
                layoutCall.visibility = View.VISIBLE
                
                btnConnectDevice.isEnabled = !isConnectedToDevice
                btnConnectDevice.text = if (isConnectedToDevice) "已连接设备" else "连接设备"
                
                btnCall.text = if (isInCall) "结束通话" else "发起呼叫"
                btnCall.isEnabled = isConnectedToDevice
            } else {
                layoutLogin.visibility = View.VISIBLE
                layoutCall.visibility = View.GONE
            }
        }
    }
    
    /**
     * 显示状态
     */
    private fun showStatus(message: String) {
        runOnUiThread {
            tvStatus.text = "状态：$message"
            Log.d(TAG, message)
        }
    }
    
    /**
     * 显示 Toast
     */
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== SignalingListener 实现 ====================
    
    override fun onConnected() {
        showStatus("信令服务器已连接")
    }
    
    override fun onDisconnected() {
        showStatus("信令服务器已断开")
        runOnUiThread {
            Toast.makeText(this, "服务器连接断开", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onAuthResponse(
        success: Boolean,
        userId: String?,
        role: String?,
        token: String?,
        error: String?,
        clientId: String?
    ) {
        runOnUiThread {
            if (success) {
                isLoggedIn = true
                showStatus("登录成功")
                showToast("欢迎，$userId")
                
                // 保存 token
                getSharedPreferences("rokid_videocall", MODE_PRIVATE)
                    .edit()
                    .putString("token", token)
                    .apply()
                
                // 连接信令服务器
                signalingClient.connect()
                
                updateUI()
            } else {
                showStatus("登录失败：$error")
                showToast("登录失败：$error")
            }
        }
    }
    
    override fun onRoomJoined(roomId: String, roomName: String, participants: List<String>) {
        currentRoomId = roomId
        showStatus("已加入房间：$roomName")
        
        // 找到其他参与者作为呼叫目标
        val clientId = signalingClient.getClientId()
        remotePeerId = participants.find { it != clientId }
        
        if (participants.size > 1) {
            // 房间已有其他人，直接发起呼叫
            startCall()
        }
    }
    
    override fun onRoomLeft(roomId: String) {
        if (currentRoomId == roomId) {
            currentRoomId = null
            showStatus("已离开房间")
        }
    }
    
    override fun onCallInitiated(callId: String, callerId: String, type: String) {
        runOnUiThread {
            currentCallId = callId
            remotePeerId = callerId
            
            val message = if (type == "video") "收到视频呼叫" else "收到语音呼叫"
            showStatus(message)
            
            // 显示接听对话框
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            // 自动接听（生产环境应该显示接听/拒绝按钮）
            android.os.Handler(mainLooper).postDelayed({
                signalingClient.acceptCall(callId, currentRoomId!!)
                setupWebRTC()
            }, 1000)
        }
    }
    
    override fun onCallAccepted(callId: String) {
        showStatus("呼叫已接受")
        setupWebRTC()
    }
    
    override fun onCallEnded(callId: String) {
        if (currentCallId == callId) {
            endCall()
        }
    }
    
    override fun onSDPOfferReceived(from: String, sdp: String) {
        Log.d(TAG, "收到 SDP Offer")
        webRTCManager.setRemoteDescription(sdp, "offer")
        webRTCManager.createAnswer()
    }
    
    override fun onSDPAnswerReceived(from: String, sdp: String) {
        Log.d(TAG, "收到 SDP Answer")
        webRTCManager.setRemoteDescription(sdp, "answer")
    }
    
    override fun onICECandidateReceived(from: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        Log.d(TAG, "收到 ICE Candidate")
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidate)
        webRTCManager.addICECandidate(iceCandidate)
    }
    
    override fun onError(message: String) {
        runOnUiThread {
            showStatus("错误：$message")
            Toast.makeText(this, "错误：$message", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== WebRTCListener 实现 ====================
    
    override fun onSignalingStateChanged(state: PeerConnection.SignalingState) {
        Log.d(TAG, "WebRTC 信令状态：$state")
    }
    
    override fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState) {
        Log.d(TAG, "WebRTC ICE 状态：$state")
    }
    
    override fun onConnected() {
        showStatus("WebRTC 已连接")
    }
    
    override fun onDisconnected() {
        showStatus("WebRTC 已断开")
    }
    
    override fun onICECandidate(candidate: IceCandidate) {
        // 发送 ICE Candidate 给对方
        remotePeerId?.let { peerId ->
            currentRoomId?.let { roomId ->
                signalingClient.sendICECandidate(
                    to = peerId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    roomId = roomId
                )
            }
        }
    }
    
    override fun onSDPReady(sdp: SessionDescription) {
        // 发送 SDP 给对方
        remotePeerId?.let { peerId ->
            currentRoomId?.let { roomId ->
                if (sdp.type == SessionDescription.Type.OFFER) {
                    signalingClient.sendSDPOffer(peerId, sdp.description, roomId)
                } else {
                    signalingClient.sendSDPAnswer(peerId, sdp.description, roomId)
                }
            }
        }
    }
    
    override fun onRemoteDescriptionSet() {
        Log.d(TAG, "远程描述已设置")
    }
    
    override fun onRemoteVideoTrackAdded(track: VideoTrack) {
        Log.d(TAG, "远程视频轨道已添加")
        runOnUiThread {
            track.addSink(remoteVideoView)
        }
    }
    
    override fun onRemoteAudioTrackAdded(track: AudioTrack) {
        Log.d(TAG, "远程音频轨道已添加")
    }
    
    override fun onStreamRemoved() {
        Log.d(TAG, "媒体流已移除")
        endCall()
    }
    
    /**
     * 设置 WebRTC
     */
    private fun setupWebRTC() {
        isInCall = true
        updateUI()
        
        // 创建 ICE 服务器列表
        val iceServers = listOf(
            PeerConnection.IceServer.builder(Config.STUN_SERVER)
                .createIceServer()
        )
        
        // 添加 TURN 服务器（如果有）
        if (Config.TURN_SERVER.isNotEmpty()) {
            // 解析 TURN 服务器配置
            // 格式：turn:username:password@host:port
        }
        
        // 创建 PeerConnection
        webRTCManager.createPeerConnection(iceServers)
        
        // 创建本地媒体流
        webRTCManager.setLocalRenderer(localVideoView)
        webRTCManager.setRemoteRenderer(remoteVideoView)
        webRTCManager.createLocalMediaStream()
        
        // 创建 Offer（如果是呼叫发起方）
        webRTCManager.createOffer()
        
        showStatus("正在建立媒体连接...")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 清理资源
        signalingClient.disconnect()
        webRTCManager.close()
    }
}
