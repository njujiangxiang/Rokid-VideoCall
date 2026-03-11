"""
信令消息处理器

处理各种信令消息类型：
- 呼叫建立
- ICE Candidate 交换
- 控制指令
- 会话管理
"""

from fastapi import WebSocket
from typing import Dict, Any
import asyncio
import logging
import json

from ..webrtc import WebRTCPeer
from ..models import Session, SessionStatus

logger = logging.getLogger(__name__)


class SignalingHandler:
    """
    信令消息处理器
    """

    def __init__(self, websocket: WebSocket, session_id: str, session: Session):
        self.websocket = websocket
        self.session_id = session_id
        self.session = session
        self.webrtc_peer: WebRTCPeer | None = None
        self.is_call_active = False

    async def handle_message(self, message: Dict[str, Any]):
        """
        处理接收到的信令消息
        """
        msg_type = message.get("type", "")
        data = message.get("data", {})

        try:
            if msg_type == "call_start":
                await self.handle_call_start(data)
            elif msg_type == "call_answer":
                await self.handle_call_answer(data)
            elif msg_type == "ice_candidate":
                await self.handle_ice_candidate(data)
            elif msg_type == "call_end":
                await self.handle_call_end(data)
            elif msg_type == "camera_switch":
                await self.handle_camera_switch(data)
            elif msg_type == "volume_control":
                await self.handle_volume_control(data)
            elif msg_type == "take_photo":
                await self.handle_take_photo(data)
            elif msg_type == "battery_status":
                await self.handle_battery_status(data)
            else:
                logger.warning(f"未知消息类型：{msg_type}")
                await self.send_error(f"未知消息类型：{msg_type}")

        except Exception as e:
            logger.error(f"处理消息失败：{e}")
            await self.send_error(f"处理失败：{str(e)}")

    async def handle_call_start(self, data: Dict[str, Any]):
        """
        处理呼叫开始请求
        
        流程:
        1. 创建 WebRTC Peer
        2. 保存 SDP Offer
        3. 等待后端用户接受
        """
        logger.info(f"收到呼叫开始请求：{self.session_id}")

        # 更新会话状态
        self.session.status = SessionStatus.CALLING

        # 保存 SDP Offer
        sdp = data.get("sdp")
        if not sdp:
            await self.send_error("缺少 SDP Offer")
            return

        # 创建 WebRTC Peer（用于后端浏览器连接）
        self.webrtc_peer = WebRTCPeer(self.session_id)
        self.webrtc_peer.set_offer(sdp)

        # 通知前端（等待接受）
        await self.send_message("call_starting", {
            "session_id": self.session_id,
            "device_id": data.get("device_id"),
            "operator_id": data.get("operator_id")
        })

        logger.info(f"呼叫已发起，等待接受")

    async def handle_call_answer(self, data: Dict[str, Any]):
        """
        处理呼叫应答（后端用户接受了呼叫）
        
        流程:
        1. 获取后端的 SDP Answer
        2. 转发给眼镜端
        """
        logger.info(f"收到呼叫应答")

        # 更新会话状态
        self.session.status = SessionStatus.ACTIVE
        self.is_call_active = True

        # 获取 Answer SDP
        sdp = data.get("sdp")
        if not sdp:
            await self.send_error("缺少 SDP Answer")
            return

        # 转发给眼镜端（如果是后端发起的 Answer）
        # 这个通常在 Web 端处理，眼镜端直接收到 WebRTC 的 Answer

        logger.info(f"呼叫已建立")

    async def handle_ice_candidate(self, data: Dict[str, Any]):
        """
        处理 ICE Candidate
        
        流程:
        1. 保存 ICE Candidate
        2. 转发给对端
        """
        candidate = data.get("candidate")
        sdp_mid = data.get("sdpMid")
        sdp_mline_index = data.get("sdpMLineIndex")

        if not all([candidate, sdp_mid, sdp_mline_index is not None]):
            logger.warning("ICE Candidate 参数不完整")
            return

        logger.debug(f"收到 ICE Candidate")

        # 转发给 WebRTC Peer
        if self.webrtc_peer:
            await self.webrtc_peer.add_ice_candidate(
                candidate=candidate,
                sdp_mid=sdp_mid,
                sdp_mline_index=sdp_mline_index
            )

    async def handle_call_end(self, data: Dict[str, Any]):
        """
        处理呼叫结束
        """
        reason = data.get("reason", "unknown")
        logger.info(f"呼叫结束：{reason}")

        await self.end_call(reason)

    async def handle_camera_switch(self, data: Dict[str, Any]):
        """
        处理摄像头切换请求
        """
        camera = data.get("camera", "front")
        logger.info(f"切换摄像头：{camera}")

        # 转发给眼镜端
        await self.send_message("camera_switch", {
            "camera": camera
        })

    async def handle_volume_control(self, data: Dict[str, Any]):
        """
        处理音量控制
        """
        level = data.get("level", 0.8)
        logger.info(f"调节音量：{level}")

        # 转发给眼镜端
        await self.send_message("volume_control", {
            "level": level
        })

    async def handle_take_photo(self, data: Dict[str, Any]):
        """
        处理拍照请求
        """
        logger.info("收到拍照请求")

        # 转发给眼镜端
        await self.send_message("take_photo", {})

    async def handle_battery_status(self, data: Dict[str, Any]):
        """
        处理电量状态上报
        """
        level = data.get("level", 0)
        charging = data.get("charging", False)
        
        logger.info(f"电量状态：{level}%, 充电：{charging}")

        # 更新会话信息
        self.session.battery_level = level
        self.session.is_charging = charging

        # 可以在这里推送给 Web 端显示

    async def handle_disconnect(self):
        """
        处理断开连接
        """
        logger.info("处理断开连接")
        await self.end_call("disconnected")

    async def handle_error(self, error: str):
        """
        处理错误
        """
        logger.error(f"处理错误：{error}")
        await self.end_call(f"error: {error}")

    async def end_call(self, reason: str):
        """
        结束呼叫
        """
        if not self.is_call_active:
            return

        self.is_call_active = False
        self.session.status = SessionStatus.ENDED
        self.session.ended_at = asyncio.get_event_loop().time()

        # 清理 WebRTC Peer
        if self.webrtc_peer:
            await self.webrtc_peer.close()
            self.webrtc_peer = None

        # 通知客户端
        try:
            await self.send_message("call_end", {
                "reason": reason
            })
        except:
            pass  # 连接可能已经断开

        logger.info(f"呼叫已结束：{reason}")

    # ==================== 发送消息 ====================

    async def send_message(self, msg_type: str, data: Dict[str, Any]):
        """
        发送信令消息
        """
        message = {
            "type": msg_type,
            "data": data
        }
        await self.websocket.send_json(message)

    async def send_error(self, error: str):
        """
        发送错误消息
        """
        await self.send_message("error", {
            "message": error
        })
