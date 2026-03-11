package com.rokid.videocall.webrtc

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import org.webrtc.AudioTrack

/**
 * WebRTC 事件监听器接口
 */
interface WebRTCListener {
    
    /**
     * 信令状态变化
     */
    fun onSignalingStateChanged(state: PeerConnection.SignalingState) {}
    
    /**
     * ICE 连接状态变化
     */
    fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState) {}
    
    /**
     * WebRTC 连接成功
     */
    fun onConnected() {}
    
    /**
     * WebRTC 断开连接
     */
    fun onDisconnected() {}
    
    /**
     * ICE Candidate 就绪
     */
    fun onICECandidate(candidate: IceCandidate) {}
    
    /**
     * SDP (Offer/Answer) 就绪，可以发送给对方
     */
    fun onSDPReady(sdp: SessionDescription) {}
    
    /**
     * 远程描述设置成功
     */
    fun onRemoteDescriptionSet() {}
    
    /**
     * 远程视频轨道已添加
     */
    fun onRemoteVideoTrackAdded(track: VideoTrack) {}
    
    /**
     * 远程音频轨道已添加
     */
    fun onRemoteAudioTrackAdded(track: AudioTrack) {}
    
    /**
     * 媒体流已移除
     */
    fun onStreamRemoved() {}
    
    /**
     * 发生错误
     */
    fun onError(error: String) {}
}
