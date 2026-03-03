# 阿里云 IoT 平台上位机远程控制完整技术文档

---

## 目录

1. [系统架构概述](#1-系统架构概述)
2. [核心概念详解](#2-核心概念详解)
3. [通信协议与数据流向](#3-通信协议与数据流向)
4. [项目代码结构分析](#4-项目代码结构分析)
5. [部署方案详解](#5-部署方案详解)
6. [完整开发流程](#6-完整开发流程)
7. [常见问题与解决方案](#7-常见问题与解决方案)

---

## 1. 系统架构概述

### 1.1 整体架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                          完整系统架构                                  │
└──────────────────────────────────────────────────────────────────────┘

┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│  本地控制端      │         │  阿里云 IoT 平台  │         │  上位机(外地)    │
│  (你的电脑)      │         │                  │         │                 │
│                 │         │                  │         │                 │
│ ┌─────────────┐ │         │ ┌──────────────┐ │         │ ┌─────────────┐ │
│ │LocalControl │ │  HTTPS  │ │ MQTT Broker  │ │  MQTT   │ │HelloWorld   │ │
│ │.java        │─┼────────►│ │              │◄┼─────────┤ │.java        │ │
│ │             │ │  API    │ │              │ │  长连接  │ │             │ │
│ └─────────────┘ │         │ └──────────────┘ │         │ └─────────────┘ │
│                 │         │                  │         │        │        │
│ - 调用云端API    │         │ ┌──────────────┐ │         │        ▼        │
│ - 下发控制指令   │         │ │ 物模型管理    │ │         │ ┌─────────────┐ │
│ - 查询设备状态   │         │ │ 数据存储     │ │         │ │ThingSample  │ │
│ - 查看历史数据   │         │ │ 规则引擎     │ │         │ │.java        │ │
│                 │         │ │ 设备影子     │ │         │ └─────────────┘ │
│                 │         │ └──────────────┘ │         │        │        │
│                 │         │                  │         │        ▼        │
│                 │         │                  │         │ ┌─────────────┐ │
│                 │         │                  │         │ │物理硬件设备  │ │
│                 │         │                  │         │ │串口/GPIO    │ │
│                 │         │                  │         │ └─────────────┘ │
└─────────────────┘         └──────────────────┘         └─────────────────┘

      不需要IP                    固定域名                    不需要公网IP
      调用API                 iot.cn-shanghai              主动连接云端
                            .aliyuncs.com
```

### 1.2 三方角色定位

| 角色 | 位置 | 作用 | 技术栈 |
|------|------|------|--------|
| **本地控制端** | 你的电脑 | 远程控制上位机、查询状态 | Java + 阿里云服务端 SDK |
| **IoT 平台** | 阿里云 | 消息中转、数据存储、设备管理 | MQTT Broker + 云服务 |
| **上位机** | 客户现场 | 接入云端、控制硬件、上报数据 | Java + LinkKit 设备端 SDK |

---

## 2. 核心概念详解

### 2.1 物模型（Thing Model）

**物模型是设备在云端的数字化表示**，定义了设备的功能。

#### 物模型三要素

```
┌────────────────────────────────────────────────────────┐
│                      物模型结构                         │
└────────────────────────────────────────────────────────┘

1. 属性 (Property)
   - 描述设备的状态
   - 可读可写
   - 例如：温度、开关状态、运行模式
   
   示例：
   {
     "identifier": "ADASSwitch",
     "dataType": "bool",
     "accessMode": "rw"
   }

2. 事件 (Event)
   - 设备主动上报的信息
   - 只写（设备→云）
   - 例如：故障告警、异常事件
   
   示例：
   {
     "identifier": "ErrorEvent",
     "outputData": [
       {"identifier": "ErrorCode", "dataType": "int"},
       {"identifier": "ErrorDesc", "dataType": "text"}
     ]
   }

3. 服务 (Service)
   - 云端可调用的设备功能
   - 可带输入输出参数
   - 例如：重启、升级、配置更新
   
   示例：
   {
     "identifier": "restart",
     "callType": "async",
     "inputData": [],
     "outputData": []
   }
```

#### 物模型在代码中的体现

```java
// 上报属性（设备→云）
Map<String, ValueWrapper> reportData = new HashMap<>();
reportData.put("ADASSwitch", new ValueWrapper.BooleanValueWrapper(1));
LinkKit.getInstance().getDeviceThing().thingPropertyPost(reportData, ...);

// 上报事件（设备→云）
HashMap<String, ValueWrapper> eventData = new HashMap<>();
eventData.put("ErrorCode", new ValueWrapper.IntValueWrapper(1));
eventData.put("ErrorDesc", new ValueWrapper.StringValueWrapper("hello"));
OutputParams params = new OutputParams(eventData);
LinkKit.getInstance().getDeviceThing().thingEventPost("ErrorEvent", params, ...);

// 处理服务调用（云→设备）
public void onProcess(String identify, Object result, ITResResponseCallback callback) {
    if ("restart".equals(identify)) {
        // 执行重启逻辑
        executeRestart();
        // 回复云端
        callback.onComplete(identify, null, null);
    }
}
```

### 2.2 设备三元组

**设备三元组是设备的唯一身份凭证**，用于 MQTT 连接认证。

```
┌─────────────────────────────────────────────────┐
│              设备三元组                          │
└─────────────────────────────────────────────────┘

productKey    (PK)  - 产品标识（一类设备）
deviceName    (DN)  - 设备名称（唯一标识）
deviceSecret  (DS)  - 设备密钥（认证凭证）

示例：
{
  "productKey": "k03k41dVanO",
  "deviceName": "Beijing_PC_001",
  "deviceSecret": "d7ee3a8a2c8718b0ad6766a0f2c42f54"
}

获取方式：
1. 在 IoT 控制台创建产品 → 获得 productKey
2. 在产品下添加设备 → 获得 deviceName + deviceSecret
3. 填入 device_id.json 配置文件
```

### 2.3 MQTT 协议

**MQTT 是设备与云端通信的底层协议**。

#### MQTT 核心概念

```
┌────────────────────────────────────────────────────┐
│                 MQTT 协议要素                       │
└────────────────────────────────────────────────────┘

1. Broker（代理服务器）
   - IoT 平台的 MQTT 服务器
   - 地址：iot-xxx.mqtt.iothub.aliyuncs.com:8883
   - 负责消息路由和分发

2. Client（客户端）
   - 上位机设备
   - 通过三元组认证连接 Broker

3. Topic（主题）
   - 消息的路由地址
   - 格式：/sys/{productKey}/{deviceName}/thing/...
   - 支持通配符订阅

4. QoS（服务质量）
   - QoS 0：最多一次（不保证送达）
   - QoS 1：至少一次（保证送达，可能重复）

5. Publish（发布）
   - 设备向 Topic 发送消息

6. Subscribe（订阅）
   - 设备监听 Topic 接收消息
```

#### MQTT 连接流程

```
上位机                                    IoT 平台 MQTT Broker
  │                                              │
  │ 1. TCP/SSL 握手                               │
  ├─────────────────────────────────────────────►│
  │                                              │
  │ 2. MQTT CONNECT                              │
  │    ClientId: {productKey}.{deviceName}       │
  │    Username: {deviceName}&{productKey}       │
  │    Password: sign({deviceSecret})            │
  ├─────────────────────────────────────────────►│
  │                                              │
  │ 3. MQTT CONNACK (连接成功)                    │
  │◄─────────────────────────────────────────────┤
  │                                              │
  │ 4. MQTT SUBSCRIBE (订阅系统 Topic)             │
  │    /sys/{pk}/{dn}/thing/service/property/set │
  │    /sys/{pk}/{dn}/thing/service/+            │
  ├─────────────────────────────────────────────►│
  │                                              │
  │ 5. MQTT SUBACK (订阅成功)                     │
  │◄─────────────────────────────────────────────┤
  │                                              │
  │ 6. 保持连接（心跳 PINGREQ/PINGRESP）            │
  │◄────────────────────────────────────────────►│
  │         每 30-60 秒一次                        │
```

---

## 3. 通信协议与数据流向

### 3.1 完整通信链路

#### 场景一：本地控制端 → 上位机（下发控制指令）

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│ 本地控制端    │         │ IoT 平台      │         │ 上位机        │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │
       │ 1. 调用云端 API         │                        │
       │ InvokeThingService()   │                        │
       ├───────────────────────►│                        │
       │ HTTPS POST             │                        │
       │ iot.cn-shanghai        │                        │
       │ .aliyuncs.com          │                        │
       │                        │                        │
       │                        │ 2. MQTT PUBLISH        │
       │                        │ Topic: /sys/{pk}/{dn}/ │
       │                        │   thing/service/restart│
       │                        │ Payload: {"method":... }│
       │                        ├───────────────────────►│
       │                        │                        │
       │                        │                        │ 3. 触发回调
       │                        │                        │ mCommonHandler
       │                        │                        │ .onProcess()
       │                        │                        │
       │                        │                        │ 4. 执行操作
       │                        │                        │ executeRestart()
       │                        │                        │
       │                        │ 5. MQTT PUBLISH (回复) │
       │                        │ Topic: .../restart_reply│
       │                        │◄───────────────────────┤
       │                        │ Payload: {"code":200}  │
       │                        │                        │
       │ 6. API 响应返回         │                        │
       │◄───────────────────────┤                        │
       │ {"success": true}      │                        │
```

#### 场景二：上位机 → 云端（上报设备状态）

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│ 本地控制端    │         │ IoT 平台      │         │ 上位机        │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │
       │                        │                        │ 1. 调用上报方法
       │                        │                        │ reportDemoProperty()
       │                        │                        │
       │                        │ 2. MQTT PUBLISH        │
       │                        │ Topic: /sys/{pk}/{dn}/ │
       │                        │   thing/event/property/│
       │                        │   post                 │
       │                        │◄───────────────────────┤
       │                        │ Payload: {"params":... }│
       │                        │                        │
       │                        │ 3. 存储到云端数据库     │
       │                        │ 更新物模型缓存         │
       │                        │                        │
       │ 4. 查询设备状态         │                        │
       │ QueryDeviceProperty    │                        │
       │ Status()               │                        │
       ├───────────────────────►│                        │
       │                        │                        │
       │ 5. 返回最新状态         │                        │
       │◄───────────────────────┤                        │
       │ {"ADASSwitch": 1}      │                        │
```

### 3.2 数据流向分类

| 流向类型 | 说明 | 协议 | 示例方法 |
|---------|------|------|---------|
| **设备→云** | 上位机上报数据到云端 | MQTT PUBLISH | `reportDemoProperty()`, `reportDemoEvent()` |
| **云→设备** | 云端下发指令到上位机 | MQTT PUBLISH | `mCommonHandler.onProcess()` 接收 |
| **设备→云→设备** | 请求-响应模式 | MQTT PUBLISH + 回复 | `shadowGet()`, `cOTAGet()` |
| **本地→云** | 本地控制端调用云端 API | HTTPS | `invokeThingService()`, `queryDevicePropertyStatus()` |
| **云→本地** | API 响应返回 | HTTPS | API Response |
| **网关→云** | 网关代理子设备通信 | MQTT PUBLISH | `subDevOnline()`, `subDevPublish()` |
| **云→网关→子设备** | 云端控制子设备 | MQTT PUBLISH 转发 | `SubThingSample.mCommonHandler` |

---

## 4. 项目代码结构分析

### 4.1 类功能总览

```
parkingLock1/
├── HelloWorld.java           # 主入口，设备连云
├── ThingSample.java          # 物模型核心（属性/事件/服务）
├── MqttSample.java           # MQTT 基础通信
├── COTASample.java           # 远程配置
├── DeviceShadowSample.java   # 设备影子
├── LabelSample.java          # 设备标签
├── GatewaySample.java        # 网关子设备管理
├── SubThingSample.java       # 子设备物模型
└── BaseSample.java           # 工具基类
```

### 4.2 核心类详解

#### 4.2.1 HelloWorld.java（主入口）

**职责**：程序启动、设备连云、任务调度

**核心方法**：

| 方法 | 功能 | 数据流向 |
|------|------|---------|
| `main()` | 程序入口，读取配置文件，判断走动态注册还是直接连云 | 本地操作 |
| `init()` | 建立设备与云端的 MQTT 长连接，初始化物模型 | 设备→云（建立连接） |
| `executeScheduler()` | 连接成功后执行测试任务调度 | 本地操作 |
| `deviceRegister()` | 一型一密动态注册，获取 deviceSecret | 设备→云→设备（请求密钥并接收） |
| `deviceRegisterDeprecated()` | 旧版动态注册（仅上海region） | 设备→云→设备 |
| `deinit()` | 断开与云端连接 | 设备→云（断开） |
| `testDeviceModel()` | 测试物模型功能 | 本地操作（调用 ThingSample） |
| `testMqtt()` | 测试 MQTT 基础通信 | 本地操作（调用 MqttSample） |
| `testCota()` | 测试远程配置 | 本地操作（调用 COTASample） |
| `testLabel()` | 测试设备标签 | 本地操作（调用 LabelSample） |
| `testGateway()` | 测试网关子设备 | 本地操作（调用 GatewaySample） |
| `testDeviceShadow()` | 测试设备影子 | 本地操作（调用 DeviceShadowSample） |

**init() 方法详解**：

```java
/**
 * 初始化并连接云端
 * 流向：设备→云（建立 MQTT 连接）
 * 
 * 底层执行：
 * 1. 配置 MQTT 参数（Broker 地址、三元组）
 * 2. 建立 TCP/SSL 连接
 * 3. 发送 MQTT CONNECT 报文
 * 4. 自动订阅系统 Topic
 * 5. 保持心跳
 */
public void init(final DeviceInfoData deviceInfoData) {
    LinkKitInitParams params = new LinkKitInitParams();
    
    // MQTT 配置
    IoTMqttClientConfig config = new IoTMqttClientConfig();
    config.productKey = deviceInfoData.productKey;
    config.deviceName = deviceInfoData.deviceName;
    config.deviceSecret = deviceInfoData.deviceSecret;
    config.channelHost = "ssl://" + deviceInfoData.instanceId 
                       + ".mqtt.iothub.aliyuncs.com:8883";
    
    // 物模型初始状态
    Map<String, ValueWrapper> propertyValues = new HashMap<>();
    propertyValues.put("ADASSwitch", new ValueWrapper.BooleanValueWrapper(0));
    params.propertyValues = propertyValues;
    
    // 建立连接
    LinkKit.getInstance().init(params, new ILinkKitConnectListener() {
        public void onInitDone(InitResult initResult) {
            // 连接成功，执行业务逻辑
            executeScheduler(deviceInfoData);
        }
    });
}
```

#### 4.2.2 ThingSample.java（物模型核心）

**职责**：处理物模型相关的所有操作

**核心方法**：

| 方法 | 功能 | 数据流向 |
|------|------|---------|
| `reportDemoProperty()` | 上报设备属性到云端（如 ADASSwitch） | 设备→云 |
| `reportDemoProperty1()` | 上报属性（支持动态参数） | 设备→云 |
| `reportDemoEvent()` | 上报设备事件到云端（如 ErrorEvent） | 设备→云 |
| `setServiceHandler()` | 注册服务处理回调，监听云端下发的属性/服务 | 本地操作（注册监听器） |
| `mCommonHandler.onProcess()` | 处理云端下发的属性设置（set）或服务调用 | 云→设备→云（接收指令→执行→回复） |
| `connectNotifyListener.onNotify()` | 处理云端 RRPC 同步服务调用（系统/自定义） | 云→设备→云（接收→回复） |

**上报属性示例**：

```java
/**
 * 上报属性
 * 流向：设备→云
 * MQTT: PUBLISH /sys/{pk}/{dn}/thing/event/property/post
 * 
 * 使用场景：
 * - 设备状态变化时主动上报
 * - 定时上报设备运行数据
 */
public void reportDemoProperty() {
    Map<String, ValueWrapper> reportData = new HashMap<>();
    reportData.put("ADASSwitch", new ValueWrapper.BooleanValueWrapper(1));
    
    LinkKit.getInstance().getDeviceThing().thingPropertyPost(
        reportData, 
        new IPublishResourceListener() {
            public void onSuccess(String alinkId, Object o) {
                // 上报成功
            }
            public void onError(String alinkId, AError aError) {
                // 上报失败
            }
        }
    );
}
```

**服务处理回调示例**：

```java
/**
 * 服务处理回调（异步服务）
 * 流向：云→设备→云（接收指令→执行→回复）
 * MQTT: 接收 PUBLISH /sys/{pk}/{dn}/thing/service/{serviceId}
 *       回复 PUBLISH /sys/{pk}/{dn}/thing/service/{serviceId}_reply
 * 
 * 触发时机：
 * - 本地控制端调用 invokeThingService() API
 * - IoT 平台通过 MQTT 推送到设备
 * - 此回调被触发
 */
private ITResRequestHandler mCommonHandler = new ITResRequestHandler() {
    public void onProcess(String identify, Object result, 
                        ITResResponseCallback callback) {
        
        if (SERVICE_SET.equals(identify)) {
            // ===== 处理属性设置 =====
            Map<String, ValueWrapper> data = 
                ((InputParams) result).getData();
            
            // TODO: 将属性值设置到真实硬件
            boolean success = setHardwareProperty(data);
            
            if (success) {
                // 回复云端：设置成功
                callback.onComplete(identify, null, null);
            } else {
                // 回复云端：设置失败
                AError error = new AError();
                error.setCode(100);
                error.setMsg("setPropertyFailed");
                callback.onComplete(identify, new ErrorInfo(error), null);
            }
            
        } else {
            // ===== 处理自定义服务调用 =====
            if ("restart".equals(identify)) {
                executeRestart();
            }
            
            // 构造返回参数
            OutputParams output = new OutputParams();
            
            // 回复云端
            callback.onComplete(identify, null, output);
        }
    }
};
```

#### 4.2.3 其他核心类

**MqttSample.java** - MQTT 基础通信

| 方法 | 功能 | 数据流向 |
|------|------|---------|
| `publish()` | 向指定 Topic 发布消息 | 设备→云 |
| `subscribe()` | 订阅指定 Topic | 设备→云（订阅请求） |
| `unSubscribe()` | 取消订阅 Topic | 设备→云（取消订阅请求） |
| `registerResource()` | 注册资源监听器，接收云端自定义 Topic 下行 | 云→设备→云（接收→回复） |

**COTASample.java** - 远程配置

| 方法 | 功能 | 数据流向 |
|------|------|---------|
| `setCOTAChangeListener()` | 监听云端远程配置下发 | 云→设备（接收配置） |
| `cOTAGet()` | 主动请求获取云端配置 | 设备→云→设备（请求→接收） |

**DeviceShadowSample.java** - 设备影子

| 方法 | 功能 | 数据流向 |
|------|------|---------|
| `shadowGet()` | 获取云端设备影子 | 设备→云→设备（请求→接收） |
| `shadowUpdate()` | 更新设备影子到云端 | 设备→云 |
| `shadowDelete()` | 删除设备影子指定属性 | 设备→云 |
| `shadowDeleteAll()` | 删除设备影子所有属性 | 设备→云 |
| `listenDownStream()` | 监听云端设备影子控制指令 | 云→设备→云（接收→执行→上报） |
| `shadowUpstream()` | 通用影子上报方法（内部调用） | 设备→云 |

**GatewaySample.java** - 网关子设备管理

| 方法 | 功能 | 数据流向 |
|------|------|---------|
| `subdevRegister()` | 子设备动态注册 | 网关→云→网关（注册→获取三元组） |
| `getSubDevices()` | 获取网关下的子设备列表 | 网关→云→网关（请求→接收） |
| `addSubDevice()` | 添加子设备到网关 | 网关→云（建立拓扑关系） |
| `deleteSubDevice()` | 从网关删除子设备 | 网关→云 |
| `subDevOnline()` | 代理子设备上线 | 网关→云（子设备上线通知） |
| `subDevOffline()` | 代理子设备下线 | 网关→云（子设备下线通知） |
| `subDevSubscribe()` | 代理子设备订阅 Topic | 网关→云（订阅请求） |
| `subDevPublish()` | 代理子设备发布消息 | 网关→云（代发消息） |
| `subDevUnsubscribe()` | 代理子设备取消订阅 | 网关→云（取消订阅） |
| `subDevDisable()` | 监听子设备禁用指令 | 云→网关→云（接收→回复） |
| `testSubdevThing()` | 初始化子设备物模型 | 本地操作 |

---

## 5. 部署方案详解

### 5.1 上位机端部署

#### 步骤 1：在 IoT 控制台创建产品和设备

**1. 登录阿里云 IoT 控制台**
```
https://iot.console.aliyun.com/
```

**2. 创建产品**
```
产品管理 → 创建产品
- 产品名称：UpperComputer
- 节点类型：直连设备
- 联网方式：WiFi/以太网
- 数据格式：ICA 标准数据格式（Alink JSON）
- 认证方式：设备密钥

创建成功后获得：productKey = "k03k41dVanO"
```

**3. 定义物模型**
```
产品详情 → 功能定义 → 添加自定义功能

添加属性：
- 标识符：ADASSwitch
- 数据类型：布尔型
- 读写类型：读写

添加事件：
- 标识符：ErrorEvent
- 事件类型：告警
- 输出参数：
  * ErrorCode (int)
  * ErrorDesc (text)

添加服务：
- 标识符：restart
- 调用方式：异步
- 输入参数：无
- 输出参数：无
```

**4. 添加设备**
```
产品详情 → 设备 → 添加设备
- DeviceName：Beijing_PC_001

添加成功后，一次性显示三元组（务必保存）：
{
  "productKey": "k03k41dVanO",
  "deviceName": "Beijing_PC_001",
  "deviceSecret": "d7ee3a8a2c8718b0ad6766a0f2c42f54"
}
```

#### 步骤 2：打包项目

```bash
# 在项目根目录执行
cd e:\wulianwnag\parkingLock\parkingLock1

# Maven 打包
mvn clean package

# 生成文件
target/parkingLock1.jar
```

#### 步骤 3：部署到上位机

```bash
# 方式一：通过 SCP 上传（Linux/Mac 上位机）
scp target/parkingLock1.jar user@上位机IP:/opt/app/
scp device_id.json user@上位机IP:/opt/app/

# 方式二：通过远程桌面上传（Windows 上位机）
# 使用 ToDesk/向日葵连接上位机，直接复制文件

# 方式三：通过 FTP 上传
ftp 上位机IP
put parkingLock1.jar
put device_id.json
```

#### 步骤 4：配置三元组

```bash
# 在上位机上编辑配置文件
vim /opt/app/device_id.json

# 填入内容
{
  "productKey": "k03k41dVanO",
  "deviceName": "Beijing_PC_001",
  "deviceSecret": "d7ee3a8a2c8718b0ad6766a0f2c42f54",
  "instanceId": "iot-06z00bmkq776uhu",
  "region": "cn-shanghai"
}
```

#### 步骤 5：运行程序

```bash
# 方式一：直接运行
java -jar /opt/app/parkingLock1.jar

# 方式二：后台运行
nohup java -jar /opt/app/parkingLock1.jar > /opt/app/logs/app.log 2>&1 &

# 方式三：使用 systemd 管理（推荐）
# 创建服务文件
sudo vim /etc/systemd/system/iot-device.service

[Unit]
Description=IoT Device Service
After=network.target

[Service]
Type=simple
User=iot
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -jar /opt/app/parkingLock1.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target

# 启动服务
sudo systemctl start iot-device
sudo systemctl enable iot-device  # 开机自启

# 查看日志
sudo journalctl -u iot-device -f
```

#### 步骤 6：验证连接

```bash
# 查看日志，确认连接成功
tail -f /opt/app/logs/app.log

# 应该看到类似输出：
# onInitDone result=InitResult{...}
# 设备已上线

# 在 IoT 控制台查看
# 设备管理 → 设备 → Beijing_PC_001
# 状态应显示：在线
```

### 5.2 本地控制端开发

#### 步骤 1：创建新的 Maven 项目

```xml
<!-- pom.xml -->
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>iot-controller</artifactId>
    <version>1.0.0</version>
    
    <dependencies>
        <!-- 阿里云 IoT 服务端 SDK -->
        <dependency>
            <groupId>com.aliyun</groupId>
            <artifactId>alibabacloud-iot20180120</artifactId>
            <version>3.0.8</version>
        </dependency>
        
        <!-- 阿里云核心库 -->
        <dependency>
            <groupId>com.aliyun</groupId>
            <artifactId>tea-openapi</artifactId>
            <version>0.2.8</version>
        </dependency>
    </dependencies>
</project>
```

#### 步骤 2：编写控制代码

```java
package com.example.iot;

import com.aliyun.iot20180120.Client;
import com.aliyun.iot20180120.models.*;
import com.aliyun.teaopenapi.models.Config;

public class IoTController {
    
    private Client client;
    private String instanceId = "iot-06z00bmkq776uhu";
    private String productKey = "k03k41dVanO";
    private String deviceName = "Beijing_PC_001";
    
    /**
     * 初始化客户端
     */
    public IoTController() throws Exception {
        Config config = new Config()
            .setAccessKeyId("你的AccessKeyId")
            .setAccessKeySecret("你的AccessKeySecret")
            .setEndpoint("iot.cn-shanghai.aliyuncs.com");
        
        this.client = new Client(config);
    }
    
    /**
     * 调用设备服务
     */
    public void invokeService(String serviceId, String args) throws Exception {
        InvokeThingServiceRequest request = new InvokeThingServiceRequest()
            .setIotInstanceId(instanceId)
            .setProductKey(productKey)
            .setDeviceName(deviceName)
            .setIdentifier(serviceId)
            .setArgs(args);
        
        InvokeThingServiceResponse response = client.invokeThingService(request);
        
        System.out.println("调用结果: " + response.getBody().getSuccess());
        System.out.println("消息ID: " + response.getBody().getData().getMessageId());
    }
    
    /**
     * 设置设备属性
     */
    public void setProperty(String propertyJson) throws Exception {
        SetDevicePropertyRequest request = new SetDevicePropertyRequest()
            .setIotInstanceId(instanceId)
            .setProductKey(productKey)
            .setDeviceName(deviceName)
            .setItems(propertyJson);
        
        SetDevicePropertyResponse response = client.setDeviceProperty(request);
        
        System.out.println("设置结果: " + response.getBody().getSuccess());
    }
    
    /**
     * 查询设备属性
     */
    public void queryProperty() throws Exception {
        QueryDevicePropertyStatusRequest request = 
            new QueryDevicePropertyStatusRequest()
                .setIotInstanceId(instanceId)
                .setProductKey(productKey)
                .setDeviceName(deviceName);
        
        QueryDevicePropertyStatusResponse response = 
            client.queryDevicePropertyStatus(request);
        
        System.out.println("设备属性: " + response.getBody().getData());
    }
    
    /**
     * 查询设备详情
     */
    public void queryDeviceDetail() throws Exception {
        QueryDeviceDetailRequest request = new QueryDeviceDetailRequest()
            .setIotInstanceId(instanceId)
            .setProductKey(productKey)
            .setDeviceName(deviceName);
        
        QueryDeviceDetailResponse response = client.queryDeviceDetail(request);
        
        System.out.println("设备状态: " + response.getBody().getData().getStatus());
        System.out.println("在线状态: " + response.getBody().getData().getOnline());
    }
    
    /**
     * 主函数示例
     */
    public static void main(String[] args) throws Exception {
        IoTController controller = new IoTController();
        
        // 1. 查询设备状态
        controller.queryDeviceDetail();
        
        // 2. 查询设备属性
        controller.queryProperty();
        
        // 3. 设置设备属性
        controller.setProperty("{\"ADASSwitch\": 1}");
        
        // 4. 调用设备服务
        controller.invokeService("restart", "{}");
    }
}
```

---

## 6. 完整开发流程

### 6.1 开发流程图

```
┌──────────────────────────────────────────────────────────────┐
│                     完整开发流程                              │
└──────────────────────────────────────────────────────────────┘

第一阶段：云端准备
├─ 1. 创建产品（获得 productKey）
├─ 2. 定义物模型（属性/事件/服务）
├─ 3. 添加设备（获得 deviceName + deviceSecret）
└─ 4. 记录实例 ID 和 region

第二阶段：上位机开发
├─ 5. 配置 device_id.json（填入三元组）
├─ 6. 修改 HelloWorld.main()（如需定制）
├─ 7. 实现业务逻辑
│   ├─ ThingSample.mCommonHandler（处理云端指令）
│   ├─ 定时上报属性（reportDemoProperty）
│   └─ 控制硬件设备（串口/GPIO）
├─ 8. 打包项目（mvn package）
└─ 9. 部署到上位机运行

第三阶段：本地控制端开发
├─ 10. 创建新项目（引入服务端 SDK）
├─ 11. 配置 AccessKey
├─ 12. 实现控制逻辑
│   ├─ invokeThingService()（调用服务）
│   ├─ setDeviceProperty()（设置属性）
│   └─ queryDevicePropertyStatus()（查询状态）
└─ 13. 测试控制功能

第四阶段：联调测试
├─ 14. 启动上位机程序
├─ 15. 确认设备上线
├─ 16. 本地控制端下发指令
├─ 17. 观察上位机日志
├─ 18. 验证硬件动作
└─ 19. 查看云端数据
```

### 6.2 典型业务场景实现

#### 场景一：远程重启上位机

**云端配置**：
```json
{
  "identifier": "restart",
  "name": "重启",
  "callType": "async",
  "inputData": [],
  "outputData": []
}
```

**上位机端代码**：
```java
// ThingSample.java
private ITResRequestHandler mCommonHandler = new ITResRequestHandler() {
    public void onProcess(String identify, Object result, 
                        ITResResponseCallback callback) {
        if ("restart".equals(identify)) {
            try {
                // 保存当前状态
                saveCurrentState();
                
                // 回复云端
                callback.onComplete(identify, null, null);
                
                // 延迟重启
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        System.exit(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                
            } catch (Exception e) {
                AError error = new AError();
                error.setCode(500);
                error.setMsg("重启失败: " + e.getMessage());
                callback.onComplete(identify, new ErrorInfo(error), null);
            }
        }
    }
};
```

**本地控制端代码**：
```java
public void restartDevice() throws Exception {
    InvokeThingServiceRequest request = new InvokeThingServiceRequest()
        .setIotInstanceId(instanceId)
        .setProductKey(productKey)
        .setDeviceName(deviceName)
        .setIdentifier("restart")
        .setArgs("{}");
    
    InvokeThingServiceResponse response = client.invokeThingService(request);
    
    if (response.getBody().getSuccess()) {
        System.out.println("重启指令已下发");
    }
}
```

#### 场景二：实时监控设备状态

**上位机端代码**：
```java
public class DeviceMonitor {
    
    private ThingSample thingSample;
    
    public void startMonitoring() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        // 每 10 秒上报一次
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, ValueWrapper> data = collectDeviceData();
                
                LinkKit.getInstance().getDeviceThing()
                      .thingPropertyPost(data, new IPublishResourceListener() {
                          public void onSuccess(String alinkId, Object o) {
                              System.out.println("状态上报成功");
                          }
                      });
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
    
    private Map<String, ValueWrapper> collectDeviceData() {
        Map<String, ValueWrapper> data = new HashMap<>();
        data.put("cpuUsage", new ValueWrapper.DoubleValueWrapper(getCpuUsage()));
        data.put("memUsage", new ValueWrapper.DoubleValueWrapper(getMemoryUsage()));
        data.put("status", new ValueWrapper.IntValueWrapper(1));
        return data;
    }
}
```

**本地控制端代码**：
```java
public class DeviceMonitorUI {
    
    private IoTController controller;
    
    public void startRealTimeMonitor() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    QueryDevicePropertyStatusResponse response = 
                        controller.queryProperty();
                    
                    List<PropertyStatusInfo> properties = 
                        response.getBody().getData().getList().getPropertyStatusInfo();
                    
                    for (PropertyStatusInfo prop : properties) {
                        System.out.println(prop.getIdentifier() + ": " 
                                         + prop.getValue());
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 5000);
    }
}
```

---

## 7. 常见问题与解决方案

### 7.1 连接问题

#### Q1: 设备连接失败，报错 "MQTT connect failed"

**可能原因**：
1. 三元组配置错误
2. 网络不通
3. 时间不同步

**解决方案**：
```bash
# 1. 检查三元组
cat device_id.json

# 2. 测试网络连通性
ping iot-06z00bmkq776uhu.mqtt.iothub.aliyuncs.com

# 3. 同步系统时间
sudo ntpdate ntp.aliyun.com

# 4. 查看详细日志
ALog.setLevel(ALog.LEVEL_DEBUG);
```

#### Q2: 设备频繁掉线重连

**可能原因**：
1. 网络不稳定
2. 心跳超时
3. 多个设备使用同一三元组

**解决方案**：
```java
// 调整心跳间隔
IoTMqttClientConfig config = new IoTMqttClientConfig();
config.keepAliveSec = 60;

// 启用离线消息
config.receiveOfflineMsg = true;
```

### 7.2 数据上报问题

#### Q3: 属性上报成功，但云端看不到数据

**可能原因**：
1. 物模型定义不一致
2. 数据类型错误

**解决方案**：
```java
// 确保标识符和数据类型匹配
Map<String, ValueWrapper> data = new HashMap<>();
data.put("temperature", new ValueWrapper.DoubleValueWrapper(25.5));

// 查看上报日志
LinkKit.getInstance().getDeviceThing().thingPropertyPost(data, 
    new IPublishResourceListener() {
        public void onSuccess(String alinkId, Object o) {
            System.out.println("上报成功: " + o);
        }
        public void onError(String alinkId, AError aError) {
            System.out.println("上报失败: " + aError.getMsg());
        }
    }
);
```

### 7.3 服务调用问题

#### Q4: 本地调用服务，上位机没有响应

**可能原因**：
1. 服务处理器未注册
2. 服务标识符不匹配

**解决方案**：
```java
// 1. 确保调用了 setServiceHandler()
private void testDeviceModel() {
    thingTestManager = new ThingSample(pk, dn);
    thingTestManager.setServiceHandler();  // 必须调用
}

// 2. 检查服务标识符
public void onProcess(String identify, ...) {
    if ("restart".equals(identify)) {  // 标识符必须一致
        // 处理逻辑
    }
}
```

### 7.4 IP 连接问题

#### Q5: 我还是想直接通过 IP 连接上位机怎么办？

**解决方案**：

**方案一：ToDesk（最简单）**
```
1. 上位机安装 ToDesk
2. 记录设备识别码
3. 本地电脑安装 ToDesk，输入识别码连接
优点：不需要 IP，不需要配置
```

**方案二：frp 内网穿透**
```bash
# frpc.ini（上位机配置）
[common]
server_addr = 47.100.200.50
server_port = 7000

[ssh]
type = tcp
local_ip = 127.0.0.1
local_port = 22
remote_port = 6000

# 本地连接
ssh -p 6000 user@47.100.200.50
```

---

## 总结

### 核心要点

1. **架构理解**
   - 本地控制端：调用云端 API（HTTPS）
   - IoT 平台：消息中转、数据存储
   - 上位机：MQTT 长连接、接收指令、上报数据

2. **通信方式**
   - 本地 ↔ 云端：HTTPS API
   - 上位机 ↔ 云端：MQTT 协议
   - 不需要知道上位机 IP

3. **代码部署**
   - 上位机：打包整个项目，运行 `HelloWorld.main()`
   - 本地：新建项目，调用服务端 SDK

4. **数据流向**
   - 设备→云：属性上报、事件上报
   - 云→设备：属性设置、服务调用
   - 本地→云：API 调用
   - 云→本地：API 响应

5. **关键方法**
   - `init()`：建立 MQTT 连接
   - `setServiceHandler()`：注册服务处理器
   - `reportDemoProperty()`：上报属性
   - `mCommonHandler.onProcess()`：处理云端指令

**记住**：整个系统通过阿里云 IoT 平台中转，你不需要知道上位机的 IP 地址，只需要知道它的 `deviceName` 即可远程控制。
