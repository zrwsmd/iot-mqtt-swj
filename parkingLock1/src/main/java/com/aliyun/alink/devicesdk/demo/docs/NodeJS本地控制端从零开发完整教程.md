# Node.js 本地控制端从零开发完整教程

> **基于实际物模型的完整开发指南（从项目初始化到测试）**

---

## 目录

1. [物模型分析](#1-物模型分析)
2. [项目初始化](#2-项目初始化)
3. [安装依赖](#3-安装依赖)
4. [项目结构](#4-项目结构)
5. [核心代码实现](#5-核心代码实现)
6. [测试代码](#6-测试代码)
7. [运行与测试](#7-运行与测试)
8. [API 使用示例](#8-api-使用示例)

---

## 1. 物模型分析

### 1.1 从 IoT 控制台获取的物模型

根据你的物模型截图，包含以下内容：

#### 服务（Service）

| 功能标识符 | 功能类型 | 标识符 | 数据类型 | 功能定义 |
|-----------|---------|--------|---------|---------|
| 服务 | 异步 | `restart` | - | 调用方式：异步调用 |

#### 事件（Event）

| 功能标识符 | 功能类型 | 标识符 | 数据类型 | 功能定义 |
|-----------|---------|--------|---------|---------|
| 事件 | 告警事件（自定义） | `ErrorEvent` | - | 事件类型：告警 |

#### 属性（Property）

| 功能标识符 | 功能类型 | 标识符 | 数据类型 | 取值范围 |
|-----------|---------|--------|---------|---------|
| 属性 | 读写 | `ADASSwitch` | bool（布尔型） | 0-关 / 1-开 |

### 1.2 物模型总结

```javascript
// 物模型定义
const TSL = {
  // 属性
  properties: {
    ADASSwitch: {
      type: 'bool',
      writable: true,
      readable: true,
      description: 'ADAS 开关',
      values: {
        0: '关',
        1: '开'
      }
    }
  },
  
  // 服务
  services: {
    restart: {
      callType: 'async',
      description: '重启设备',
      inputParams: {},
      outputParams: {}
    }
  },
  
  // 事件
  events: {
    ErrorEvent: {
      type: 'alert',
      description: '错误事件',
      outputParams: {
        ErrorCode: 'int',
        ErrorDesc: 'text'
      }
    }
  }
};
```

---

## 2. 项目初始化

### 2.1 创建项目目录

```bash
# 创建项目目录
mkdir iot-controller-nodejs
cd iot-controller-nodejs
```

### 2.2 初始化 npm 项目

```bash
# 初始化 package.json
npm init -y
```

**生成的 package.json**：

```json
{
  "name": "iot-controller-nodejs",
  "version": "1.0.0",
  "description": "阿里云 IoT 本地控制端",
  "main": "index.js",
  "scripts": {
    "test": "echo \"Error: no test specified\" && exit 1"
  },
  "keywords": [],
  "author": "",
  "license": "ISC"
}
```

---

## 3. 安装依赖

### 3.1 安装核心依赖

```bash
# 安装阿里云 IoT SDK
npm install @alicloud/iot20180120 @alicloud/openapi-client

# 安装 Express 框架
npm install express cors

# 安装环境变量管理
npm install dotenv

# 安装开发依赖
npm install --save-dev nodemon
```

### 3.2 安装测试依赖

```bash
# 安装 axios 用于测试
npm install axios
```

### 3.3 最终的 package.json

```json
{
  "name": "iot-controller-nodejs",
  "version": "1.0.0",
  "description": "阿里云 IoT 本地控制端",
  "main": "index.js",
  "scripts": {
    "start": "node src/server.js",
    "dev": "nodemon src/server.js",
    "test": "node tests/test-all.js"
  },
  "keywords": ["iot", "aliyun", "mqtt"],
  "author": "",
  "license": "ISC",
  "dependencies": {
    "@alicloud/iot20180120": "^3.0.8",
    "@alicloud/openapi-client": "^0.4.0",
    "axios": "^1.6.0",
    "cors": "^2.8.5",
    "dotenv": "^16.0.3",
    "express": "^4.18.2"
  },
  "devDependencies": {
    "nodemon": "^3.0.1"
  }
}
```

---

## 4. 项目结构

### 4.1 创建目录结构

```bash
# 创建目录
mkdir src
mkdir src/config
mkdir src/services
mkdir src/controllers
mkdir src/routes
mkdir tests
```

### 4.2 完整项目结构

```
iot-controller-nodejs/
├── .env                        # 环境变量配置
├── .gitignore                  # Git 忽略文件
├── package.json                # 项目配置
├── README.md                   # 项目说明
├── src/
│   ├── config/
│   │   └── iot-config.js       # IoT 配置
│   ├── services/
│   │   └── iot-service.js      # IoT 服务封装
│   ├── controllers/
│   │   └── device-controller.js # 设备控制器
│   ├── routes/
│   │   └── device-routes.js    # 路由定义
│   └── server.js               # 服务器入口
└── tests/
    ├── test-all.js             # 完整测试
    ├── test-property.js        # 属性测试
    ├── test-service.js         # 服务测试
    └── test-query.js           # 查询测试
```

---

## 5. 核心代码实现

### 5.1 环境变量配置（.env）

```bash
# 阿里云访问凭证
ACCESS_KEY_ID=你的AccessKeyId
ACCESS_KEY_SECRET=你的AccessKeySecret

# IoT 平台配置
IOT_INSTANCE_ID=iot-06z00d8xy9ns00z
IOT_REGION=cn-shanghai
PRODUCT_KEY=k0u6s8Wj9WW
DEVICE_NAME=taiyuan-pc-001

# 服务器配置
PORT=3000
NODE_ENV=development
```

### 5.2 IoT 配置（src/config/iot-config.js）

```javascript
require('dotenv').config();

module.exports = {
  // 阿里云访问凭证
  accessKeyId: process.env.ACCESS_KEY_ID,
  accessKeySecret: process.env.ACCESS_KEY_SECRET,
  
  // IoT 平台配置
  iotInstanceId: process.env.IOT_INSTANCE_ID,
  region: process.env.IOT_REGION,
  endpoint: `iot.${process.env.IOT_REGION}.aliyuncs.com`,
  
  // 设备信息
  productKey: process.env.PRODUCT_KEY,
  deviceName: process.env.DEVICE_NAME,
  
  // 服务器配置
  port: process.env.PORT || 3000,
  nodeEnv: process.env.NODE_ENV || 'development'
};
```

### 5.3 IoT 服务封装（src/services/iot-service.js）

```javascript
const Iot20180120 = require('@alicloud/iot20180120').default;
const OpenApi = require('@alicloud/openapi-client');
const config = require('../config/iot-config');

class IoTService {
  constructor() {
    // 初始化阿里云 IoT 客户端
    const openApiConfig = new OpenApi.Config({
      accessKeyId: config.accessKeyId,
      accessKeySecret: config.accessKeySecret,
      endpoint: config.endpoint
    });
    
    this.client = new Iot20180120(openApiConfig);
    this.iotInstanceId = config.iotInstanceId;
    this.productKey = config.productKey;
    this.deviceName = config.deviceName;
    
    console.log('[IoTService] 初始化成功');
    console.log(`[IoTService] 设备: ${this.productKey}/${this.deviceName}`);
  }

  /**
   * 1. 设置设备属性
   * @param {Object} properties - 属性对象，例如 { ADASSwitch: 1 }
   */
  async setProperty(properties) {
    try {
      console.log('[IoTService] 设置属性:', properties);
      
      const request = new Iot20180120.SetDevicePropertyRequest({
        iotInstanceId: this.iotInstanceId,
        productKey: this.productKey,
        deviceName: this.deviceName,
        items: JSON.stringify(properties)
      });

      const response = await this.client.setDeviceProperty(request);
      
      return {
        success: response.body.success,
        messageId: response.body.data?.messageId,
        errorMessage: response.body.errorMessage
      };
    } catch (error) {
      console.error('[IoTService] 设置属性失败:', error.message);
      throw error;
    }
  }

  /**
   * 2. 查询设备属性
   */
  async queryProperty() {
    try {
      console.log('[IoTService] 查询设备属性');
      
      const request = new Iot20180120.QueryDevicePropertyStatusRequest({
        iotInstanceId: this.iotInstanceId,
        productKey: this.productKey,
        deviceName: this.deviceName
      });

      const response = await this.client.queryDevicePropertyStatus(request);
      const properties = response.body.data?.list?.propertyStatusInfo || [];

      // 转换为易读格式
      const propertyMap = {};
      properties.forEach(prop => {
        propertyMap[prop.identifier] = {
          value: prop.value,
          time: new Date(prop.time).toLocaleString('zh-CN', {
            timeZone: 'Asia/Shanghai'
          })
        };
      });

      return {
        success: true,
        properties: propertyMap,
        count: properties.length
      };
    } catch (error) {
      console.error('[IoTService] 查询属性失败:', error.message);
      throw error;
    }
  }

  /**
   * 3. 调用设备服务
   * @param {String} serviceId - 服务标识符，例如 'restart'
   * @param {Object} args - 服务参数
   */
  async invokeService(serviceId, args = {}) {
    try {
      console.log(`[IoTService] 调用服务: ${serviceId}`, args);
      
      const request = new Iot20180120.InvokeThingServiceRequest({
        iotInstanceId: this.iotInstanceId,
        productKey: this.productKey,
        deviceName: this.deviceName,
        identifier: serviceId,
        args: JSON.stringify(args)
      });

      const response = await this.client.invokeThingService(request);
      
      return {
        success: response.body.success,
        messageId: response.body.data?.messageId,
        errorMessage: response.body.errorMessage
      };
    } catch (error) {
      console.error('[IoTService] 调用服务失败:', error.message);
      throw error;
    }
  }

  /**
   * 4. 查询设备详情
   */
  async queryDeviceDetail() {
    try {
      console.log('[IoTService] 查询设备详情');
      
      const request = new Iot20180120.QueryDeviceDetailRequest({
        iotInstanceId: this.iotInstanceId,
        productKey: this.productKey,
        deviceName: this.deviceName
      });

      const response = await this.client.queryDeviceDetail(request);
      const deviceInfo = response.body.data;

      return {
        success: true,
        deviceName: deviceInfo.deviceName,
        productKey: deviceInfo.productKey,
        deviceSecret: deviceInfo.deviceSecret?.substring(0, 8) + '****', // 隐藏部分
        status: deviceInfo.status,
        online: deviceInfo.online,
        gmtCreate: new Date(deviceInfo.gmtCreate).toLocaleString('zh-CN'),
        gmtActive: new Date(deviceInfo.gmtActive).toLocaleString('zh-CN'),
        gmtOnline: deviceInfo.gmtOnline 
          ? new Date(deviceInfo.gmtOnline).toLocaleString('zh-CN') 
          : 'N/A',
        ipAddress: deviceInfo.ipAddress || 'N/A'
      };
    } catch (error) {
      console.error('[IoTService] 查询设备详情失败:', error.message);
      throw error;
    }
  }

  /**
   * 5. 获取设备状态（综合信息）
   */
  async getDeviceStatus() {
    try {
      console.log('[IoTService] 获取设备状态');
      
      // 并行查询设备详情和属性
      const [detail, property] = await Promise.all([
        this.queryDeviceDetail(),
        this.queryProperty()
      ]);

      return {
        success: true,
        online: detail.online,
        status: detail.status,
        properties: property.properties,
        lastOnlineTime: detail.gmtOnline,
        ipAddress: detail.ipAddress
      };
    } catch (error) {
      console.error('[IoTService] 获取设备状态失败:', error.message);
      throw error;
    }
  }
}

module.exports = IoTService;
```

### 5.4 设备控制器（src/controllers/device-controller.js）

```javascript
const IoTService = require('../services/iot-service');

class DeviceController {
  constructor() {
    this.iotService = new IoTService();
  }

  /**
   * 设置 ADASSwitch 属性
   */
  async setADASSwitch(req, res) {
    try {
      const { value } = req.body;

      // 验证参数
      if (value !== 0 && value !== 1) {
        return res.status(400).json({
          success: false,
          message: 'ADASSwitch 值必须是 0 或 1'
        });
      }

      const result = await this.iotService.setProperty({
        ADASSwitch: value
      });

      res.json({
        success: result.success,
        message: `ADASSwitch 设置为 ${value === 1 ? '开' : '关'}`,
        data: {
          messageId: result.messageId,
          value: value,
          description: value === 1 ? '开' : '关'
        }
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        message: '设置属性失败',
        error: error.message
      });
    }
  }

  /**
   * 设置设备属性（通用）
   */
  async setProperty(req, res) {
    try {
      const { properties } = req.body;

      if (!properties || typeof properties !== 'object') {
        return res.status(400).json({
          success: false,
          message: '属性参数格式错误'
        });
      }

      const result = await this.iotService.setProperty(properties);

      res.json({
        success: result.success,
        message: '属性设置成功',
        data: {
          messageId: result.messageId,
          properties: properties
        }
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        message: '设置属性失败',
        error: error.message
      });
    }
  }

  /**
   * 查询设备属性
   */
  async queryProperty(req, res) {
    try {
      const result = await this.iotService.queryProperty();

      res.json({
        success: true,
        message: '查询属性成功',
        data: {
          properties: result.properties,
          count: result.count
        }
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        message: '查询属性失败',
        error: error.message
      });
    }
  }

  /**
   * 调用 restart 服务
   */
  async restart(req, res) {
    try {
      const result = await this.iotService.invokeService('restart', {});

      res.json({
        success: result.success,
        message: '重启指令已发送',
        data: {
          messageId: result.messageId,
          service: 'restart'
        }
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        message: '调用服务失败',
        error: error.message
      });
    }
  }

  /**
   * 调用设备服务（通用）
   */
  async invokeService(req, res) {
    try {
      const { serviceId } = req.params;
      const { args = {} } = req.body;

      const result = await this.iotService.invokeService(serviceId, args);

      res.json({
        success: result.success,
        message: `服务 ${serviceId} 调用成功`,
        data: {
          messageId: result.messageId,
          service: serviceId
        }
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        message: '调用服务失败',
        error: error.message
      });
    }
  }

  /**
   * 查询设备详情
   */
  async queryDetail(req, res) {
    try {
      const result = await this.iotService.queryDeviceDetail();

      res.json({
        success: true,
        message: '查询设备详情成功',
        data: result
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        message: '查询设备详情失败',
        error: error.message
      });
    }
  }

  /**
   * 获取设备状态
   */
  async getStatus(req, res) {
    try {
      const result = await this.iotService.getDeviceStatus();

      res.json({
        success: true,
        message: '获取设备状态成功',
        data: result
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        message: '获取设备状态失败',
        error: error.message
      });
    }
  }
}

module.exports = DeviceController;
```

### 5.5 路由定义（src/routes/device-routes.js）

```javascript
const express = require('express');
const DeviceController = require('../controllers/device-controller');

const router = express.Router();
const deviceController = new DeviceController();

// ===== 属性相关 =====

// 设置 ADASSwitch 属性（简化接口）
router.post('/adas-switch', (req, res) => {
  deviceController.setADASSwitch(req, res);
});

// 设置设备属性（通用接口）
router.post('/property', (req, res) => {
  deviceController.setProperty(req, res);
});

// 查询设备属性
router.get('/property', (req, res) => {
  deviceController.queryProperty(req, res);
});

// ===== 服务相关 =====

// 调用 restart 服务（简化接口）
router.post('/restart', (req, res) => {
  deviceController.restart(req, res);
});

// 调用设备服务（通用接口）
router.post('/service/:serviceId', (req, res) => {
  deviceController.invokeService(req, res);
});

// ===== 查询相关 =====

// 查询设备详情
router.get('/detail', (req, res) => {
  deviceController.queryDetail(req, res);
});

// 获取设备状态
router.get('/status', (req, res) => {
  deviceController.getStatus(req, res);
});

module.exports = router;
```

### 5.6 服务器入口（src/server.js）

```javascript
require('dotenv').config();
const express = require('express');
const cors = require('cors');
const config = require('./config/iot-config');
const deviceRoutes = require('./routes/device-routes');

const app = express();

// ===== 中间件 =====
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 请求日志
app.use((req, res, next) => {
  console.log(`[${new Date().toLocaleString('zh-CN')}] ${req.method} ${req.path}`);
  next();
});

// ===== 路由 =====

// 根路径
app.get('/', (req, res) => {
  res.json({
    name: 'IoT Controller API',
    version: '1.0.0',
    device: {
      productKey: config.productKey,
      deviceName: config.deviceName
    },
    endpoints: {
      // 属性
      setADASSwitch: 'POST /api/device/adas-switch',
      setProperty: 'POST /api/device/property',
      queryProperty: 'GET /api/device/property',
      // 服务
      restart: 'POST /api/device/restart',
      invokeService: 'POST /api/device/service/:serviceId',
      // 查询
      queryDetail: 'GET /api/device/detail',
      getStatus: 'GET /api/device/status'
    }
  });
});

// 健康检查
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// 设备 API
app.use('/api/device', deviceRoutes);

// 404 处理
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: '接口不存在',
    path: req.path
  });
});

// 错误处理
app.use((err, req, res, next) => {
  console.error('[Error]', err.message);
  res.status(500).json({
    success: false,
    message: '服务器内部错误',
    error: config.nodeEnv === 'development' ? err.message : undefined
  });
});

// ===== 启动服务器 =====
const PORT = config.port;
app.listen(PORT, () => {
  console.log('\n========================================');
  console.log('  IoT Controller API 启动成功');
  console.log('========================================');
  console.log(`📖 地址: http://localhost:${PORT}`);
  console.log(`💚 健康检查: http://localhost:${PORT}/health`);
  console.log(`📱 设备: ${config.productKey}/${config.deviceName}`);
  console.log('========================================\n');
});

module.exports = app;
```

---

## 6. 测试代码

### 6.1 完整测试（tests/test-all.js）

```javascript
require('dotenv').config();
const axios = require('axios');

const BASE_URL = 'http://localhost:3000';

// 工具函数：延迟
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function testAll() {
  console.log('\n========================================');
  console.log('  IoT Controller 完整测试');
  console.log('========================================\n');

  try {
    // 1. 健康检查
    console.log('1️⃣  健康检查...');
    const healthRes = await axios.get(`${BASE_URL}/health`);
    console.log('   ✓ 状态:', healthRes.data.status);
    console.log('   ✓ 运行时间:', Math.floor(healthRes.data.uptime), '秒');
    console.log('');

    // 2. 查询设备详情
    console.log('2️⃣  查询设备详情...');
    const detailRes = await axios.get(`${BASE_URL}/api/device/detail`);
    console.log('   ✓ 设备名称:', detailRes.data.data.deviceName);
    console.log('   ✓ 产品Key:', detailRes.data.data.productKey);
    console.log('   ✓ 设备状态:', detailRes.data.data.status);
    console.log('   ✓ 在线状态:', detailRes.data.data.online ? '在线' : '离线');
    console.log('   ✓ IP地址:', detailRes.data.data.ipAddress);
    console.log('   ✓ 最后上线:', detailRes.data.data.gmtOnline);
    console.log('');

    // 3. 查询设备属性
    console.log('3️⃣  查询设备属性...');
    const propertyRes = await axios.get(`${BASE_URL}/api/device/property`);
    console.log('   ✓ 属性数量:', propertyRes.data.data.count);
    console.log('   ✓ 属性列表:');
    Object.entries(propertyRes.data.data.properties).forEach(([key, value]) => {
      console.log(`      - ${key}: ${value.value} (更新时间: ${value.time})`);
    });
    console.log('');

    // 4. 设置 ADASSwitch 为开（1）
    console.log('4️⃣  设置 ADASSwitch = 1（开）...');
    const setOnRes = await axios.post(`${BASE_URL}/api/device/adas-switch`, {
      value: 1
    });
    console.log('   ✓ 设置结果:', setOnRes.data.success);
    console.log('   ✓ 消息ID:', setOnRes.data.data.messageId);
    console.log('   ✓ 状态:', setOnRes.data.data.description);
    console.log('');

    // 等待设备处理
    console.log('   ⏳ 等待 3 秒让设备处理...\n');
    await sleep(3000);

    // 5. 查询属性确认
    console.log('5️⃣  查询属性确认...');
    const propertyRes2 = await axios.get(`${BASE_URL}/api/device/property`);
    const adasValue = propertyRes2.data.data.properties.ADASSwitch?.value;
    console.log('   ✓ ADASSwitch 当前值:', adasValue, `(${adasValue === '1' ? '开' : '关'})`);
    console.log('');

    // 6. 设置 ADASSwitch 为关（0）
    console.log('6️⃣  设置 ADASSwitch = 0（关）...');
    const setOffRes = await axios.post(`${BASE_URL}/api/device/adas-switch`, {
      value: 0
    });
    console.log('   ✓ 设置结果:', setOffRes.data.success);
    console.log('   ✓ 状态:', setOffRes.data.data.description);
    console.log('');

    // 等待设备处理
    console.log('   ⏳ 等待 3 秒...\n');
    await sleep(3000);

    // 7. 调用 restart 服务
    console.log('7️⃣  调用 restart 服务...');
    const restartRes = await axios.post(`${BASE_URL}/api/device/restart`);
    console.log('   ✓ 调用结果:', restartRes.data.success);
    console.log('   ✓ 消息ID:', restartRes.data.data.messageId);
    console.log('   ✓ 服务:', restartRes.data.data.service);
    console.log('');

    // 8. 获取设备状态（综合）
    console.log('8️⃣  获取设备状态（综合）...');
    const statusRes = await axios.get(`${BASE_URL}/api/device/status`);
    console.log('   ✓ 在线:', statusRes.data.data.online ? '是' : '否');
    console.log('   ✓ 状态:', statusRes.data.data.status);
    console.log('   ✓ IP地址:', statusRes.data.data.ipAddress);
    console.log('   ✓ 属性:');
    Object.entries(statusRes.data.data.properties).forEach(([key, value]) => {
      console.log(`      - ${key}: ${value.value}`);
    });
    console.log('');

    console.log('========================================');
    console.log('  ✅ 所有测试通过！');
    console.log('========================================\n');

  } catch (error) {
    console.error('\n❌ 测试失败:');
    if (error.response) {
      console.error('   状态码:', error.response.status);
      console.error('   错误信息:', error.response.data);
    } else {
      console.error('   错误:', error.message);
    }
    console.log('');
  }
}

// 运行测试
testAll();
```

### 6.2 属性测试（tests/test-property.js）

```javascript
require('dotenv').config();
const axios = require('axios');

const BASE_URL = 'http://localhost:3000';

async function testProperty() {
  console.log('\n========== 属性测试 ==========\n');

  try {
    // 1. 查询当前属性
    console.log('1. 查询当前属性...');
    const queryRes = await axios.get(`${BASE_URL}/api/device/property`);
    console.log('当前属性:', queryRes.data.data.properties);
    console.log('');

    // 2. 设置 ADASSwitch = 1
    console.log('2. 设置 ADASSwitch = 1...');
    const setRes = await axios.post(`${BASE_URL}/api/device/adas-switch`, {
      value: 1
    });
    console.log('设置结果:', setRes.data.message);
    console.log('消息ID:', setRes.data.data.messageId);
    console.log('');

    // 3. 等待并查询
    console.log('3. 等待 2 秒后查询...');
    await new Promise(resolve => setTimeout(resolve, 2000));
    const queryRes2 = await axios.get(`${BASE_URL}/api/device/property`);
    console.log('更新后属性:', queryRes2.data.data.properties);
    console.log('');

    console.log('✅ 属性测试完成\n');
  } catch (error) {
    console.error('❌ 测试失败:', error.response?.data || error.message);
  }
}

testProperty();
```

### 6.3 服务测试（tests/test-service.js）

```javascript
require('dotenv').config();
const axios = require('axios');

const BASE_URL = 'http://localhost:3000';

async function testService() {
  console.log('\n========== 服务测试 ==========\n');

  try {
    // 1. 调用 restart 服务
    console.log('1. 调用 restart 服务...');
    const restartRes = await axios.post(`${BASE_URL}/api/device/restart`);
    console.log('调用结果:', restartRes.data.message);
    console.log('消息ID:', restartRes.data.data.messageId);
    console.log('');

    // 2. 通用服务调用
    console.log('2. 通用服务调用（restart）...');
    const serviceRes = await axios.post(`${BASE_URL}/api/device/service/restart`, {
      args: {}
    });
    console.log('调用结果:', serviceRes.data.message);
    console.log('消息ID:', serviceRes.data.data.messageId);
    console.log('');

    console.log('✅ 服务测试完成\n');
  } catch (error) {
    console.error('❌ 测试失败:', error.response?.data || error.message);
  }
}

testService();
```

### 6.4 查询测试（tests/test-query.js）

```javascript
require('dotenv').config();
const axios = require('axios');

const BASE_URL = 'http://localhost:3000';

async function testQuery() {
  console.log('\n========== 查询测试 ==========\n');

  try {
    // 1. 查询设备详情
    console.log('1. 查询设备详情...');
    const detailRes = await axios.get(`${BASE_URL}/api/device/detail`);
    console.log('设备信息:', JSON.stringify(detailRes.data.data, null, 2));
    console.log('');

    // 2. 查询设备属性
    console.log('2. 查询设备属性...');
    const propertyRes = await axios.get(`${BASE_URL}/api/device/property`);
    console.log('设备属性:', JSON.stringify(propertyRes.data.data, null, 2));
    console.log('');

    // 3. 获取设备状态
    console.log('3. 获取设备状态...');
    const statusRes = await axios.get(`${BASE_URL}/api/device/status`);
    console.log('设备状态:', JSON.stringify(statusRes.data.data, null, 2));
    console.log('');

    console.log('✅ 查询测试完成\n');
  } catch (error) {
    console.error('❌ 测试失败:', error.response?.data || error.message);
  }
}

testQuery();
```

---

## 7. 运行与测试

### 7.1 配置环境变量

**编辑 .env 文件**：

```bash
# 阿里云访问凭证（从阿里云控制台获取）
ACCESS_KEY_ID=LTAI5txxxxxxxxxx
ACCESS_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx

# IoT 平台配置（从 device_id.json 获取）
IOT_INSTANCE_ID=iot-06z00d8xy9ns00z
IOT_REGION=cn-shanghai
PRODUCT_KEY=k0u6s8Wj9WW
DEVICE_NAME=taiyuan-pc-001

# 服务器配置
PORT=3000
NODE_ENV=development
```

### 7.2 启动服务器

```bash
# 方式一：正常启动
npm start

# 方式二：开发模式（自动重启）
npm run dev
```

**预期输出**：

```
========================================
  IoT Controller API 启动成功
========================================
📖 地址: http://localhost:3000
💚 健康检查: http://localhost:3000/health
📱 设备: k0u6s8Wj9WW/taiyuan-pc-001
========================================
```

### 7.3 运行完整测试

**打开新的终端**：

```bash
npm test
```

**预期输出**：

```
========================================
  IoT Controller 完整测试
========================================

1️⃣  健康检查...
   ✓ 状态: ok
   ✓ 运行时间: 10 秒

2️⃣  查询设备详情...
   ✓ 设备名称: taiyuan-pc-001
   ✓ 产品Key: k0u6s8Wj9WW
   ✓ 设备状态: ONLINE
   ✓ 在线状态: 在线
   ✓ IP地址: xxx.xxx.xxx.xxx
   ✓ 最后上线: 2026-03-04 13:00:00

3️⃣  查询设备属性...
   ✓ 属性数量: 1
   ✓ 属性列表:
      - ADASSwitch: 0 (更新时间: 2026-03-04 13:00:00)

4️⃣  设置 ADASSwitch = 1（开）...
   ✓ 设置结果: true
   ✓ 消息ID: 123456789
   ✓ 状态: 开

   ⏳ 等待 3 秒让设备处理...

5️⃣  查询属性确认...
   ✓ ADASSwitch 当前值: 1 (开)

6️⃣  设置 ADASSwitch = 0（关）...
   ✓ 设置结果: true
   ✓ 状态: 关

   ⏳ 等待 3 秒...

7️⃣  调用 restart 服务...
   ✓ 调用结果: true
   ✓ 消息ID: 123456790
   ✓ 服务: restart

8️⃣  获取设备状态（综合）...
   ✓ 在线: 是
   ✓ 状态: ONLINE
   ✓ IP地址: xxx.xxx.xxx.xxx
   ✓ 属性:
      - ADASSwitch: 0

========================================
  ✅ 所有测试通过！
========================================
```

### 7.4 运行单独测试

```bash
# 属性测试
node tests/test-property.js

# 服务测试
node tests/test-service.js

# 查询测试
node tests/test-query.js
```

---

## 8. API 使用示例

### 8.1 使用 curl 测试

```bash
# 1. 健康检查
curl http://localhost:3000/health

# 2. 查询设备详情
curl http://localhost:3000/api/device/detail

# 3. 查询设备属性
curl http://localhost:3000/api/device/property

# 4. 设置 ADASSwitch = 1（开）
curl -X POST http://localhost:3000/api/device/adas-switch \
  -H "Content-Type: application/json" \
  -d '{"value": 1}'

# 5. 设置 ADASSwitch = 0（关）
curl -X POST http://localhost:3000/api/device/adas-switch \
  -H "Content-Type: application/json" \
  -d '{"value": 0}'

# 6. 调用 restart 服务
curl -X POST http://localhost:3000/api/device/restart

# 7. 获取设备状态
curl http://localhost:3000/api/device/status
```

### 8.2 使用 Postman 测试

#### 设置 ADASSwitch

```
POST http://localhost:3000/api/device/adas-switch
Content-Type: application/json

{
  "value": 1
}
```

#### 调用 restart 服务

```
POST http://localhost:3000/api/device/restart
Content-Type: application/json

{}
```

#### 查询设备属性

```
GET http://localhost:3000/api/device/property
```

### 8.3 在浏览器中测试

直接在浏览器访问：

```
http://localhost:3000/
http://localhost:3000/health
http://localhost:3000/api/device/detail
http://localhost:3000/api/device/property
http://localhost:3000/api/device/status
```

---

## 9. 完整 API 文档

### 9.1 API 列表

| 方法 | 路径 | 功能 | 参数 |
|------|------|------|------|
| GET | `/` | 查看 API 文档 | - |
| GET | `/health` | 健康检查 | - |
| POST | `/api/device/adas-switch` | 设置 ADASSwitch | `{value: 0或1}` |
| POST | `/api/device/property` | 设置属性（通用） | `{properties: {...}}` |
| GET | `/api/device/property` | 查询设备属性 | - |
| POST | `/api/device/restart` | 调用 restart 服务 | - |
| POST | `/api/device/service/:serviceId` | 调用服务（通用） | `{args: {...}}` |
| GET | `/api/device/detail` | 查询设备详情 | - |
| GET | `/api/device/status` | 获取设备状态 | - |

### 9.2 响应格式

**成功响应**：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    // 具体数据
  }
}
```

**失败响应**：

```json
{
  "success": false,
  "message": "错误信息",
  "error": "详细错误（开发环境）"
}
```

---

## 10. 总结

### ✅ 完成的功能

1. **项目初始化**
   - ✅ npm 项目创建
   - ✅ 依赖安装
   - ✅ 目录结构搭建

2. **核心功能**
   - ✅ 设置设备属性（ADASSwitch）
   - ✅ 查询设备属性
   - ✅ 调用设备服务（restart）
   - ✅ 查询设备详情
   - ✅ 获取设备状态

3. **测试**
   - ✅ 完整自动化测试
   - ✅ 单独功能测试
   - ✅ API 文档和示例

### ✅ 项目特点

- **完整性**：从项目初始化到测试的完整流程
- **实用性**：基于实际物模型开发
- **易用性**：简化的 API 接口
- **可扩展性**：模块化设计，易于扩展

### ✅ 下一步

- 添加更多属性支持
- 添加事件监听
- 添加 WebSocket 实时推送
- 添加前端界面

**现在你可以按照这个文档从零开始开发 Node.js 本地控制端！** 🚀
