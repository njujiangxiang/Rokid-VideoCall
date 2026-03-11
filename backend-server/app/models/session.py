"""
会话数据模型
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Optional


class SessionStatus(Enum):
    """
    会话状态枚举
    """
    CREATED = "created"        # 已创建
    CONNECTING = "connecting"  # 连接中
    CALLING = "calling"        # 呼叫中（等待接受）
    ACTIVE = "active"          # 通话中
    ENDED = "ended"           # 已结束
    FAILED = "failed"         # 失败


@dataclass
class Session:
    """
    视频通话会话模型
    """
    session_id: str
    operator_id: str
    device_id: str
    status: SessionStatus
    created_at: datetime
    
    # 可选字段
    description: Optional[str] = None
    ended_at: Optional[float] = None  # 使用 Unix 时间戳
    
    # 设备状态
    battery_level: int = 0  # 电量百分比
    is_charging: bool = False
    
    # 网络质量
    video_bitrate: int = 0  # 视频码率 (kbps)
    audio_bitrate: int = 0  # 音频码率 (kbps)
    latency_ms: int = 0     # 延迟 (毫秒)
    
    # 录制信息
    recording_path: Optional[str] = None
    recording_duration: int = 0  # 录制时长 (秒)

    def duration(self) -> int:
        """
        计算通话时长（秒）
        """
        if self.ended_at is None:
            return 0
        
        if isinstance(self.created_at, datetime):
            start_ts = self.created_at.timestamp()
        else:
            start_ts = self.created_at
        
        return int(self.ended_at - start_ts)

    def to_dict(self) -> dict:
        """
        转换为字典
        """
        return {
            "session_id": self.session_id,
            "operator_id": self.operator_id,
            "device_id": self.device_id,
            "status": self.status.value,
            "created_at": self.created_at.isoformat() if isinstance(self.created_at, datetime) else self.created_at,
            "ended_at": self.ended_at,
            "battery_level": self.battery_level,
            "is_charging": self.is_charging,
            "duration": self.duration()
        }
