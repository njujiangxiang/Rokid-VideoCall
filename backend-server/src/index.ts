/**
 * Rokid Glasses 远程视频连线系统 - 信令服务器
 * 
 * 功能:
 * - WebSocket 信令中转
 * - 房间管理
 * - 设备状态同步
 * - WebRTC SDP 交换
 */

import express from 'express';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import cors from 'cors';
import { v4 as uuidv4 } from 'uuid';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const server = createServer(app);
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'rokid-videocall-secret-key';

// 中间件
app.use(cors());
app.use(express.json());

// ==================== 内存存储（生产环境请替换为数据库）====================

// 用户存储
interface User {
  id: string;
  username: string;
  passwordHash: string;
  role: 'admin' | 'expert' | 'operator';
  createdAt: number;
}

const users: Map<string, User> = new Map();

// 设备存储
interface Device {
  id: string;
  sn: string;
  name: string;
  userId?: string;
  online: boolean;
  battery: number;
  signal: number;
  lastSeen: number;
}

const devices: Map<string, Device> = new Map();

// WebSocket 连接存储
interface ClientConnection {
  ws: WebSocket;
  userId?: string;
  deviceId?: string;
  roomId?: string;
  isAlive: boolean;
}

const clients: Map<string, ClientConnection> = new Map();

// 房间存储
interface Room {
  id: string;
  name: string;
  participants: string[];  // clientId 列表
  createdAt: number;
  createdBy: string;
}

const rooms: Map<string, Room> = new Map();

// 呼叫记录
interface CallRecord {
  id: string;
  roomId: string;
  callerId: string;
  calleeId: string;
  status: 'initiated' | 'accepted' | 'rejected' | 'ended';
  startTime?: number;
  endTime?: number;
  duration?: number;
}

const callRecords: Map<string, CallRecord> = new Map();

// ==================== WebSocket 信令服务器 ====================

const wss = new WebSocketServer({ server, path: '/ws' });

// 信令消息类型
enum MessageType {
  // 认证
  AUTH = 'auth',
  AUTH_RESPONSE = 'auth.response',
  
  // 房间管理
  ROOM_JOIN = 'room.join',
  ROOM_LEAVE = 'room.leave',
  ROOM_CREATED = 'room.created',
  ROOM_JOINED = 'room.joined',
  ROOM_LEFT = 'room.left',
  
  // 呼叫控制
  CALL_INITIATE = 'call.initiate',
  CALL_ACCEPT = 'call.accept',
  CALL_REJECT = 'call.reject',
  CALL_END = 'call.end',
  
  // WebRTC 信令
  SDP_OFFER = 'webrtc.offer',
  SDP_ANSWER = 'webrtc.answer',
  ICE_CANDIDATE = 'webrtc.ice',
  
  // 设备管理
  DEVICE_REGISTER = 'device.register',
  DEVICE_STATUS = 'device.status',
  DEVICE_CONTROL = 'device.control',
  
  // 错误
  ERROR = 'error',
}

// 消息结构
interface SignalingMessage {
  type: MessageType;
  roomId?: string;
  from?: string;
  to?: string;
  data?: any;
  timestamp?: number;
}

wss.on('connection', (ws: WebSocket) => {
  const clientId = uuidv4();
  const connection: ClientConnection = {
    ws,
    isAlive: true,
  };
  
  clients.set(clientId, connection);
  console.log(`[WS] 新连接：${clientId}`);
  
  // 发送客户端 ID
  sendMessage(ws, {
    type: MessageType.AUTH_RESPONSE,
    data: { clientId },
  });
  
  // 心跳检测
  ws.on('pong', () => {
    connection.isAlive = true;
  });
  
  // 接收消息
  ws.on('message', (data: Buffer) => {
    try {
      const message: SignalingMessage = JSON.parse(data.toString());
      handleMessage(clientId, message);
    } catch (error) {
      console.error('[WS] 消息解析错误:', error);
      sendMessage(ws, {
        type: MessageType.ERROR,
        data: { message: 'Invalid message format' },
      });
    }
  });
  
  // 断开连接
  ws.on('close', () => {
    console.log(`[WS] 连接断开：${clientId}`);
    
    // 清理房间
    if (connection.roomId) {
      leaveRoom(clientId, connection.roomId);
    }
    
    // 更新设备状态
    if (connection.deviceId) {
      const device = devices.get(connection.deviceId);
      if (device) {
        device.online = false;
        broadcastDeviceStatus(device);
      }
    }
    
    clients.delete(clientId);
  });
  
  // 错误处理
  ws.on('error', (error) => {
    console.error(`[WS] 错误 [${clientId}]:`, error);
  });
});

