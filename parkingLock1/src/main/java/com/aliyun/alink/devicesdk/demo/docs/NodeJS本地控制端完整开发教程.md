# Node.js + Express 本地控制端完整开发教程

---

## 目录

1. [项目初始化](#1-项目初始化)
2. [安装依赖](#2-安装依赖)
3. [项目结构](#3-项目结构)
4. [核心代码实现](#4-核心代码实现)
5. [配置文件](#5-配置文件)
6. [运行和测试](#6-运行和测试)
7. [API 接口文档](#7-api-接口文档)
8. [部署上线](#8-部署上线)

---

## 1. 项目初始化

### 1.1 创建项目目录

```bash
# 创建项目目录
mkdir iot-controller-nodejs
cd iot-controller-nodejs

# 初始化 npm 项目
npm init -y
```

### 1.2 修改 package.json

执行 `npm init -y` 后，会生成 `package.json` 文件，修改如下：

```json
{
  "name": "iot-controller-nodejs",
  "version": "1.0.0",
  "description": "阿里云 IoT 平台本地控制端（Node.js + Express）",
  "main": "src/app.js",
  "scripts": {
    "start": "node src/app.js",
    "dev": "nodemon src/app.js",
    "test": "node src/test.js"
  },
  "keywords": ["iot", "aliyun", "mqtt", "express"],
  "author": "Your Name",
  "license": "MIT"
}
```

---

## 2. 安装依赖

### 2.1 安装核心依赖

```bash
# 安装 Express 框架
npm install express

# 安装阿里云 IoT SDK
npm install @alicloud/iot20180120 @alicloud/openapi-client

# 安装环境变量管理
npm install dotenv

# 安装 CORS（跨域支持）
npm install cors

# 安装日志工具
npm install winston
```

### 2.2 安装开发依赖

```bash
# 安装 nodemon（开发时自动重启）
npm install --save-dev nodemon
```

### 2.3 最终的 package.json

```json
{
  "name": "iot-controller-nodejs",
  "version": "1.0.0",
  "description": "阿里云 IoT 平台本地控制端（Node.js + Express）",
  "main": "src/app.js",
  "scripts": {
    "start": "node src/app.js",
    "dev": "nodemon src/app.js",
    "test": "node src/test.js"
  },
  "keywords": ["iot", "aliyun", "mqtt", "express"],
  "author": "Your Name",
  "license": "MIT",
  "dependencies": {
    "@alicloud/iot20180120": "^3.0.8",
    "@alicloud/openapi-client": "^0.4.0",
    "cors": "^2.8.5",
    "dotenv": "^16.0.3",
    "express": "^4.18.2",
    "winston": "^3.11.0"
  },
  "devDependencies": {
    "nodemon": "^3.0.2"
  }
}
```

---

## 3. 项目结构

### 3.1 创建目录结构

```bash
# 创建目录
mkdir -p src/controllers
mkdir -p src/services
mkdir -p src/routes
mkdir -p src/config
mkdir -p src/utils
mkdir -p logs
```

### 3.2 完整项目结构

```
iot-controller-nodejs/
├── src/
│   ├── config/
│   │   └── logger.js          # 日志配置
│   ├── controllers/
│   │   └── deviceController.js # 设备控制器
│   ├── services/
│   │   └── iotService.js      # IoT 服务层
│   ├── routes/
│   │   └── device.js          # 设备路由
│   ├── utils/
│   │   └── response.js        # 响应工具
│   ├── app.js                 # 应用入口
│   └── test.js                # 测试脚本
├── logs/                      # 日志目录
├── .env                       # 环境变量
├── .env.example               # 环境变量示例
├── .gitignore                 # Git 忽略文件
├── package.json               # 项目配置
└── README.md                  # 项目说明
```

---

## 4. 核心代码实现

### 4.1 环境变量配置（.env）

创建 `.env` 文件：

```bash
# 阿里云访问凭证
ACCESS_KEY_ID=你的AccessKeyId
ACCESS_KEY_SECRET=你的AccessKeySecret

# IoT 平台配置
IOT_INSTANCE_ID=iot-06z00bmkq776uhu
IOT_REGION=cn-shanghai
PRODUCT_KEY=k03k41dVanO
DEVICE_NAME=Beijing_PC_001

# 服务器配置
PORT=3000
NODE_ENV=development
```

创建 `.env.example` 文件（用于版本控制）：

```bash
# 阿里云访问凭证
ACCESS_KEY_ID=your_access_key_id
ACCESS_KEY_SECRET=your_access_key_secret

# IoT 平台配置
IOT_INSTANCE_ID=your_instance_id
IOT_REGION=cn-shanghai
PRODUCT_KEY=your_product_key
DEVICE_NAME=your_device_name

# 服务器配置
PORT=3000
NODE_ENV=development
```

---

### 4.2 日志配置（src/config/logger.js）

```javascript
const winston = require('winston');
const path = require('path');

// 定义日志格式
const logFormat = winston.format.combine(
    winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
    winston.format.errors({ stack: true }),
    winston.format.splat(),
    winston.format.json()
);

// 创建 logger 实例
const logger = winston.createLogger({
    level: process.env.NODE_ENV === 'production' ? 'info' : 'debug',
    format: logFormat,
    transports: [
        // 错误日志
        new winston.transports.File({
            filename: path.join('logs', 'error.log'),
            level: 'error',
            maxsize: 5242880, // 5MB
            maxFiles: 5
        }),
        // 所有日志
        new winston.transports.File({
            filename: path.join('logs', 'combined.log'),
            maxsize: 5242880,
            maxFiles: 5
        })
    ]
});

// 开发环境输出到控制台
if (process.env.NODE_ENV !== 'production') {
    logger.add(new winston.transports.Console({
        format: winston.format.combine(
            winston.format.colorize(),
            winston.format.simple()
        )
    }));
}

module.exports = logger;
```

---

### 4.3 IoT 服务层（src/services/iotService.js）

```javascript
const Iot20180120 = require('@alicloud/iot20180120').default;
const OpenApi = require('@alicloud/openapi-client');
const logger = require('../config/logger');

class IoTService {
    constructor() {
        // 初始化阿里云 IoT 客户端
        const config = new OpenApi.Config({
            accessKeyId: process.env.ACCESS_KEY_ID,
            accessKeySecret: process.env.ACCESS_KEY_SECRET,
            endpoint: `iot.${process.env.IOT_REGION}.aliyuncs.com`
        });

        this.client = new Iot20180120(config);
        this.instanceId = process.env.IOT_INSTANCE_ID;
        this.productKey = process.env.PRODUCT_KEY;
        this.deviceName = process.env.DEVICE_NAME;

        logger.info('IoT Service initialized', {
            region: process.env.IOT_REGION,
            productKey: this.productKey,
            deviceName: this.deviceName
        });
    }

    /**
     * 调用设备服务
     * @param {string} serviceId - 服务标识符
     * @param {string} args - 服务参数（JSON 字符串）
     * @returns {Promise<Object>}
     */
    async invokeService(serviceId, args = '{}') {
        try {
            const request = new Iot20180120.InvokeThingServiceRequest({
                iotInstanceId: this.instanceId,
                productKey: this.productKey,
                deviceName: this.deviceName,
                identifier: serviceId,
                args: args
            });

            logger.info('Invoking service', { serviceId, args });
            const response = await this.client.invokeThingService(request);

            logger.info('Service invoked successfully', {
                serviceId,
                success: response.body.success,
                messageId: response.body.data?.messageId
            });

            return {
                success: response.body.success,
                messageId: response.body.data?.messageId,
                data: response.body.data
            };
        } catch (error) {
            logger.error('Failed to invoke service', {
                serviceId,
                error: error.message,
                stack: error.stack
            });
            throw error;
        }
    }

    /**
     * 设置设备属性
     * @param {Object} properties - 属性对象
     * @returns {Promise<Object>}
     */
    async setProperty(properties) {
        try {
            const request = new Iot20180120.SetDevicePropertyRequest({
                iotInstanceId: this.instanceId,
                productKey: this.productKey,
                deviceName: this.deviceName,
                items: JSON.stringify(properties)
            });

            logger.info('Setting property', { properties });
            const response = await this.client.setDeviceProperty(request);

            logger.info('Property set successfully', {
                success: response.body.success,
                messageId: response.body.data?.messageId
            });

            return {
                success: response.body.success,
                messageId: response.body.data?.messageId,
                data: response.body.data
            };
        } catch (error) {
            logger.error('Failed to set property', {
                properties,
                error: error.message,
                stack: error.stack
            });
            throw error;
        }
    }

    /**
     * 查询设备属性
     * @returns {Promise<Object>}
     */
    async queryProperty() {
        try {
            const request = new Iot20180120.QueryDevicePropertyStatusRequest({
                iotInstanceId: this.instanceId,
                productKey: this.productKey,
                deviceName: this.deviceName
            });

            logger.info('Querying property');
            const response = await this.client.queryDevicePropertyStatus(request);

            const properties = response.body.data?.list?.propertyStatusInfo || [];
            const propertyMap = {};

            properties.forEach(prop => {
                propertyMap[prop.identifier] = {
                    value: prop.value,
                    time: prop.time
                };
            });

            logger.info('Property queried successfully', {
                propertyCount: properties.length
            });

            return {
                success: true,
                properties: propertyMap,
                raw: properties
            };
        } catch (error) {
            logger.error('Failed to query property', {
                error: error.message,
                stack: error.stack
            });
            throw error;
        }
    }

    /**
     * 查询设备详情
     * @returns {Promise<Object>}
     */
    async queryDeviceDetail() {
        try {
            const request = new Iot20180120.QueryDeviceDetailRequest({
                iotInstanceId: this.instanceId,
                productKey: this.productKey,
                deviceName: this.deviceName
            });

            logger.info('Querying device detail');
            const response = await this.client.queryDeviceDetail(request);

            const deviceInfo = response.body.data;

            logger.info('Device detail queried successfully', {
                status: deviceInfo.status,
                online: deviceInfo.online
            });

            return {
                success: true,
                deviceName: deviceInfo.deviceName,
                productKey: deviceInfo.productKey,
                status: deviceInfo.status,
                online: deviceInfo.online,
                gmtCreate: deviceInfo.gmtCreate,
                gmtActive: deviceInfo.gmtActive,
                gmtOnline: deviceInfo.gmtOnline,
                ipAddress: deviceInfo.ipAddress,
                nodeType: deviceInfo.nodeType
            };
        } catch (error) {
            logger.error('Failed to query device detail', {
                error: error.message,
                stack: error.stack
            });
            throw error;
        }
    }

    /**
     * 查询设备状态（简化版）
     * @returns {Promise<Object>}
     */
    async getDeviceStatus() {
        try {
            const detail = await this.queryDeviceDetail();
            const properties = await this.queryProperty();

            return {
                success: true,
                online: detail.online,
                status: detail.status,
                properties: properties.properties,
                lastOnlineTime: detail.gmtOnline
            };
        } catch (error) {
            logger.error('Failed to get device status', {
                error: error.message
            });
            throw error;
        }
    }
}

module.exports = IoTService;
```

---

### 4.4 响应工具（src/utils/response.js）

```javascript
/**
 * 成功响应
 * @param {Object} res - Express response 对象
 * @param {Object} data - 响应数据
 * @param {string} message - 响应消息
 */
function success(res, data = null, message = 'Success') {
    res.json({
        success: true,
        message: message,
        data: data,
        timestamp: new Date().toISOString()
    });
}

/**
 * 错误响应
 * @param {Object} res - Express response 对象
 * @param {string} message - 错误消息
 * @param {number} statusCode - HTTP 状态码
 * @param {Object} error - 错误详情
 */
function error(res, message = 'Error', statusCode = 500, error = null) {
    res.status(statusCode).json({
        success: false,
        message: message,
        error: error ? {
            message: error.message,
            code: error.code
        } : null,
        timestamp: new Date().toISOString()
    });
}

module.exports = {
    success,
    error
};
```

---

### 4.5 设备控制器（src/controllers/deviceController.js）

```javascript
const IoTService = require('../services/iotService');
const { success, error } = require('../utils/response');
const logger = require('../config/logger');

const iotService = new IoTService();

/**
 * 调用设备服务
 */
async function invokeService(req, res) {
    try {
        const { serviceId } = req.params;
        const { args } = req.body;

        if (!serviceId) {
            return error(res, '服务标识符不能为空', 400);
        }

        const result = await iotService.invokeService(
            serviceId,
            args ? JSON.stringify(args) : '{}'
        );

        success(res, result, `服务 ${serviceId} 调用成功`);
    } catch (err) {
        logger.error('Controller: invokeService failed', { error: err.message });
        error(res, '调用服务失败', 500, err);
    }
}

/**
 * 设置设备属性
 */
async function setProperty(req, res) {
    try {
        const { properties } = req.body;

        if (!properties || typeof properties !== 'object') {
            return error(res, '属性参数格式错误', 400);
        }

        const result = await iotService.setProperty(properties);

        success(res, result, '属性设置成功');
    } catch (err) {
        logger.error('Controller: setProperty failed', { error: err.message });
        error(res, '设置属性失败', 500, err);
    }
}

/**
 * 查询设备属性
 */
async function queryProperty(req, res) {
    try {
        const result = await iotService.queryProperty();

        success(res, result, '查询属性成功');
    } catch (err) {
        logger.error('Controller: queryProperty failed', { error: err.message });
        error(res, '查询属性失败', 500, err);
    }
}

/**
 * 查询设备详情
 */
async function queryDeviceDetail(req, res) {
    try {
        const result = await iotService.queryDeviceDetail();

        success(res, result, '查询设备详情成功');
    } catch (err) {
        logger.error('Controller: queryDeviceDetail failed', { error: err.message });
        error(res, '查询设备详情失败', 500, err);
    }
}

/**
 * 获取设备状态（综合信息）
 */
async function getDeviceStatus(req, res) {
    try {
        const result = await iotService.getDeviceStatus();

        success(res, result, '获取设备状态成功');
    } catch (err) {
        logger.error('Controller: getDeviceStatus failed', { error: err.message });
        error(res, '获取设备状态失败', 500, err);
    }
}

module.exports = {
    invokeService,
    setProperty,
    queryProperty,
    queryDeviceDetail,
    getDeviceStatus
};
```

---

### 4.6 设备路由（src/routes/device.js）

```javascript
const express = require('express');
const router = express.Router();
const deviceController = require('../controllers/deviceController');

// 调用设备服务
router.post('/service/:serviceId', deviceController.invokeService);

// 设置设备属性
router.post('/property', deviceController.setProperty);

// 查询设备属性
router.get('/property', deviceController.queryProperty);

// 查询设备详情
router.get('/detail', deviceController.queryDeviceDetail);

// 获取设备状态（综合信息）
router.get('/status', deviceController.getDeviceStatus);

module.exports = router;
```

---

### 4.7 应用入口（src/app.js）

```javascript
require('dotenv').config();
const express = require('express');
const cors = require('cors');
const logger = require('./config/logger');
const deviceRoutes = require('./routes/device');

const app = express();
const PORT = process.env.PORT || 3000;

// 中间件
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 请求日志
app.use((req, res, next) => {
    logger.info('Incoming request', {
        method: req.method,
        path: req.path,
        ip: req.ip
    });
    next();
});

// 路由
app.use('/api/device', deviceRoutes);

// 健康检查
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

// 根路径
app.get('/', (req, res) => {
    res.json({
        name: 'IoT Controller API',
        version: '1.0.0',
        endpoints: {
            health: '/health',
            device: {
                invokeService: 'POST /api/device/service/:serviceId',
                setProperty: 'POST /api/device/property',
                queryProperty: 'GET /api/device/property',
                queryDetail: 'GET /api/device/detail',
                getStatus: 'GET /api/device/status'
            }
        }
    });
});

// 404 处理
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: 'API endpoint not found',
        path: req.path
    });
});

// 错误处理
app.use((err, req, res, next) => {
    logger.error('Unhandled error', {
        error: err.message,
        stack: err.stack
    });

    res.status(500).json({
        success: false,
        message: 'Internal server error',
        error: process.env.NODE_ENV === 'development' ? err.message : undefined
    });
});

// 启动服务器
app.listen(PORT, () => {
    logger.info(`IoT Controller API running on port ${PORT}`);
    console.log(`\n🚀 Server is running on http://localhost:${PORT}`);
    console.log(`📖 API Documentation: http://localhost:${PORT}`);
    console.log(`💚 Health Check: http://localhost:${PORT}/health\n`);
});

module.exports = app;
```

---

### 4.8 测试脚本（src/test.js）

```javascript
require('dotenv').config();
const IoTService = require('./services/iotService');
const logger = require('./config/logger');

const iotService = new IoTService();

async function runTests() {
    console.log('\n=== IoT Service 测试 ===\n');

    try {
        // 1. 查询设备详情
        console.log('1. 查询设备详情...');
        const detail = await iotService.queryDeviceDetail();
        console.log('设备状态:', detail.status);
        console.log('在线状态:', detail.online);
        console.log('');

        // 2. 查询设备属性
        console.log('2. 查询设备属性...');
        const properties = await iotService.queryProperty();
        console.log('设备属性:', properties.properties);
        console.log('');

        // 3. 设置设备属性
        console.log('3. 设置设备属性...');
        const setResult = await iotService.setProperty({
            ADASSwitch: 1
        });
        console.log('设置结果:', setResult.success);
        console.log('');

        // 4. 调用设备服务
        console.log('4. 调用设备服务（restart）...');
        const serviceResult = await iotService.invokeService('restart', '{}');
        console.log('调用结果:', serviceResult.success);
        console.log('消息ID:', serviceResult.messageId);
        console.log('');

        // 5. 获取设备状态（综合）
        console.log('5. 获取设备状态（综合）...');
        const status = await iotService.getDeviceStatus();
        console.log('在线:', status.online);
        console.log('属性:', status.properties);
        console.log('');

        console.log('✅ 所有测试通过！');
    } catch (error) {
        console.error('❌ 测试失败:', error.message);
        logger.error('Test failed', { error: error.message, stack: error.stack });
    }
}

runTests();
```

---

## 5. 配置文件

### 5.1 .gitignore

```
# 依赖
node_modules/

# 环境变量
.env

# 日志
logs/
*.log

# 系统文件
.DS_Store
Thumbs.db

# IDE
.vscode/
.idea/
*.swp
*.swo

# 临时文件
tmp/
temp/
```

---

### 5.2 README.md

```markdown
# IoT Controller API (Node.js + Express)

阿里云 IoT 平台本地控制端，基于 Node.js + Express 开发。

## 功能特性

- ✅ 调用设备服务
- ✅ 设置设备属性
- ✅ 查询设备属性
- ✅ 查询设备详情
- ✅ 获取设备状态
- ✅ RESTful API
- ✅ 日志记录
- ✅ 错误处理

## 快速开始

### 1. 安装依赖

\`\`\`bash
npm install
\`\`\`

### 2. 配置环境变量

复制 `.env.example` 为 `.env`，填入你的配置：

\`\`\`bash
cp .env.example .env
\`\`\`

### 3. 运行服务

\`\`\`bash
# 生产模式
npm start

# 开发模式（自动重启）
npm run dev
\`\`\`

### 4. 测试

\`\`\`bash
npm test
\`\`\`

## API 文档

### 基础信息

- **Base URL**: `http://localhost:3000`
- **Content-Type**: `application/json`

### 接口列表

#### 1. 调用设备服务

\`\`\`
POST /api/device/service/:serviceId
\`\`\`

**请求示例**：
\`\`\`bash
curl -X POST http://localhost:3000/api/device/service/restart \
  -H "Content-Type: application/json" \
  -d '{"args": {}}'
\`\`\`

#### 2. 设置设备属性

\`\`\`
POST /api/device/property
\`\`\`

**请求示例**：
\`\`\`bash
curl -X POST http://localhost:3000/api/device/property \
  -H "Content-Type: application/json" \
  -d '{"properties": {"ADASSwitch": 1}}'
\`\`\`

#### 3. 查询设备属性

\`\`\`
GET /api/device/property
\`\`\`

#### 4. 查询设备详情

\`\`\`
GET /api/device/detail
\`\`\`

#### 5. 获取设备状态

\`\`\`
GET /api/device/status
\`\`\`

## 项目结构

\`\`\`
src/
├── config/          # 配置文件
├── controllers/     # 控制器
├── services/        # 服务层
├── routes/          # 路由
├── utils/           # 工具函数
└── app.js           # 入口文件
\`\`\`

## 许可证

MIT
```

---

## 6. 运行和测试

### 6.1 安装依赖

```bash
npm install
```

### 6.2 配置环境变量

编辑 `.env` 文件，填入你的阿里云凭证和设备信息。

### 6.3 运行测试

```bash
npm test
```

**预期输出**：

```
=== IoT Service 测试 ===

1. 查询设备详情...
设备状态: ONLINE
在线状态: true

2. 查询设备属性...
设备属性: { ADASSwitch: { value: '0', time: 1709452800000 } }

3. 设置设备属性...
设置结果: true

4. 调用设备服务（restart）...
调用结果: true
消息ID: 123456789

5. 获取设备状态（综合）...
在线: true
属性: { ADASSwitch: { value: '1', time: 1709452900000 } }

✅ 所有测试通过！
```

### 6.4 启动服务

```bash
# 开发模式（自动重启）
npm run dev

# 生产模式
npm start
```

**预期输出**：

```
🚀 Server is running on http://localhost:3000
📖 API Documentation: http://localhost:3000
💚 Health Check: http://localhost:3000/health
```

---

## 7. API 接口文档

### 7.1 调用设备服务

**接口**: `POST /api/device/service/:serviceId`

**请求示例**：

```bash
curl -X POST http://localhost:3000/api/device/service/restart \
  -H "Content-Type: application/json" \
  -d '{
    "args": {}
  }'
```

**响应示例**：

```json
{
  "success": true,
  "message": "服务 restart 调用成功",
  "data": {
    "success": true,
    "messageId": "123456789",
    "data": {}
  },
  "timestamp": "2026-03-03T07:30:00.000Z"
}
```

---

### 7.2 设置设备属性

**接口**: `POST /api/device/property`

**请求示例**：

```bash
curl -X POST http://localhost:3000/api/device/property \
  -H "Content-Type: application/json" \
  -d '{
    "properties": {
      "ADASSwitch": 1,
      "temperature": 30.5
    }
  }'
```

**响应示例**：

```json
{
  "success": true,
  "message": "属性设置成功",
  "data": {
    "success": true,
    "messageId": "123456790"
  },
  "timestamp": "2026-03-03T07:31:00.000Z"
}
```

---

### 7.3 查询设备属性

**接口**: `GET /api/device/property`

**请求示例**：

```bash
curl http://localhost:3000/api/device/property
```

**响应示例**：

```json
{
  "success": true,
  "message": "查询属性成功",
  "data": {
    "success": true,
    "properties": {
      "ADASSwitch": {
        "value": "1",
        "time": 1709452800000
      },
      "temperature": {
        "value": "30.5",
        "time": 1709452800000
      }
    }
  },
  "timestamp": "2026-03-03T07:32:00.000Z"
}
```

---

### 7.4 查询设备详情

**接口**: `GET /api/device/detail`

**请求示例**：

```bash
curl http://localhost:3000/api/device/detail
```

**响应示例**：

```json
{
  "success": true,
  "message": "查询设备详情成功",
  "data": {
    "success": true,
    "deviceName": "Beijing_PC_001",
    "productKey": "k03k41dVanO",
    "status": "ONLINE",
    "online": true,
    "gmtCreate": 1709452800000,
    "gmtActive": 1709452900000,
    "gmtOnline": 1709453000000,
    "ipAddress": "192.168.1.100",
    "nodeType": 0
  },
  "timestamp": "2026-03-03T07:33:00.000Z"
}
```

---

### 7.5 获取设备状态

**接口**: `GET /api/device/status`

**请求示例**：

```bash
curl http://localhost:3000/api/device/status
```

**响应示例**：

```json
{
  "success": true,
  "message": "获取设备状态成功",
  "data": {
    "success": true,
    "online": true,
    "status": "ONLINE",
    "properties": {
      "ADASSwitch": {
        "value": "1",
        "time": 1709452800000
      }
    },
    "lastOnlineTime": 1709453000000
  },
  "timestamp": "2026-03-03T07:34:00.000Z"
}
```

---

## 8. 部署上线

### 8.1 使用 PM2 部署（推荐）

```bash
# 安装 PM2
npm install -g pm2

# 启动应用
pm2 start src/app.js --name iot-controller

# 查看状态
pm2 status

# 查看日志
pm2 logs iot-controller

# 开机自启
pm2 startup
pm2 save

# 停止应用
pm2 stop iot-controller

# 重启应用
pm2 restart iot-controller
```

---

### 8.2 使用 Docker 部署

创建 `Dockerfile`：

```dockerfile
FROM node:18-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY . .

EXPOSE 3000

CMD ["node", "src/app.js"]
```

创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  iot-controller:
    build: .
    ports:
      - "3000:3000"
    env_file:
      - .env
    volumes:
      - ./logs:/app/logs
    restart: unless-stopped
```

运行：

```bash
# 构建并启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止
docker-compose down
```

---

### 8.3 Nginx 反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

---

## 总结

本教程从零开始，完整演示了如何使用 Node.js + Express 开发阿里云 IoT 平台本地控制端，包括：

- ✅ 项目初始化和依赖安装
- ✅ 完整的项目结构
- ✅ 核心代码实现（服务层、控制器、路由）
- ✅ 日志和错误处理
- ✅ RESTful API 设计
- ✅ 测试和部署

**优势**：
- 开发速度快
- 代码简洁
- 易于维护
- 部署简单

**适用场景**：
- 快速原型开发
- 轻量级应用
- Web 服务
- 个人/小团队项目
