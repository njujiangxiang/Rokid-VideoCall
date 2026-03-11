package com.rokid.videocall

import android.content.Context
import android.util.Log
import org.webrtc.*

/**
 * WebRTC 管理器
 * 
 * 功能:
 * - 初始化 PeerConnection
 * - 创建 Offer/Answer
 * - 管理音视频轨道
 * - 处理 ICE Candidate
 * 
 * 使用 org.webrtc 库实现
 */
class WebRTCManager(
    private val context: Context,
    private val callback: WebRTCCallback
) {

    interface WebRTCCallback {
        fun onIceCandidate(candidate: IceCandidate)
        fun onRemoteStreamAdded(stream: MediaStream)
        fun onCallEstablished()
        fun onCallEnded()
    }

    interface SdpCallback {
        fun onSuccess(sdp: SessionDescription)
        fun onError(error: String)
    }

    companion object {
        private const val TAG = "WebRTCManager"
        
        // STUN/TURN 服务器配置（实际部署需要替换为自己的服务器）
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            // 添加 TURN 服务器（用于 NAT 穿透）
            // PeerConnection.IceServer.builder("turn:your-turn-server.com:3478")
            //     .setUsername("user")
            //     .setPassword("pass")
            //     .createIceServer()
        )
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    
    private var isInitialized = false
    private var iceCandidatesQueue = mutableListOf<IceCandidate>()

    /**
     * 初始化 WebRTC
     * 必须在应用启动时调用一次
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "WebRTC 已初始化")
            return
        }

        Log.d(TAG, "初始化 WebRTC...")

        // 初始化 PeerConnectionFactory
        PeerConnectionFactory.initialize(
            PeerConnectionInitializationOptions.builder(context).build()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()

        isInitialized = true
        Log.d(TAG, "WebRTC 初始化完成")
    }

    /**
     * 创建 PeerConnection
     */
    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            // 使用 DTLS 加密
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            
            // ICE 传输策略
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            
            // 候选集合策略
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "生成 ICE Candidate")
                callback.onIceCandidate(candidate)
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "添加远端流：${stream.id}")
                callback.onRemoteStreamAdded(stream)
            }

            override fun onRemoveStream(stream: MediaStream) {
                Log.d(TAG, "移除远端流")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE 连接状态变化：$state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        callback.onCallEstablished()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        callback.onCallEnded()
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
            override fun onDataChannel(dataChannel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }

        return peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
    }

    /**
     * 添加视频轨道
     */
    fun addVideoTrack(videoSource: VideoSource) {
        if (!isInitialized) {
            throw IllegalStateException("WebRTC 未初始化")
        }

        localVideoTrack = peerConnectionFactory?.createVideoTrack(
            "video_track",
            videoSource
        )

        // 添加到 PeerConnection
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("video"))
        }

        Log.d(TAG, "视频轨道已添加")
    }

    /**
     * 添加音频轨道
     */
    fun addAudioTrack(audioSource: AudioSource) {
        if (!isInitialized) {
            throw IllegalStateException("WebRTC 未初始化")
        }

        localAudioTrack = peerConnectionFactory?.createAudioTrack(
            "audio_track",
            audioSource
        )

        // 添加到 PeerConnection
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("audio"))
        }

        Log.d(TAG, "音频轨道已添加")
    }

    /**
     * 创建 Offer
     */
    fun createOffer(callback: SdpCallback) {
        peerConnection = createPeerConnection()

        if (peerConnection == null) {
            callback.onError("创建 PeerConnection 失败")
            return
        }

        val constraints = MediaConstraints().apply {
            with(mandatory) {
                add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            }
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "创建 Offer 成功")
                
                // 设置本地 Description
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "设置本地 Description 成功")
                        callback.onSuccess(sdp)
                    }
                    override fun onSetFailure(error: String?) {
                        callback.onError("设置本地 Description 失败：$error")
                    }
                }, sdp)
            }

            override fun onCreateFailure(error: String) {
                Log.e(TAG, "创建 Offer 失败：$error")
                callback.onError(error)
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    /**
     * 设置远端 Description（Answer）
     */
    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "设置远端 Description 成功")
                
                // 处理排队的 ICE candidates
                iceCandidatesQueue.forEach { candidate ->
                    peerConnection?.addIceCandidate(candidate)
                }
                iceCandidatesQueue.clear()
            }
            override fun onSetFailure(error: String) {
                Log.e(TAG, "设置远端 Description 失败：$error")
            }
        }, sdp)
    }

    /**
     * 添加 ICE Candidate
     */
    fun addIceCandidate(candidate: IceCandidate) {
        if (peerConnection?.remoteDescription == null) {
            // 远端 Description 还未设置，先缓存
            iceCandidatesQueue.add(candidate)
            Log.d(TAG, "ICE Candidate 已缓存")
        } else {
            peerConnection?.addIceCandidate(candidate)
            Log.d(TAG, "ICE Candidate 已添加")
        }
    }

    /**
     * 创建 Answer（用于被叫方）
     */
    fun createAnswer(offerSdp: String, callback: SdpCallback) {
        if (!isInitialized) {
            callback.onError("WebRTC 未初始化")
            return
        }

        // 设置远端 Offer
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() {
                // 创建 Answer
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        // 设置本地 Answer
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(sdp: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) {}
                            override fun onSetSuccess() {
                                callback.onSuccess(sdp)
                            }
                            override fun onSetFailure(error: String?) {
                                callback.onError("设置本地 Answer 失败：$error")
                            }
                        }, sdp)
                    }

                    override fun onCreateFailure(error: String) {
                        callback.onError(error)
                    }

                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String) {}
                }, MediaConstraints())
            }
            override fun onSetFailure(error: String) {
                callback.onError("设置远端 Offer 失败：$error")
            }
        }, offer)
    }

    /**
     * 启用/禁用本地视频
     */
    fun enableLocalVideo(enable: Boolean) {
        localVideoTrack?.setEnabled(enable)
        Log.d(TAG, "本地视频已${if (enable) "启用" else "禁用"}")
    }

    /**
     * 启用/禁用本地音频
     */
    fun enableLocalAudio(enable: Boolean) {
        localAudioTrack?.setEnabled(enable)
        Log.d(TAG, "本地音频已${if (enable) "启用" else "禁用"}")
    }

    /**
     * 释放资源
     */
    fun dispose() {
        Log.d(TAG, "释放 WebRTC 资源")
        
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        remoteVideoTrack?.dispose()
        remoteAudioTrack?.dispose()
        
        peerConnection?.close()
        peerConnection?.dispose()
        
        peerConnectionFactory?.dispose()
        
        localVideoTrack = null
        localAudioTrack = null
        remoteVideoTrack = null
        remoteAudioTrack = null
        peerConnection = null
        peerConnectionFactory = null
        isInitialized = false
        iceCandidatesQueue.clear()
    }
}