// 心跳检查定时器
const heartbeatInterval = setInterval(() => {
  clients.forEach((connection, clientId) => {
    if (!connection.isAlive) {
      console.log(`[WS] 心跳超时，断开连接：${clientId}`);
      connection.ws.terminate();
      clients.delete(clientId);
      return;
    }
    
    connection.isAlive = false;
    connection.ws.ping();
  });
}, 30000);

wss.on('close', () => {
  clearInterval(heartbeatInterval);
});

// ==================== 消息处理 ====================

function handleMessage(clientId: string, message: SignalingMessage) {
  const connection = clients.get(clientId);
  if (!connection) return;
  
  console.log(`[WS] 收到消息 [${clientId}]: ${message.type}`);
  
  switch (message.type) {
    case MessageType.AUTH:
      handleAuth(clientId, message);
      break;
      
    case MessageType.ROOM_JOIN:
      handleRoomJoin(clientId, message);
      break;
      
    case MessageType.ROOM_LEAVE:
      handleRoomLeave(clientId, message);
      break;
      
    case MessageType.CALL_INITIATE:
      handleCallInitiate(clientId, message);
      break;
      
    case MessageType.CALL_ACCEPT:
      handleCallAccept(clientId, message);
      break;
      
    case MessageType.CALL_END:
      handleCallEnd(clientId, message);
      break;
      
    case MessageType.SDP_OFFER:
    case MessageType.SDP_ANSWER:
    case MessageType.ICE_CANDIDATE:
      handleWebRTCSignaling(clientId, message);
      break;
      
    case MessageType.DEVICE_REGISTER:
      handleDeviceRegister(clientId, message);
      break;
      
    case MessageType.DEVICE_STATUS:
      handleDeviceStatus(clientId, message);
      break;
      
    default:
      console.warn(`[WS] 未知消息类型：${message.type}`);
  }
}

// 认证处理
function handleAuth(clientId: string, message: SignalingMessage) {
  const { token, username, password } = message.data || {};
  const connection = clients.get(clientId);
  
  if (token) {
    // Token 认证
    try {
      const decoded = jwt.verify(token, JWT_SECRET) as { userId: string };
      const user = users.get(decoded.userId);
      
      if (user && connection) {
        connection.userId = decoded.userId;
        sendMessage(connection.ws, {
          type: MessageType.AUTH_RESPONSE,
          data: { success: true, userId: user.id, role: user.role },
        });
        console.log(`[AUTH] 用户登录：${user.username}`);
      }
    } catch (error) {
      sendMessage(connection!.ws, {
        type: MessageType.AUTH_RESPONSE,
        data: { success: false, error: 'Invalid token' },
      });
    }
  } else if (username && password) {
    // 用户名密码认证（简化版，生产环境请查询数据库）
    let targetUser: User | undefined;
    users.forEach(user => {
      if (user.username === username) targetUser = user;
    });
    
    if (targetUser && bcrypt.compareSync(password, targetUser.passwordHash)) {
      if (connection) {
        connection.userId = targetUser.id;
        const token = jwt.sign({ userId: targetUser.id }, JWT_SECRET, { expiresIn: '24h' });
        sendMessage(connection.ws, {
          type: MessageType.AUTH_RESPONSE,
          data: { success: true, userId: targetUser.id, role: targetUser.role, token },
        });
        console.log(`[AUTH] 用户登录：${targetUser.username}`);
      }
    } else {
      sendMessage(connection!.ws, {
        type: MessageType.AUTH_RESPONSE,
        data: { success: false, error: 'Invalid credentials' },
      });
    }
  }
}

// 加入房间
function handleRoomJoin(clientId: string, message: SignalingMessage) {
  const { roomId, roomName } = message.data || {};
  const connection = clients.get(clientId);
  if (!connection) return;
  
  let room = rooms.get(roomId);
  
  if (!room && roomName) {
    // 创建新房间
    room = {
      id: roomId || uuidv4(),
      name: roomName,
      participants: [],
      createdAt: Date.now(),
      createdBy: clientId,
    };
    rooms.set(room.id, room);
    
    console.log(`[ROOM] 创建房间：${room.id}`);
  }
  
  if (room) {
    room.participants.push(clientId);
    connection.roomId = room.id;
    
    sendMessage(connection.ws, {
      type: MessageType.ROOM_JOINED,
      roomId: room.id,
      data: {
        roomId: room.id,
        roomName: room.name,
        participants: room.participants,
      },
    });
    
    // 通知房间内其他成员
    broadcastToRoom(room.id, {
      type: MessageType.ROOM_JOINED,
      roomId: room.id,
      from: clientId,
      data: { participantId: clientId },
    }, clientId);
    
    console.log(`[ROOM] ${clientId} 加入房间：${room.id}`);
  }
}

// 离开房间
function handleRoomLeave(clientId: string, message: SignalingMessage) {
  const { roomId } = message.data || {};
  leaveRoom(clientId, roomId);
}

