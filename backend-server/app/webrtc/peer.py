"""
WebRTC Peer 连接管理

使用 aiortc 库实现 WebRTC 功能
aiortc 是 Python 的 WebRTC 实现，基于 asyncio
"""

import asyncio
import logging
from typing import Optional, Callable
import json

try:
    from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceCandidate
    from aiortc.contrib.media import MediaPlayer, MediaRecorder
    AIORTC_AVAILABLE = True
except ImportError:
    AIORTC_AVAILABLE = False
    logger.warning("aiortc 未安装，WebRTC 功能将不可用")

logger = logging.getLogger(__name__)


class WebRTCPeer:
    """
    WebRTC Peer 连接
    
    功能:
    - 管理 PeerConnection
    - 处理 SDP Offer/Answer
    - 处理 ICE Candidate
    - 媒体流录制（可选）
    """

    def __init__(self, session_id: str):
        self.session_id = session_id
        self.pc: Optional[RTCPeerConnection] = None
        self.offer: Optional[RTCSessionDescription] = None
        self.answer: Optional[RTCSessionDescription] = None
        self.is_connected = False
        
        # 媒体录制（可选）
        self.recorder: Optional[MediaRecorder] = None
        self.recording_path: Optional[str] = None

        if AIORTC_AVAILABLE:
            self._init_peer_connection()

    def _init_peer_connection(self):
        """
        初始化 PeerConnection
        """
        if not AIORTC_AVAILABLE:
            return

        self.pc = RTCPeerConnection()

        # 设置事件处理器
        @self.pc.on("iceconnectionstatechange")
        async def on_iceconnectionstatechange():
            logger.info(f"ICE 状态：{self.pc.iceConnectionState}")
            if self.pc.iceConnectionState == "connected":
                self.is_connected = True
            elif self.pc.iceConnectionState in ["failed", "closed"]:
                self.is_connected = False

        @self.pc.on("connectionstatechange")
        async def on_connectionstatechange():
            logger.info(f"连接状态：{self.pc.connectionState}")

        @self.pc.on("track")
        def on_track(track):
            logger.info(f"收到轨道：{track.kind}")
            # 可以在这里处理接收到的音视频流

    def set_offer(self, sdp: str):
        """
        设置远端 Offer
        """
        if not AIORTC_AVAILABLE:
            logger.error("aiortc 未安装")
            return

        self.offer = RTCSessionDescription(sdp=sdp, type="offer")

    async def create_answer(self) -> str:
        """
        创建 Answer
        
        Returns:
            SDP Answer 字符串
        """
        if not AIORTC_AVAILABLE or not self.offer:
            return ""

        # 设置远端 Offer
        await self.pc.setRemoteDescription(self.offer)

        # 创建 Answer
        answer = await self.pc.createAnswer()
        await self.pc.setLocalDescription(answer)
        self.answer = answer

        logger.info("Answer 创建成功")
        return answer.sdp

    async def add_ice_candidate(self, candidate: str, sdp_mid: str, sdp_mline_index: int):
        """
        添加 ICE Candidate
        """
        if not AIORTC_AVAILABLE:
            return

        try:
            ice_candidate = RTCIceCandidate(
                candidate=candidate,
                sdpMid=sdp_mid,
                sdpMLineIndex=sdp_mline_index
            )
            await self.pc.addIceCandidate(ice_candidate)
            logger.debug("ICE Candidate 已添加")
        except Exception as e:
            logger.error(f"添加 ICE Candidate 失败：{e}")

    async def close(self):
        """
        关闭 PeerConnection
        """
        if self.pc:
            await self.pc.close()
            self.pc = None
            self.is_connected = False
            logger.info("PeerConnection 已关闭")

        if self.recorder:
            await self.recorder.stop()
            self.recorder = None

    async def start_recording(self, output_path: str):
        """
        开始录制通话
        
        Args:
            output_path: 输出文件路径（.mp4 或 .wav）
        """
        if not AIORTC_AVAILABLE:
            return

        self.recording_path = output_path
        self.recorder = MediaRecorder(output_path)
        await self.recorder.start()
        logger.info(f"开始录制：{output_path}")

    async def stop_recording(self):
        """
        停止录制
        """
        if self.recorder:
            await self.recorder.stop()
            self.recorder = None
            logger.info(f"录制完成：{self.recording_path}")

    def get_stats(self) -> dict:
        """
        获取连接统计信息
        """
        if not self.pc:
            return {}

        return {
            "session_id": self.session_id,
            "is_connected": self.is_connected,
            "ice_state": self.pc.iceConnectionState if self.pc else None,
            "connection_state": self.pc.connectionState if self.pc else None
        }
