"""
Rokid Glasses 远程视频连线系统 - 后端服务器

功能:
- WebSocket 信令服务
- WebRTC 媒体流处理
- REST API 会话管理
- Web 管理后台

作者：江翔 🦞
日期：2026-03-11
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Depends
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Dict, List, Optional
from datetime import datetime
import asyncio
import json
import uuid
import logging

# 导入内部模块
from .signaling import SignalingHandler
from .webrtc import WebRTCPeer
from .models import Session, SessionStatus

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 创建 FastAPI 应用
app = FastAPI(
    title="Rokid VideoCall Server",
    description="Rokid Glasses 远程视频连线系统后端",
    version="1.0.0"
)

# CORS 配置（允许前端跨域访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应该限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 挂载静态文件目录
app.mount("/static", StaticFiles(directory="static"), name="static")

# 内存数据库（生产环境应使用 PostgreSQL）
sessions_db: Dict[str, Session] = {}
active_connections: Dict[str, WebSocket] = {}


# ==================== 数据模型 ====================

class SessionCreateRequest(BaseModel):
    operator_id: str
    device_id: str
    description: Optional[str] = None


class SessionResponse(BaseModel):
    session_id: str
    operator_id: str
    device_id: str
    status: str
    created_at: str
    ended_at: Optional[str] = None


# ==================== WebSocket 信令端点 ====================

@app.websocket("/ws/signaling/{session_id}")
async def signaling_endpoint(websocket: WebSocket, session_id: str):
    """
    WebSocket 信令端点
    
    处理:
    - 呼叫建立
    - ICE Candidate 交换
    - 控制指令
    """
    # 接受连接
    await websocket.accept()
    logger.info(f"WebSocket 连接：{session_id}")
    
    # 检查会话是否存在
    if session_id not in sessions_db:
        await websocket.send_json({
            "type": "error",
            "data": {"message": "会话不存在"}
        })
        await websocket.close()
        return
    
    # 注册连接
    active_connections[session_id] = websocket
    sessions_db[session_id].status = SessionStatus.CONNECTING
    
    # 创建信令处理器
    handler = SignalingHandler(websocket, session_id, sessions_db[session_id])
    
    try:
        while True:
            # 接收消息
            data = await websocket.receive_text()
            message = json.loads(data)
            
            logger.info(f"收到信令消息：{message['type']}")
            
            # 处理消息
            await handler.handle_message(message)
            
    except WebSocketDisconnect:
        logger.info(f"WebSocket 断开：{session_id}")
        await handler.handle_disconnect()
    except Exception as e:
        logger.error(f"WebSocket 错误：{e}")
        await handler.handle_error(str(e))
    finally:
        # 清理连接
        if session_id in active_connections:
            del active_connections[session_id]


# ==================== REST API ====================

@app.post("/api/sessions", response_model=SessionResponse)
async def create_session(request: SessionCreateRequest):
    """
    创建新的视频通话会话
    """
    session_id = str(uuid.uuid4())
    
    session = Session(
        session_id=session_id,
        operator_id=request.operator_id,
        device_id=request.device_id,
        description=request.description,
        status=SessionStatus.CREATED,
        created_at=datetime.now()
    )
    
    sessions_db[session_id] = session
    
    logger.info(f"创建会话：{session_id}")
    
    return SessionResponse(
        session_id=session_id,
        operator_id=session.operator_id,
        device_id=session.device_id,
        status=session.status.value,
        created_at=session.created_at.isoformat()
    )


@app.get("/api/sessions", response_model=List[SessionResponse])
async def list_sessions(
    operator_id: Optional[str] = None,
    status: Optional[str] = None,
    limit: int = 20
):
    """
    获取会话列表（支持筛选）
    """
    filtered = sessions_db.values()
    
    if operator_id:
        filtered = filter(lambda s: s.operator_id == operator_id, filtered)
    
    if status:
        filtered = filter(lambda s: s.status.value == status, filtered)
    
    # 按创建时间倒序
    sorted_sessions = sorted(filtered, key=lambda s: s.created_at, reverse=True)
    
    return [
        SessionResponse(
            session_id=s.session_id,
            operator_id=s.operator_id,
            device_id=s.device_id,
            status=s.status.value,
            created_at=s.created_at.isoformat(),
            ended_at=s.ended_at.isoformat() if s.ended_at else None
        )
        for s in list(sorted_sessions)[:limit]
    ]


@app.get("/api/sessions/{session_id}", response_model=SessionResponse)
async def get_session(session_id: str):
    """
    获取会话详情
    """
    if session_id not in sessions_db:
        raise HTTPException(status_code=404, detail="会话不存在")
    
    session = sessions_db[session_id]
    
    return SessionResponse(
        session_id=session.session_id,
        operator_id=session.operator_id,
        device_id=session.device_id,
        status=session.status.value,
        created_at=session.created_at.isoformat(),
        ended_at=session.ended_at.isoformat() if session.ended_at else None
    )


@app.post("/api/sessions/{session_id}/end")
async def end_session(session_id: str):
    """
    结束会话
    """
    if session_id not in sessions_db:
        raise HTTPException(status_code=404, detail="会话不存在")
    
    session = sessions_db[session_id]
    session.status = SessionStatus.ENDED
    session.ended_at = datetime.now()
    
    # 通知客户端
    if session_id in active_connections:
        await active_connections[session_id].send_json({
            "type": "call_end",
            "data": {"reason": "server_request"}
        })
    
    logger.info(f"结束会话：{session_id}")
    
    return {"message": "会话已结束"}


@app.delete("/api/sessions/{session_id}")
async def delete_session(session_id: str):
    """
    删除会话记录
    """
    if session_id not in sessions_db:
        raise HTTPException(status_code=404, detail="会话不存在")
    
    del sessions_db[session_id]
    logger.info(f"删除会话：{session_id}")
    
    return {"message": "会话已删除"}


# ==================== Web 管理后台 ====================

@app.get("/", response_class=HTMLResponse)
async def index():
    """
    Web 管理后台首页
    """
    return FileResponse("static/index.html")


@app.get("/health")
async def health_check():
    """
    健康检查接口
    """
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "active_sessions": len(active_connections),
        "total_sessions": len(sessions_db)
    }


# ==================== 应用生命周期 ====================

@app.on_event("startup")
async def startup_event():
    """
    应用启动时执行
    """
    logger.info("服务器启动...")
    logger.info(f"WebRTC 配置：已加载")
    logger.info(f"WebSocket 端点：ws://localhost:8765/ws/signaling/{{session_id}}")


@app.on_event("shutdown")
async def shutdown_event():
    """
    应用关闭时执行
    """
    logger.info("服务器关闭...")
    
    # 关闭所有活跃连接
    for session_id, websocket in active_connections.items():
        try:
            await websocket.close()
        except:
            pass
    
    active_connections.clear()
    logger.info("所有连接已关闭")


# ==================== 主入口 ====================

if __name__ == "__main__":
    import uvicorn
    
    logger.info("=" * 60)
    logger.info("Rokid Glasses 远程视频连线系统 - 后端服务器")
    logger.info("=" * 60)
    logger.info("")
    logger.info("📱 WebSocket 信令：ws://localhost:8765/ws/signaling/{session_id}")
    logger.info("🌐 REST API:      http://localhost:8765/api/sessions")
    logger.info("💻 Web 管理后台：   http://localhost:8765/")
    logger.info("")
    logger.info("按 Ctrl+C 停止服务器")
    logger.info("=" * 60)
    
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8765,
        reload=True,  # 开发模式启用热重载
        log_level="info"
    )