function leaveRoom(clientId: string, roomId?: string) {
  const connection = clients.get(clientId);
  const targetRoomId = roomId || connection?.roomId;
  
  if (!targetRoomId) return;
  
  const room = rooms.get(targetRoomId);
  if (room) {
    room.participants = room.participants.filter(id => id !== clientId);
    
    if (connection) {
      connection.roomId = undefined;
      sendMessage(connection.ws, {
        type: MessageType.ROOM_LEFT,
        roomId: targetRoomId,
      });
    }
    
    // 通知房间内其他成员
    broadcastToRoom(targetRoomId, {
      type: MessageType.ROOM_LEFT,
      roomId: targetRoomId,
      from: clientId,
      data: { participantId: clientId },
    });
    
    // 如果房间为空，删除房间
    if (room.participants.length === 0) {
      rooms.delete(targetRoomId);
      console.log(`[ROOM] 删除空房间：${targetRoomId}`);
    }
    
    console.log(`[ROOM] ${clientId} 离开房间：${targetRoomId}`);
  }
}

// 发起呼叫
function handleCallInitiate(clientId: string, message: SignalingMessage) {
  const { targetId, type } = message.data || {};
  const room = rooms.get(message.roomId!);
  
  if (!room) return;
  
  // 创建呼叫记录
  const callId = uuidv4();
  const callRecord: CallRecord = {
    id: callId,
    roomId: room.id,
    callerId: clientId,
    calleeId: targetId,
    status: 'initiated',
    startTime: Date.now(),
  };
  callRecords.set(callId, callRecord);
  
  // 转发给被呼叫方
  const targetConnection = clients.get(targetId);
  if (targetConnection) {
    sendMessage(targetConnection.ws, {
      type: MessageType.CALL_INITIATE,
      roomId: room.id,
      from: clientId,
      data: {
        callId,
        callerId: clientId,
        type: type || 'video',
      },
    });
  }
  
  console.log(`[CALL] 发起呼叫：${callId} (${clientId} -> ${targetId})`);
}

// 接受呼叫
function handleCallAccept(clientId: string, message: SignalingMessage) {
  const { callId } = message.data || {};
  const callRecord = callRecords.get(callId);
  
  if (callRecord) {
    callRecord.status = 'accepted';
    
    // 转发给呼叫方
    const callerConnection = clients.get(callRecord.callerId);
    if (callerConnection) {
      sendMessage(callerConnection.ws, {
        type: MessageType.CALL_ACCEPT,
        roomId: message.roomId,
        from: clientId,
        data: { callId },
      });
    }
    
    console.log(`[CALL] 呼叫已接受：${callId}`);
  }
}

// 结束呼叫
function handleCallEnd(clientId: string, message: SignalingMessage) {
  const { callId } = message.data || {};
  const callRecord = callRecords.get(callId);
  
  if (callRecord) {
    callRecord.status = 'ended';
    callRecord.endTime = Date.now();
    callRecord.duration = callRecord.endTime - callRecord.startTime!;
    
    // 通知对方
    const otherId = callRecord.callerId === clientId ? callRecord.calleeId : callRecord.callerId;
    const otherConnection = clients.get(otherId);
    if (otherConnection) {
      sendMessage(otherConnection.ws, {
        type: MessageType.CALL_END,
        roomId: message.roomId,
        from: clientId,
        data: { callId },
      });
    }
    
    console.log(`[CALL] 呼叫已结束：${callId}, 时长：${Math.round(callRecord.duration! / 1000)}s`);
  }
}

// WebRTC 信令处理
function handleWebRTCSignaling(clientId: string, message: SignalingMessage) {
  const { to, sdp, candidate } = message.data || {};
  
  if (!to) return;
  
  const targetConnection = clients.get(to);
  if (targetConnection) {
    sendMessage(targetConnection.ws, {
      ...message,
      from: clientId,
    });
  }
}

// 设备注册
function handleDeviceRegister(clientId: string, message: SignalingMessage) {
  const { sn, name } = message.data || {};
  const connection = clients.get(clientId);
  
  if (!sn || !connection) return;
  
  // 查找或创建设备
  let device: Device | undefined;
  devices.forEach(d => {
    if (d.sn === sn) device = d;
  });
  
  if (!device) {
    device = {
      id: uuidv4(),
      sn,
      name: name || `Device-${sn.slice(-6)}`,
      online: true,
      battery: 100,
      signal: -50,
      lastSeen: Date.now(),
    };
    devices.set(device.id, device);
  } else {
    device.online = true;
    device.lastSeen = Date.now();
  }
  
  connection.deviceId = device.id;
  
  sendMessage(connection.ws, {
    type: MessageType.DEVICE_REGISTER,
    data: {
      success: true,
      deviceId: device.id,
      sn: device.sn,
      name: device.name,
    },
  });
  
  console.log(`[DEVICE] 设备注册：${device.name} (${device.sn})`);
}

