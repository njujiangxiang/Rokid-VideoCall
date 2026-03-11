package com.rokid.videocall.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.rokid.videocall.Config
import org.webrtc.*

/**
 * WebRTC 管理器
 * 
 * 负责 WebRTC 连接管理、媒体流处理、编解码等
 */
class WebRTCManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WebRTCManager"
        
        @Volatile
        private var instance: WebRTCManager? = null
        
        fun getInstance(context: Context): WebRTCManager {
            return instance ?: synchronized(this) {
                instance ?: WebRTCManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // PeerConnection 工厂
    private val peerConnectionFactory: PeerConnectionFactory
    
    // 当前 PeerConnection
    private var peerConnection: PeerConnection? = null
    
    // 本地视频轨道
    private var localVideoTrack: VideoTrack? = null
    
    // 本地音频轨道
    private var localAudioTrack: AudioTrack? = null
    
    // 远程视频轨道
    private var remoteVideoTrack: VideoTrack? = null
    
    // 远程音频轨道
    private var remoteAudioTrack: AudioTrack? = null
    
    // 视频采集器
    private var videoCapturer: VideoCapturer? = null
    
    // 本地渲染器
    private var localRenderer: SurfaceViewRenderer? = null
    
    // 远程渲染器
    private var remoteRenderer: SurfaceViewRenderer? = null
    
    // 监听器
    private var listener: WebRTCListener? = null
    
    // EglBase
    private val eglBase = EglBase.create()
    
    // 音频管理器
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    init {
        // 初始化 PeerConnectionFactory
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        
        // 设置编解码器
        val videoEncoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,  // 支持硬件编码
            true   // 支持软件编码
        )
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .setAudioHardwareProcessingModule(null)
            .createPeerConnectionFactory()
        
        Log.d(TAG, "PeerConnectionFactory 初始化完成")
    }
    
    /**
     * 设置监听器
     */
    fun setListener(listener: WebRTCListener) {
        this.listener = listener
    }
    
    /**
     * 创建 PeerConnection
     */
    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>): Boolean {
        try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                // ICE 传输策略
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                
                // Bundle 策略
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                
                // RTCP 策略
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                
                // TCP 候选
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                
                // 连续 ICE
                continuousGathering = false
                
                // 候选数量
                iceCandidatePoolSize = 10
            }
            
            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                        Log.d(TAG, "信令状态变化：$newState")
                        listener?.onSignalingStateChanged(newState)
                    }
                    
                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                        Log.d(TAG, "ICE 连接状态变化：$newState")
                        listener?.onIceConnectionStateChanged(newState)
                        
                        when (newState) {
                            PeerConnection.IceConnectionState.CONNECTED -> {
                                listener?.onConnected()
                            }
                            PeerConnection.IceConnectionState.DISCONNECTED,
                            PeerConnection.IceConnectionState.FAILED -> {
                                listener?.onDisconnected()
                            }
                            else -> {}
                        }
                    }
                    
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {
                        Log.d(TAG, "ICE 接收状态：$receiving")
                    }
                    
                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                        Log.d(TAG, "ICE 收集状态：$newState")
                    }
                    
                    override fun onIceCandidate(candidate: IceCandidate) {
                        Log.d(TAG, "ICE Candidate: ${candidate.sdp}")
                        listener?.onICECandidate(candidate)
                    }
                    
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
                        Log.d(TAG, "ICE Candidates 移除：${candidates.size}")
                    }
                    
                    override fun onAddStream(stream: MediaStream) {
                        Log.d(TAG, "添加远程流")
                        stream.audioTracks.firstOrNull()?.let { track ->
                            remoteAudioTrack = track
                            listener?.onRemoteAudioTrackAdded(track)
                        }
                        stream.videoTracks.firstOrNull()?.let { track ->
                            remoteVideoTrack = track
                            listener?.onRemoteVideoTrackAdded(track)
                        }
                    }
                    
                    override fun onRemoveStream(stream: MediaStream) {
                        Log.d(TAG, "移除远程流")
                        listener?.onStreamRemoved()
                    }
                    
                    override fun onDataChannel(dataChannel: DataChannel) {
                        Log.d(TAG, "数据通道：${dataChannel.label()}")
                    }
                    
                    override fun onRenegotiationNeeded() {
                        Log.d(TAG, "需要重新协商")
                    }
                    
                    override fun onAddTrack(
                        receiver: RtpReceiver<out MediaStreamTrack>,
                        mediaStreams: Array<out MediaStream>
                    ) {
                        Log.d(TAG, "添加轨道：${receiver.kind()}")
                        val track = receiver.track()
                        when (track.kind()) {
                            "video" -> {
                                remoteVideoTrack = track as VideoTrack
                                listener?.onRemoteVideoTrackAdded(track)
                            }
                            "audio" -> {
                                remoteAudioTrack = track as AudioTrack
                                listener?.onRemoteAudioTrackAdded(track)
                            }
                        }
                    }
                }
            )
            
            Log.d(TAG, "PeerConnection 创建成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "创建 PeerConnection 失败", e)
            return false
        }
    }
    
    /**
     * 创建本地媒体流（摄像头 + 麦克风）
     */
    fun createLocalMediaStream(cameraFacing: CameraVideoCapturer.CameraFacing = CameraVideoCapturer.CameraFacing.FRONT) {
        try {
            // 创建音频轨道
            val audioConstraints = MediaConstraints().apply {
                with(optional) {
                    add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
                    add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
                    add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
                    add(MediaConstraints.KeyValuePair("highpassFilter", "true"))
                }
            }
            
            val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)
            localAudioTrack?.setEnabled(true)
            
            Log.d(TAG, "本地音频轨道创建成功")
            
            // 创建视频轨道
            val videoCapturer = createVideoCapturer(cameraFacing)
            val videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
            
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "VideoCaptureThread",
                eglBase.eglBaseContext
            )
            
            videoCapturer.initialize(
                surfaceTextureHelper,
                context,
                videoSource.capturerObserver
            )
            
            this.videoCapturer = videoCapturer
            
            // 启动采集（720p, 30fps）
            videoCapturer.startCapture(1280, 720, Config.VIDEO_FPS)
            
            localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
            localVideoTrack?.setEnabled(true)
            
            // 渲染到本地预览
            localRenderer?.let { renderer ->
                localVideoTrack?.addSink(renderer)
            }
            
            Log.d(TAG, "本地视频轨道创建成功")
            
            // 添加到 PeerConnection
            addLocalTracks()
            
        } catch (e: Exception) {
            Log.e(TAG, "创建本地媒体流失败", e)
            listener?.onError("创建媒体流失败：${e.message}")
        }
    }
    
    /**
     * 创建视频采集器
     */
    private fun createVideoCapturer(facing: CameraVideoCapturer.CameraFacing): VideoCapturer {
        return if (facing == CameraVideoCapturer.CameraFacing.FRONT) {
            Camera2Enumerator(context).apply {
                deviceNames.forEach { name ->
                    if (isFrontFacing(name)) {
                        Log.d(TAG, "使用前置摄像头：$name")
                    }
                }
            }
            Camera2VideoCapturer()
        } else {
            Camera2VideoCapturer()
        }
    }
    
    /**
     * 添加本地轨道到 PeerConnection
     */
    private fun addLocalTracks() {
        peerConnection?.let { pc ->
            // 添加音频轨道
            localAudioTrack?.let { audioTrack ->
                pc.addTrack(audioTrack, listOf("streamId"))
            }
            
            // 添加视频轨道
            localVideoTrack?.let { videoTrack ->
                pc.addTrack(videoTrack, listOf("streamId"))
            }
            
            Log.d(TAG, "本地轨道已添加到 PeerConnection")
        }
    }
    
    /**
     * 创建 Offer
     */
    fun createOffer() {
        val constraints = MediaConstraints().apply {
            with(optional) {
                add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Offer 创建成功")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "本地描述设置成功")
                        listener?.onSDPReady(sessionDescription)
                    }
                    override fun onSetFailure(p0: String?) {}
                }, sessionDescription)
            }
            
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "创建 Offer 失败：$error")
                listener?.onError("创建 Offer 失败：$error")
            }
        }, constraints)
    }
    
    /**
     * 创建 Answer
     */
    fun createAnswer() {
        val constraints = MediaConstraints().apply {
            with(optional) {
                add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Answer 创建成功")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "本地描述设置成功")
                        listener?.onSDPReady(sessionDescription)
                    }
                    override fun onSetFailure(p0: String?) {}
                }, sessionDescription)
            }
            
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "创建 Answer 失败：$error")
                listener?.onError("创建 Answer 失败：$error")
            }
        }, constraints)
    }
    
    /**
     * 设置远程描述
     */
    fun setRemoteDescription(sdp: String, type: String) {
        val sessionDescription = SessionDescription(
            SessionDescription.Type.fromCanonicalForm(type),
            sdp
        )
        
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            
            override fun onSetSuccess() {
                Log.d(TAG, "远程描述设置成功")
                listener?.onRemoteDescriptionSet()
            }
            
            override fun onSetFailure(error: String) {
                Log.e(TAG, "设置远程描述失败：$error")
                listener?.onError("设置远程描述失败：$error")
            }
        }, sessionDescription)
    }
    
    /**
     * 添加 ICE Candidate
     */
    fun addICECandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)?.let { success ->
            if (!success) {
                Log.w(TAG, "添加 ICE Candidate 失败")
            }
        }
    }
    
    /**
     * 设置本地渲染器
     */
    fun setLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(true)  // 前置摄像头镜像
        localVideoTrack?.addSink(renderer)
    }
    
    /**
     * 设置远程渲染器
     */
    fun setRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(false)
        remoteVideoTrack?.addSink(renderer)
    }
    
    /**
     * 切换摄像头
     */
    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }
    
    /**
     * 启用/禁用本地视频
     */
    fun enableLocalVideo(enable: Boolean) {
        localVideoTrack?.setEnabled(enable)
    }
    
    /**
     * 启用/禁用本地音频
     */
    fun enableLocalAudio(enable: Boolean) {
        localAudioTrack?.setEnabled(enable)
    }
    
    /**
     * 启用扬声器
     */
    fun enableSpeakerphone(enable: Boolean) {
        audioManager.isSpeakerphoneOn = enable
    }
    
    /**
     * 启用蓝牙耳机
     */
    fun enableBluetoothAudio(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = enable
        }
    }
    
    /**
     * 关闭连接
     */
    fun close() {
        Log.d(TAG, "关闭 WebRTC")
        
        // 停止采集
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        
        // 移除轨道
        peerConnection?.removeTrack(localAudioTrack?.sender ?: run { null })
        peerConnection?.removeTrack(localVideoTrack?.sender ?: run { null })
        
        // 关闭 PeerConnection
        peerConnection?.close()
        peerConnection = null
        
        // 释放渲染器
        localRenderer?.release()
        remoteRenderer?.release()
        
        // 释放 EglBase
        eglBase.release()
        
        Log.d(TAG, "WebRTC 已关闭")
    }
}
