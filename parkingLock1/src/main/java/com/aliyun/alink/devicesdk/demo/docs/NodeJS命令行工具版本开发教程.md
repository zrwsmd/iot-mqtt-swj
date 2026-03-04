# Node.js 命令行工具版本开发教程（无需 HTTP 服务）

---

## 目录

1. [项目初始化](#1-项目初始化)
2. [安装依赖](#2-安装依赖)
3. [项目结构](#3-项目结构)
4. [核心代码实现](#4-核心代码实现)
5. [配置文件](#5-配置文件)
6. [使用方式](#6-使用方式)
7. [进阶功能](#7-进阶功能)

---

## 1. 项目初始化

### 1.1 创建项目目录

```bash
# 创建项目目录
mkdir iot-cli
cd iot-cli

# 初始化 npm 项目
npm init -y
```

### 1.2 修改 package.json

```json
{
  "name": "iot-cli",
  "version": "1.0.0",
  "description": "阿里云 IoT 平台命令行控制工具",
  "main": "index.js",
  "bin": {
    "iot": "./cli.js"
  },
  "scripts": {
    "test": "node test.js"
  },
  "keywords": ["iot", "aliyun", "cli"],
  "author": "Your Name",
  "license": "MIT"
}
```

---

## 2. 安装依赖

### 2.1 安装核心依赖

```bash
# 安装阿里云 IoT SDK
npm install @alicloud/iot20180120 @alicloud/openapi-client

# 安装环境变量管理
npm install dotenv

# 安装命令行工具（可选，用于美化输出）
npm install chalk commander
```

### 2.2 最终的 package.json

```json
{
  "name": "iot-cli",
  "version": "1.0.0",
  "description": "阿里云 IoT 平台命令行控制工具",
  "main": "index.js",
  "bin": {
    "iot": "./cli.js"
  },
  "scripts": {
    "test": "node test.js"
  },
  "keywords": ["iot", "aliyun", "cli"],
  "author": "Your Name",
  "license": "MIT",
  "dependencies": {
    "@alicloud/iot20180120": "^3.0.8",
    "@alicloud/openapi-client": "^0.4.0",
    "chalk": "^4.1.2",
    "commander": "^11.1.0",
    "dotenv": "^16.0.3"
  }
}
```

---

## 3. 项目结构

```
iot-cli/
├── lib/
│   └── IoTController.js    # IoT 控制器
├── .env                    # 环境变量
├── .env.example            # 环境变量示例
├── cli.js                  # 命令行入口（可选）
├── index.js                # 简单脚本入口
├── test.js                 # 测试脚本
├── package.json
└── README.md
```

---

## 4. 核心代码实现

### 4.1 环境变量配置（.env）

```bash
# 阿里云访问凭证
ACCESS_KEY_ID=你的AccessKeyId
ACCESS_KEY_SECRET=你的AccessKeySecret

# IoT 平台配置
IOT_INSTANCE_ID=iot-06z00bmkq776uhu
IOT_REGION=cn-shanghai
PRODUCT_KEY=k03k41dVanO
DEVICE_NAME=Beijing_PC_001
```

### 4.2 IoT 控制器（lib/IoTController.js）

```javascript
const Iot20180120 = require('@alicloud/iot20180120').default;
const OpenApi = require('@alicloud/openapi-client');

class IoTController {
    constructor(config) {
        const apiConfig = new OpenApi.Config({
            accessKeyId: config.accessKeyId,
            accessKeySecret: config.accessKeySecret,
            endpoint: `iot.${config.region}.aliyuncs.com`
        });

        this.client = new Iot20180120(apiConfig);
        this.instanceId = config.instanceId;
        this.productKey = config.productKey;
        this.deviceName = config.deviceName;
    }

    /**
     * 调用设备服务
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

            const response = await this.client.invokeThingService(request);
            return {
                success: response.body.success,
                messageId: response.body.data?.messageId,
                data: response.body.data
            };
        } catch (error) {
            throw new Error(`调用服务失败: ${error.message}`);
        }
    }

    /**
     * 设置设备属性
     */
    async setProperty(properties) {
        try {
            const request = new Iot20180120.SetDevicePropertyRequest({
                iotInstanceId: this.instanceId,
                productKey: this.productKey,
                deviceName: this.deviceName,
                items: JSON.stringify(properties)
            });

            const response = await this.client.setDeviceProperty(request);
            return {
                success: response.body.success,
                messageId: response.body.data?.messageId
            };
        } catch (error) {
            throw new Error(`设置属性失败: ${error.message}`);
        }
    }

    /**
     * 查询设备属性
     */
    async queryProperty() {
        try {
            const request = new Iot20180120.QueryDevicePropertyStatusRequest({
                iotInstanceId: this.instanceId,
                productKey: this.productKey,
                deviceName: this.deviceName
            });

            const response = await this.client.queryDevicePropertyStatus(request);
            const properties = response.body.data?.list?.propertyStatusInfo || [];

            const propertyMap = {};
            properties.forEach(prop => {
                propertyMap[prop.identifier] = {
                    value: prop.value,
                    time: new Date(prop.time).toLocaleString('zh-CN')
                };
            });

            return propertyMap;
        } catch (error) {
            throw new Error(`查询属性失败: ${error.message}`);
        }
    }

    /**
     * 查询设备详情
     */
    async queryDeviceDetail() {
        try {
            const request = new Iot20180120.QueryDeviceDetailRequest({
                iotInstanceId: this.instanceId,
                productKey: this.productKey,
                deviceName: this.deviceName
            });

            const response = await this.client.queryDeviceDetail(request);
            const deviceInfo = response.body.data;

            return {
                deviceName: deviceInfo.deviceName,
                productKey: deviceInfo.productKey,
                status: deviceInfo.status,
                online: deviceInfo.online,
                gmtCreate: new Date(deviceInfo.gmtCreate).toLocaleString('zh-CN'),
                gmtActive: new Date(deviceInfo.gmtActive).toLocaleString('zh-CN'),
                gmtOnline: deviceInfo.gmtOnline ? new Date(deviceInfo.gmtOnline).toLocaleString('zh-CN') : 'N/A',
                ipAddress: deviceInfo.ipAddress || 'N/A'
            };
        } catch (error) {
            throw new Error(`查询设备详情失败: ${error.message}`);
        }
    }

    /**
     * 获取设备状态（综合信息）
     */
    async getDeviceStatus() {
        try {
            const detail = await this.queryDeviceDetail();
            const properties = await this.queryProperty();

            return {
                online: detail.online,
                status: detail.status,
                properties: properties,
                lastOnlineTime: detail.gmtOnline
            };
        } catch (error) {
            throw new Error(`获取设备状态失败: ${error.message}`);
        }
    }
}

module.exports = IoTController;
```

---

### 4.3 简单脚本版本（index.js）

**最简单的使用方式，直接运行脚本**

```javascript
require('dotenv').config();
const IoTController = require('./lib/IoTController');

// 初始化控制器
const controller = new IoTController({
    accessKeyId: process.env.ACCESS_KEY_ID,
    accessKeySecret: process.env.ACCESS_KEY_SECRET,
    instanceId: process.env.IOT_INSTANCE_ID,
    region: process.env.IOT_REGION,
    productKey: process.env.PRODUCT_KEY,
    deviceName: process.env.DEVICE_NAME
});

// 主函数
async function main() {
    try {
        // ===== 1. 查询设备详情 =====
        console.log('\n========== 查询设备详情 ==========');
        const detail = await controller.queryDeviceDetail();
        console.log('设备名称:', detail.deviceName);
        console.log('设备状态:', detail.status);
        console.log('在线状态:', detail.online ? '在线' : '离线');
        console.log('IP 地址:', detail.ipAddress);
        console.log('最后上线:', detail.gmtOnline);

        // ===== 2. 查询设备属性 =====
        console.log('\n========== 查询设备属性 ==========');
        const properties = await controller.queryProperty();
        console.log('设备属性:', JSON.stringify(properties, null, 2));

        // ===== 3. 设置设备属性 =====
        console.log('\n========== 设置设备属性 ==========');
        const setResult = await controller.setProperty({
            ADASSwitch: 1
        });
        console.log('设置成功:', setResult.success);
        console.log('消息ID:', setResult.messageId);

        // ===== 4. 调用设备服务 =====
        console.log('\n========== 调用设备服务 ==========');
        const serviceResult = await controller.invokeService('restart', '{}');
        console.log('调用成功:', serviceResult.success);
        console.log('消息ID:', serviceResult.messageId);

        // ===== 5. 获取设备状态（综合） =====
        console.log('\n========== 获取设备状态 ==========');
        const status = await controller.getDeviceStatus();
        console.log('在线:', status.online ? '是' : '否');
        console.log('状态:', status.status);
        console.log('属性:', JSON.stringify(status.properties, null, 2));

        console.log('\n✅ 所有操作完成！');
    } catch (error) {
        console.error('\n❌ 错误:', error.message);
        process.exit(1);
    }
}

// 运行
main();
```

**使用方式**：

```bash
# 直接运行
node index.js
```

---

### 4.4 命令行工具版本（cli.js）

**更灵活的命令行工具，支持不同的操作**

```javascript
#!/usr/bin/env node

require('dotenv').config();
const { program } = require('commander');
const chalk = require('chalk');
const IoTController = require('./lib/IoTController');

// 初始化控制器
const controller = new IoTController({
    accessKeyId: process.env.ACCESS_KEY_ID,
    accessKeySecret: process.env.ACCESS_KEY_SECRET,
    instanceId: process.env.IOT_INSTANCE_ID,
    region: process.env.IOT_REGION,
    productKey: process.env.PRODUCT_KEY,
    deviceName: process.env.DEVICE_NAME
});

// 配置命令行工具
program
    .name('iot')
    .description('阿里云 IoT 平台命令行控制工具')
    .version('1.0.0');

// 查询设备详情
program
    .command('detail')
    .description('查询设备详情')
    .action(async () => {
        try {
            console.log(chalk.blue('\n查询设备详情...'));
            const detail = await controller.queryDeviceDetail();
            
            console.log(chalk.green('\n✓ 查询成功'));
            console.log('设备名称:', chalk.yellow(detail.deviceName));
            console.log('设备状态:', detail.online ? chalk.green(detail.status) : chalk.red(detail.status));
            console.log('在线状态:', detail.online ? chalk.green('在线') : chalk.red('离线'));
            console.log('IP 地址:', chalk.cyan(detail.ipAddress));
            console.log('创建时间:', detail.gmtCreate);
            console.log('激活时间:', detail.gmtActive);
            console.log('最后上线:', detail.gmtOnline);
        } catch (error) {
            console.error(chalk.red('\n✗ 错误:'), error.message);
            process.exit(1);
        }
    });

// 查询设备属性
program
    .command('property')
    .description('查询设备属性')
    .action(async () => {
        try {
            console.log(chalk.blue('\n查询设备属性...'));
            const properties = await controller.queryProperty();
            
            console.log(chalk.green('\n✓ 查询成功'));
            console.log(JSON.stringify(properties, null, 2));
        } catch (error) {
            console.error(chalk.red('\n✗ 错误:'), error.message);
            process.exit(1);
        }
    });

// 设置设备属性
program
    .command('set <property> <value>')
    .description('设置设备属性')
    .action(async (property, value) => {
        try {
            console.log(chalk.blue(`\n设置属性 ${property} = ${value}...`));
            
            // 尝试解析值（数字、布尔值）
            let parsedValue = value;
            if (value === 'true') parsedValue = true;
            else if (value === 'false') parsedValue = false;
            else if (!isNaN(value)) parsedValue = Number(value);
            
            const result = await controller.setProperty({
                [property]: parsedValue
            });
            
            console.log(chalk.green('\n✓ 设置成功'));
            console.log('消息ID:', chalk.cyan(result.messageId));
        } catch (error) {
            console.error(chalk.red('\n✗ 错误:'), error.message);
            process.exit(1);
        }
    });

// 调用设备服务
program
    .command('service <serviceId> [args]')
    .description('调用设备服务')
    .action(async (serviceId, args = '{}') => {
        try {
            console.log(chalk.blue(`\n调用服务 ${serviceId}...`));
            const result = await controller.invokeService(serviceId, args);
            
            console.log(chalk.green('\n✓ 调用成功'));
            console.log('消息ID:', chalk.cyan(result.messageId));
        } catch (error) {
            console.error(chalk.red('\n✗ 错误:'), error.message);
            process.exit(1);
        }
    });

// 获取设备状态
program
    .command('status')
    .description('获取设备状态（综合信息）')
    .action(async () => {
        try {
            console.log(chalk.blue('\n获取设备状态...'));
            const status = await controller.getDeviceStatus();
            
            console.log(chalk.green('\n✓ 查询成功'));
            console.log('在线:', status.online ? chalk.green('是') : chalk.red('否'));
            console.log('状态:', status.online ? chalk.green(status.status) : chalk.red(status.status));
            console.log('最后上线:', chalk.cyan(status.lastOnlineTime));
            console.log('\n属性:');
            console.log(JSON.stringify(status.properties, null, 2));
        } catch (error) {
            console.error(chalk.red('\n✗ 错误:'), error.message);
            process.exit(1);
        }
    });

// 解析命令行参数
program.parse();
```

**使用方式**：

```bash
# 方式一：直接运行
node cli.js detail
node cli.js property
node cli.js set ADASSwitch 1
node cli.js service restart
node cli.js status

# 方式二：全局安装后使用
npm link
iot detail
iot property
iot set ADASSwitch 1
iot service restart
iot status
```

---

### 4.5 测试脚本（test.js）

```javascript
require('dotenv').config();
const IoTController = require('./lib/IoTController');

const controller = new IoTController({
    accessKeyId: process.env.ACCESS_KEY_ID,
    accessKeySecret: process.env.ACCESS_KEY_SECRET,
    instanceId: process.env.IOT_INSTANCE_ID,
    region: process.env.IOT_REGION,
    productKey: process.env.PRODUCT_KEY,
    deviceName: process.env.DEVICE_NAME
});

async function runTests() {
    console.log('\n=== IoT 控制器测试 ===\n');

    try {
        // 测试 1
        console.log('1. 查询设备详情...');
        const detail = await controller.queryDeviceDetail();
        console.log('   ✓ 设备状态:', detail.status);
        console.log('   ✓ 在线状态:', detail.online);

        // 测试 2
        console.log('\n2. 查询设备属性...');
        const properties = await controller.queryProperty();
        console.log('   ✓ 属性数量:', Object.keys(properties).length);

        // 测试 3
        console.log('\n3. 设置设备属性...');
        const setResult = await controller.setProperty({ ADASSwitch: 1 });
        console.log('   ✓ 设置成功:', setResult.success);

        // 测试 4
        console.log('\n4. 调用设备服务...');
        const serviceResult = await controller.invokeService('restart', '{}');
        console.log('   ✓ 调用成功:', serviceResult.success);

        // 测试 5
        console.log('\n5. 获取设备状态...');
        const status = await controller.getDeviceStatus();
        console.log('   ✓ 在线:', status.online);

        console.log('\n✅ 所有测试通过！\n');
    } catch (error) {
        console.error('\n❌ 测试失败:', error.message, '\n');
        process.exit(1);
    }
}

runTests();
```

---

## 5. 配置文件

### 5.1 .env.example

```bash
# 阿里云访问凭证
ACCESS_KEY_ID=your_access_key_id
ACCESS_KEY_SECRET=your_access_key_secret

# IoT 平台配置
IOT_INSTANCE_ID=your_instance_id
IOT_REGION=cn-shanghai
PRODUCT_KEY=your_product_key
DEVICE_NAME=your_device_name
```

### 5.2 .gitignore

```
node_modules/
.env
*.log
.DS_Store
```

### 5.3 README.md

```markdown
# IoT CLI - 阿里云 IoT 平台命令行工具

无需启动 HTTP 服务，直接通过命令行控制 IoT 设备。

## 快速开始

### 1. 安装依赖

\`\`\`bash
npm install
\`\`\`

### 2. 配置环境变量

复制 `.env.example` 为 `.env`，填入你的配置。

### 3. 使用方式

#### 方式一：简单脚本（推荐快速测试）

\`\`\`bash
# 直接运行，执行所有操作
node index.js
\`\`\`

#### 方式二：命令行工具（推荐日常使用）

\`\`\`bash
# 查询设备详情
node cli.js detail

# 查询设备属性
node cli.js property

# 设置设备属性
node cli.js set ADASSwitch 1

# 调用设备服务
node cli.js service restart

# 获取设备状态
node cli.js status
\`\`\`

#### 方式三：全局安装

\`\`\`bash
# 全局安装
npm link

# 使用
iot detail
iot property
iot set ADASSwitch 1
iot service restart
iot status
\`\`\`

## 功能特性

- ✅ 查询设备详情
- ✅ 查询设备属性
- ✅ 设置设备属性
- ✅ 调用设备服务
- ✅ 获取设备状态
- ✅ 无需启动 HTTP 服务
- ✅ 支持命令行参数
- ✅ 彩色输出

## 测试

\`\`\`bash
npm test
\`\`\`
```

---

## 6. 使用方式

### 6.1 方式一：简单脚本（最简单）

**适合场景**：快速测试、一次性执行多个操作

```bash
# 1. 配置 .env 文件
cp .env.example .env
# 编辑 .env，填入你的配置

# 2. 直接运行
node index.js
```

**输出示例**：

```
========== 查询设备详情 ==========
设备名称: Beijing_PC_001
设备状态: ONLINE
在线状态: 在线
IP 地址: 192.168.1.100
最后上线: 2026-03-04 08:30:00

========== 查询设备属性 ==========
设备属性: {
  "ADASSwitch": {
    "value": "0",
    "time": "2026-03-04 08:30:00"
  }
}

========== 设置设备属性 ==========
设置成功: true
消息ID: 123456789

========== 调用设备服务 ==========
调用成功: true
消息ID: 123456790

========== 获取设备状态 ==========
在线: 是
状态: ONLINE
属性: {
  "ADASSwitch": {
    "value": "1",
    "time": "2026-03-04 08:30:10"
  }
}

✅ 所有操作完成！
```

---

### 6.2 方式二：命令行工具（最灵活）

**适合场景**：日常使用、单个操作、脚本自动化

```bash
# 查询设备详情
node cli.js detail

# 查询设备属性
node cli.js property

# 设置属性（支持不同类型）
node cli.js set ADASSwitch 1          # 数字
node cli.js set temperature 30.5      # 小数
node cli.js set enabled true          # 布尔值

# 调用服务
node cli.js service restart           # 无参数服务
node cli.js service setConfig '{"key":"value"}'  # 有参数服务

# 获取设备状态
node cli.js status
```

---

### 6.3 方式三：全局安装（最方便）

```bash
# 1. 全局安装
npm link

# 2. 直接使用 iot 命令
iot detail
iot property
iot set ADASSwitch 1
iot service restart
iot status

# 3. 查看帮助
iot --help
```

---

### 6.4 方式四：集成到脚本

**在其他脚本中调用**

```javascript
// my-script.js
require('dotenv').config();
const IoTController = require('./lib/IoTController');

const controller = new IoTController({
    accessKeyId: process.env.ACCESS_KEY_ID,
    accessKeySecret: process.env.ACCESS_KEY_SECRET,
    instanceId: process.env.IOT_INSTANCE_ID,
    region: process.env.IOT_REGION,
    productKey: process.env.PRODUCT_KEY,
    deviceName: process.env.DEVICE_NAME
});

async function myCustomLogic() {
    // 你的自定义逻辑
    const status = await controller.getDeviceStatus();
    
    if (!status.online) {
        console.log('设备离线，发送告警...');
        // 发送告警邮件、短信等
    }
    
    // 根据属性值执行不同操作
    if (status.properties.ADASSwitch?.value === '1') {
        console.log('ADAS 已开启');
    }
}

myCustomLogic();
```

---

## 7. 进阶功能

### 7.1 定时任务

**使用 cron 定时查询设备状态**

```javascript
// cron-task.js
require('dotenv').config();
const IoTController = require('./lib/IoTController');

const controller = new IoTController({
    accessKeyId: process.env.ACCESS_KEY_ID,
    accessKeySecret: process.env.ACCESS_KEY_SECRET,
    instanceId: process.env.IOT_INSTANCE_ID,
    region: process.env.IOT_REGION,
    productKey: process.env.PRODUCT_KEY,
    deviceName: process.env.DEVICE_NAME
});

async function checkDeviceStatus() {
    try {
        const status = await controller.getDeviceStatus();
        console.log(`[${new Date().toLocaleString()}] 设备状态:`, status.online ? '在线' : '离线');
        
        // 如果离线，发送告警
        if (!status.online) {
            console.log('⚠️  设备离线，发送告警！');
            // TODO: 发送邮件、短信、钉钉通知等
        }
    } catch (error) {
        console.error('检查失败:', error.message);
    }
}

// 每 5 分钟检查一次
setInterval(checkDeviceStatus, 5 * 60 * 1000);

// 立即执行一次
checkDeviceStatus();
```

**使用 Linux crontab**：

```bash
# 编辑 crontab
crontab -e

# 添加定时任务（每 5 分钟执行一次）
*/5 * * * * cd /path/to/iot-cli && node cron-task.js >> /var/log/iot-check.log 2>&1
```

---

### 7.2 批量操作

**批量控制多个设备**

```javascript
// batch-control.js
require('dotenv').config();
const IoTController = require('./lib/IoTController');

// 设备列表
const devices = [
    { productKey: 'k03k41dVanO', deviceName: 'Device_001' },
    { productKey: 'k03k41dVanO', deviceName: 'Device_002' },
    { productKey: 'k03k41dVanO', deviceName: 'Device_003' }
];

async function batchControl() {
    for (const device of devices) {
        const controller = new IoTController({
            accessKeyId: process.env.ACCESS_KEY_ID,
            accessKeySecret: process.env.ACCESS_KEY_SECRET,
            instanceId: process.env.IOT_INSTANCE_ID,
            region: process.env.IOT_REGION,
            productKey: device.productKey,
            deviceName: device.deviceName
        });

        try {
            console.log(`\n控制设备: ${device.deviceName}`);
            
            // 设置属性
            await controller.setProperty({ ADASSwitch: 1 });
            console.log('  ✓ 属性设置成功');
            
            // 调用服务
            await controller.invokeService('restart', '{}');
            console.log('  ✓ 服务调用成功');
        } catch (error) {
            console.error(`  ✗ 失败: ${error.message}`);
        }
    }
}

batchControl();
```

---

### 7.3 交互式命令行

**使用 inquirer 实现交互式选择**

```bash
npm install inquirer
```

```javascript
// interactive.js
require('dotenv').config();
const inquirer = require('inquirer');
const IoTController = require('./lib/IoTController');

const controller = new IoTController({
    accessKeyId: process.env.ACCESS_KEY_ID,
    accessKeySecret: process.env.ACCESS_KEY_SECRET,
    instanceId: process.env.IOT_INSTANCE_ID,
    region: process.env.IOT_REGION,
    productKey: process.env.PRODUCT_KEY,
    deviceName: process.env.DEVICE_NAME
});

async function main() {
    const { action } = await inquirer.prompt([
        {
            type: 'list',
            name: 'action',
            message: '请选择操作:',
            choices: [
                '查询设备详情',
                '查询设备属性',
                '设置设备属性',
                '调用设备服务',
                '获取设备状态',
                '退出'
            ]
        }
    ]);

    switch (action) {
        case '查询设备详情':
            const detail = await controller.queryDeviceDetail();
            console.log(detail);
            break;
        case '查询设备属性':
            const properties = await controller.queryProperty();
            console.log(properties);
            break;
        case '设置设备属性':
            const { property, value } = await inquirer.prompt([
                { type: 'input', name: 'property', message: '属性名:' },
                { type: 'input', name: 'value', message: '属性值:' }
            ]);
            await controller.setProperty({ [property]: value });
            console.log('✓ 设置成功');
            break;
        case '调用设备服务':
            const { serviceId } = await inquirer.prompt([
                { type: 'input', name: 'serviceId', message: '服务标识符:' }
            ]);
            await controller.invokeService(serviceId, '{}');
            console.log('✓ 调用成功');
            break;
        case '获取设备状态':
            const status = await controller.getDeviceStatus();
            console.log(status);
            break;
        case '退出':
            process.exit(0);
    }

    // 继续
    main();
}

main();
```

---

## 总结

本教程提供了**无需启动 HTTP 服务**的 Node.js IoT 控制方案，包括：

### ✅ 三种使用方式

1. **简单脚本**（`index.js`）
   - 最简单，直接运行
   - 适合快速测试
   - 一次执行多个操作

2. **命令行工具**（`cli.js`）
   - 最灵活，支持参数
   - 适合日常使用
   - 可集成到脚本

3. **全局安装**
   - 最方便，全局命令
   - 适合频繁使用

### ✅ 进阶功能

- 定时任务（cron）
- 批量操作
- 交互式命令行
- 集成到其他脚本

### ✅ 优势

- 无需启动服务
- 内存占用小
- 启动速度快
- 使用简单
- 易于自动化

**推荐使用场景**：
- ✅ 临时测试设备
- ✅ 脚本自动化
- ✅ 定时任务
- ✅ 批量操作
- ✅ 快速调试