// 设备状态上报
function handleDeviceStatus(clientId: string, message: SignalingMessage) {
  const { battery, signal, temperature } = message.data || {};
  const connection = clients.get(clientId);
  
  if (!connection?.deviceId) return;
  
  const device = devices.get(connection.deviceId);
  if (device) {
    if (battery !== undefined) device.battery = battery;
    if (signal !== undefined) device.signal = signal;
    if (temperature !== undefined) device.temperature = temperature;
    device.lastSeen = Date.now();
    
    broadcastDeviceStatus(device);
  }
}

// ==================== 辅助函数 ====================

function sendMessage(ws: WebSocket, message: SignalingMessage) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      ...message,
      timestamp: Date.now(),
    }));
  }
}

function broadcastToRoom(roomId: string, message: SignalingMessage, excludeClientId?: string) {
  const room = rooms.get(roomId);
  if (!room) return;
  
  room.participants.forEach(clientId => {
    if (clientId !== excludeClientId) {
      const connection = clients.get(clientId);
      if (connection) {
        sendMessage(connection.ws, message);
      }
    }
  });
}

function broadcastDeviceStatus(device: Device) {
  clients.forEach((connection, clientId) => {
    if (connection.userId) {  // 只发送给已认证用户
      sendMessage(connection.ws, {
        type: MessageType.DEVICE_STATUS,
        data: device,
      });
    }
  });
}

// ==================== REST API ====================

// 用户注册（仅用于测试）
app.post('/api/v1/auth/register', async (req, res) => {
  const { username, password, role = 'expert' } = req.body;
  
  if (users.size >= 100) {  // 限制用户数量
    return res.status(400).json({ error: 'User limit reached' });
  }
  
  const userId = uuidv4();
  const passwordHash = await bcrypt.hash(password, 10);
  
  const user: User = {
    id: userId,
    username,
    passwordHash,
    role: role as 'admin' | 'expert' | 'operator',
    createdAt: Date.now(),
  };
  
  users.set(userId, user);
  
  const token = jwt.sign({ userId }, JWT_SECRET, { expiresIn: '24h' });
  
  res.json({
    success: true,
    user: { id: user.id, username: user.username, role: user.role },
    token,
  });
});

// 用户登录
app.post('/api/v1/auth/login', async (req, res) => {
  const { username, password } = req.body;
  
  let targetUser: User | undefined;
  users.forEach(user => {
    if (user.username === username) targetUser = user;
  });
  
  if (!targetUser) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }
  
  const valid = await bcrypt.compare(password, targetUser.passwordHash);
  if (!valid) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }
  
  const token = jwt.sign({ userId: targetUser.id }, JWT_SECRET, { expiresIn: '24h' });
  
  res.json({
    success: true,
    user: { id: targetUser.id, username: targetUser.username, role: targetUser.role },
    token,
  });
});

// 获取设备列表
app.get('/api/v1/devices', (req, res) => {
  const deviceList = Array.from(devices.values()).map(d => ({
    id: d.id,
    sn: d.sn,
    name: d.name,
    online: d.online,
    battery: d.battery,
    signal: d.signal,
    lastSeen: d.lastSeen,
  }));
  
  res.json({ devices: deviceList });
});

// 获取呼叫记录
app.get('/api/v1/calls', (req, res) => {
  const callList = Array.from(callRecords.values()).map(c => ({
    id: c.id,
    roomId: c.roomId,
    callerId: c.callerId,
    calleeId: c.calleeId,
    status: c.status,
    startTime: c.startTime,
    endTime: c.endTime,
    duration: c.duration,
  }));
  
  res.json({ calls: callList });
});

// 健康检查
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    uptime: process.uptime(),
    clients: clients.size,
    rooms: rooms.size,
    devices: devices.size,
  });
});

// ==================== 启动服务器 ====================

server.listen(PORT, () => {
  console.log(`
╔═══════════════════════════════════════════════════════════╗
║                                                           ║
║   🦞 Rokid Glasses 视频连线系统 - 信令服务器                 ║
║                                                           ║
║   监听端口：${PORT}                                          ║
║   WebSocket: ws://localhost:${PORT}/ws                      ║
║   REST API:  http://localhost:${PORT}/api/v1               ║
║   健康检查：http://localhost:${PORT}/health                 ║
║                                                           ║
║   开发模式：运行 npm run dev                               ║
║   生产模式：运行 npm run build && npm start                ║
║                                                           ║
╚═══════════════════════════════════════════════════════════╝
  `);
});

// 优雅关闭
process.on('SIGTERM', () => {
  console.log('[SERVER] 收到 SIGTERM，优雅关闭...');
  clearInterval(heartbeatInterval);
  server.close(() => {
    console.log('[SERVER] 服务器已关闭');
    process.exit(0);
  });
});
