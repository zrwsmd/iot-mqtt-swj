# MQTT 上行/下行 Topic 完整文档

---

## 目录

1. [Topic 概述](#1-topic-概述)
2. [上行 Topic（设备→云）](#2-上行-topic设备云)
3. [下行 Topic（云→设备）](#3-下行-topic云设备)
4. [Topic 格式规范](#4-topic-格式规范)
5. [完整方法与 Topic 对照表](#5-完整方法与-topic-对照表)

---

## 1. Topic 概述

### 1.1 什么是 MQTT Topic

**Topic** 是 MQTT 协议中消息的路由地址，类似于邮件地址。设备和云端通过订阅和发布不同的 Topic 来实现双向通信。

### 1.2 Topic 方向分类

```
上行 Topic（Upstream）
├─ 设备 → 云端
├─ 设备主动发布消息
└─ 例如：上报属性、上报事件

下行 Topic（Downstream）
├─ 云端 → 设备
├─ 设备订阅并接收消息
└─ 例如：属性设置、服务调用
```

### 1.3 Topic 基本格式

```
/sys/{productKey}/{deviceName}/thing/{功能}/{操作}

示例：
/sys/k03k41dVanO/Beijing_PC_001/thing/event/property/post
     ↑           ↑                ↑     ↑      ↑        ↑
  产品标识    设备名称           物模型 事件  属性    上报
```

---

## 2. 上行 Topic（设备→云）

### 2.1 属性上报

#### 方法：`reportDemoProperty()` / `reportDemoProperty1()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/event/property/post
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台
```

**Payload 格式**：
```json
{
  "id": "123",
  "version": "1.0",
  "params": {
    "ADASSwitch": 1,
    "temperature": 25.5
  },
  "method": "thing.event.property.post"
}
```

**代码示例**：
```java
public void reportDemoProperty() {
    String identity = "ADASSwitch";
    ValueWrapper intWrapper = new ValueWrapper.BooleanValueWrapper(1);
    
    Map<String, ValueWrapper> reportData = new HashMap<>();
    reportData.put(identity, intWrapper);
    
    // 发布到上行 Topic
    LinkKit.getInstance().getDeviceThing().thingPropertyPost(reportData, 
        new IPublishResourceListener() {
            public void onSuccess(String alinkId, Object o) {
                // 上报成功
            }
        }
    );
}
```

**使用场景**：
- 定时上报设备状态
- 设备状态变化时主动上报
- 响应云端属性设置后的确认上报

---

### 2.2 事件上报

#### 方法：`reportDemoEvent()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/event/{eventIdentifier}/post
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台
```

**Payload 格式**：
```json
{
  "id": "123",
  "version": "1.0",
  "params": {
    "ErrorCode": 1,
    "ErrorDesc": "hello123"
  },
  "method": "thing.event.ErrorEvent.post"
}
```

**代码示例**：
```java
public void reportDemoEvent() {
    String identity = "ErrorEvent";
    
    HashMap<String, ValueWrapper> valueWrapperMap = new HashMap<>();
    valueWrapperMap.put("ErrorCode", new ValueWrapper.IntValueWrapper(1));
    valueWrapperMap.put("ErrorDesc", new ValueWrapper.StringValueWrapper("hello123"));
    
    OutputParams params = new OutputParams(valueWrapperMap);
    
    // 发布到上行 Topic
    LinkKit.getInstance().getDeviceThing().thingEventPost(identity, params, 
        new IPublishResourceListener() {
            public void onSuccess(String alinkId, Object o) {
                // 事件上报成功
            }
        }
    );
}
```

**使用场景**：
- 设备告警
- 故障通知
- 重要状态变更通知

---

### 2.3 设备影子上报

#### 方法：`shadowUpdate()` / `shadowUpstream()`

**Topic**：
```
/shadow/update/{productKey}/{deviceName}
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台（设备影子服务）
```

**Payload 格式**：
```json
{
  "method": "update",
  "state": {
    "reported": {
      "temperature": 25.5,
      "status": "running"
    }
  },
  "version": 10
}
```

**代码示例**：
```java
public void shadowUpdate() {
    version++;
    String shadowUpdate = "{\"method\":\"update\",\"state\":{\"reported\":{\"temperature\":25.5}},\"version\":" + version + "}";
    
    // 发布到设备影子上行 Topic
    shadowUpstream(shadowUpdate);
}
```

**使用场景**：
- 更新设备影子状态
- 离线控制场景下的状态同步

---

### 2.4 MQTT 自定义消息发布

#### 方法：`MqttSample.publish()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/deviceinfo/update
（或其他自定义 Topic）
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台
```

**Payload 格式**：
```json
{
  "id": "123",
  "version": "1.0",
  "params": [
    {
      "attrKey": "Temperature",
      "attrValue": "36.8"
    }
  ],
  "method": "thing.deviceinfo.update"
}
```

**代码示例**：
```java
public void publish() {
    MqttPublishRequest request = new MqttPublishRequest();
    request.topic = "/sys/" + productKey + "/" + deviceName + "/thing/deviceinfo/update";
    request.qos = 0;
    request.payloadObj = "{\"id\":\"123\",\"version\":\"1.0\",\"params\":[...]}";
    
    // 发布到自定义上行 Topic
    LinkKit.getInstance().publish(request, new IConnectSendListener() {
        public void onResponse(ARequest aRequest, AResponse aResponse) {
            // 发布成功
        }
    });
}
```

**使用场景**：
- 自定义业务数据上报
- 非物模型数据传输

---

### 2.5 设备上线通知

#### 方法：`HelloWorld.init()` 成功后自动发送

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/event/property/post
（连接成功后自动发送初始属性）
```

**数据流向**：
```
设备 ──CONNECT + PUBLISH──> IoT 平台
```

**说明**：
- 设备连接成功后，SDK 自动发送初始属性值
- 云端更新设备在线状态

---

### 2.6 网关子设备上报

#### 方法：`GatewaySample.subDevPublish()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/sub/register
/sys/{productKey}/{deviceName}/thing/topo/add
/sys/{productKey}/{deviceName}/thing/sub/login
```

**数据流向**：
```
网关 ──PUBLISH──> IoT 平台（代理子设备）
```

**代码示例**：
```java
public void subDevPublish() {
    MqttPublishRequest request = new MqttPublishRequest();
    request.topic = testPublishTopic.replace("{productKey}", subProductKey)
                                    .replace("{deviceName}", subDeviceName);
    request.payloadObj = "{\"id\":\"123\",\"params\":{\"temperature\":25.5}}";
    
    // 网关代理子设备发布消息
    LinkKit.getInstance().publish(request, ...);
}
```

**使用场景**：
- 网关代理子设备上报数据
- 子设备注册、登录

---

### 2.7 服务调用响应（上行回复）

#### 方法：`mCommonHandler.onProcess()` 中的 `callback.onComplete()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/service/{serviceIdentifier}_reply
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台（回复服务调用结果）
```

**Payload 格式**：
```json
{
  "id": "123",
  "code": 200,
  "data": {}
}
```

**代码示例**：
```java
public void onProcess(String identify, Object result, ITResResponseCallback callback) {
    if ("restart".equals(identify)) {
        // 执行重启
        executeRestart();
        
        // 回复云端（发布到上行 Topic）
        callback.onComplete(identify, null, null);
    }
}
```

**使用场景**：
- 响应云端服务调用
- 返回服务执行结果

---

### 2.8 RRPC 同步调用响应

#### 方法：`connectNotifyListener.onNotify()` 中处理 RRPC

**Topic**：
```
/sys/{productKey}/{deviceName}/rrpc/response/{messageId}
/ext/rrpc/{messageId}/response
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台（RRPC 响应）
```

**Payload 格式**：
```json
{
  "id": "messageId",
  "code": 200,
  "data": {}
}
```

**代码示例**：
```java
public void onNotify(String connectId, String topic, AMessage aMessage) {
    if (topic.startsWith("/sys/" + productKey + "/" + deviceName + "/rrpc/request")) {
        MqttPublishRequest request = new MqttPublishRequest();
        request.topic = topic.replace("request", "response");
        String resId = topic.substring(topic.indexOf("rrpc/request/") + 13);
        request.payloadObj = "{\"id\":\"" + resId + "\", \"code\":\"200\"}";
        
        // 发布 RRPC 响应到上行 Topic
        LinkKit.getInstance().getMqttClient().publish(request, ...);
    }
}
```

**使用场景**：
- 同步服务调用（5秒内必须响应）
- 需要立即获取结果的场景

---

### 2.9 设备标签更新

#### 方法：`LabelSample.labelUpdate()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/deviceinfo/update
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台
```

**Payload 格式**：
```json
{
  "id": "123",
  "version": "1.0",
  "params": [
    {
      "attrKey": "Temperature",
      "attrValue": "56.8"
    }
  ],
  "method": "thing.deviceinfo.update"
}
```

**代码示例**：
```java
public void labelUpdate() {
    MqttPublishRequest request = new MqttPublishRequest();
    request.topic = "/sys/" + productKey + "/" + deviceName + "/thing/deviceinfo/update";
    request.payloadObj = "{\"id\":\"123\",\"params\":[{\"attrKey\":\"Temperature\",\"attrValue\":\"56.8\"}]}";
    
    LinkKit.getInstance().publish(request, ...);
}
```

**使用场景**：
- 更新设备标签（元数据）

---

### 2.10 COTA 配置请求

#### 方法：`COTASample.cOTAGet()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/config/get
```

**数据流向**：
```
设备 ──PUBLISH──> IoT 平台（请求配置）
```

**Payload 格式**：
```json
{
  "id": "123",
  "version": "1.0",
  "params": {
    "configScope": "product",
    "getType": "file"
  },
  "method": "thing.config.get"
}
```

**代码示例**：
```java
public void cOTAGet() {
    MqttPublishRequest request = new MqttPublishRequest();
    request.isRPC = false;
    request.topic = "/sys/" + productKey + "/" + deviceName + "/thing/config/get";
    request.payloadObj = "{\"id\":\"123\",\"params\":{\"configScope\":\"product\"}}";
    
    LinkKit.getInstance().publish(request, ...);
}
```

**使用场景**：
- 主动获取云端配置
- 配置同步

---

## 3. 下行 Topic（云→设备）

### 3.1 属性设置

#### 方法：`mCommonHandler.onProcess()` 接收（identify = "set"）

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/service/property/set
```

**数据流向**：
```
IoT 平台 ──PUBLISH──> 设备（订阅）
```

**Payload 格式**：
```json
{
  "method": "thing.service.property.set",
  "id": "123",
  "params": {
    "ADASSwitch": 1,
    "temperature": 30.0
  },
  "version": "1.0.0"
}
```

**代码示例**：
```java
// 设备端订阅（SDK 自动订阅）
// 接收处理
private ITResRequestHandler mCommonHandler = new ITResRequestHandler() {
    public void onProcess(String identify, Object result, ITResResponseCallback callback) {
        if (SERVICE_SET.equals(identify)) {
            // 接收到云端属性设置
            Map<String, ValueWrapper> data = ((InputParams) result).getData();
            
            // 设置到硬件
            setHardware(data);
            
            // 回复云端
            callback.onComplete(identify, null, null);
        }
    }
};
```

**触发方式**：
- 本地控制端调用 `setDeviceProperty()` API
- IoT 控制台手动设置属性

**使用场景**：
- 远程控制设备
- 修改设备配置

---

### 3.2 服务调用

#### 方法：`mCommonHandler.onProcess()` 接收（identify = 服务标识符）

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/service/{serviceIdentifier}
```

**数据流向**：
```
IoT 平台 ──PUBLISH──> 设备（订阅）
```

**Payload 格式**：
```json
{
  "method": "thing.service.restart",
  "id": "123",
  "params": {},
  "version": "1.0.0"
}
```

**代码示例**：
```java
// 设备端订阅（SDK 自动订阅）
// 接收处理
public void onProcess(String identify, Object result, ITResResponseCallback callback) {
    if ("restart".equals(identify)) {
        // 接收到重启服务调用
        executeRestart();
        
        // 回复云端
        OutputParams output = new OutputParams();
        callback.onComplete(identify, null, output);
    }
}
```

**触发方式**：
- 本地控制端调用 `invokeThingService()` API
- IoT 控制台手动调用服务

**使用场景**：
- 远程重启设备
- 执行复杂业务逻辑
- 触发设备动作

---

### 3.3 RRPC 同步调用

#### 方法：`connectNotifyListener.onNotify()` 接收

**Topic**：
```
/sys/{productKey}/{deviceName}/rrpc/request/{messageId}
/ext/rrpc/{messageId}/request
```

**数据流向**：
```
IoT 平台 ──PUBLISH──> 设备（订阅）
```

**Payload 格式**：
```json
{
  "method": "thing.service.test_service",
  "id": "123374967",
  "params": {
    "vv": 60
  },
  "version": "1.0.0"
}
```

**代码示例**：
```java
private IConnectNotifyListener connectNotifyListener = new IConnectNotifyListener() {
    public void onNotify(String connectId, String topic, AMessage aMessage) {
        if (topic.startsWith("/sys/" + productKey + "/" + deviceName + "/rrpc/request")) {
            // 接收到 RRPC 请求
            String payload = new String((byte[]) aMessage.data);
            
            // 处理请求
            processRRPC(payload);
            
            // 立即回复（必须在 5 秒内）
            MqttPublishRequest request = new MqttPublishRequest();
            request.topic = topic.replace("request", "response");
            request.payloadObj = "{\"id\":\"xxx\", \"code\":200}";
            LinkKit.getInstance().getMqttClient().publish(request, ...);
        }
    }
};
```

**触发方式**：
- 本地控制端调用 RRPC API
- 需要同步获取结果的场景

**使用场景**：
- 需要立即获取结果的操作
- 超时时间 5 秒

---

### 3.4 设备影子下发

#### 方法：`DeviceShadowSample.listenDownStream()` 接收

**Topic**：
```
/shadow/get/{productKey}/{deviceName}
```

**数据流向**：
```
IoT 平台 ──PUBLISH──> 设备（订阅）
```

**Payload 格式**：
```json
{
  "method": "control",
  "payload": {
    "status": "success",
    "state": {
      "desired": {
        "temperature": 30.0
      }
    },
    "version": 10
  }
}
```

**代码示例**：
```java
public void listenDownStream() {
    MqttSubscribeRequest request = new MqttSubscribeRequest();
    request.topic = "/shadow/get/" + productKey + "/" + deviceName;
    request.isSubscribe = true;
    
    LinkKit.getInstance().subscribe(request, new IConnectSubscribeListener() {
        public void onSuccess() {
            // 订阅成功
        }
    });
    
    // 注册监听器
    LinkKit.getInstance().registerOnPushListener(new IConnectNotifyListener() {
        public void onNotify(String connectId, String topic, AMessage aMessage) {
            // 接收到影子下发
            String payload = new String((byte[]) aMessage.data);
            JSONObject json = JSON.parseObject(payload);
            JSONObject desired = json.getJSONObject("payload")
                                     .getJSONObject("state")
                                     .getJSONObject("desired");
            
            // 根据期望状态调整设备
            adjustDevice(desired);
        }
    });
}
```

**触发方式**：
- 本地控制端调用 `updateDeviceShadow()` API
- 设备离线时，云端缓存期望状态

**使用场景**：
- 离线控制
- 设备上线后自动同步状态

---

### 3.5 COTA 配置下发

#### 方法：`COTASample.setCOTAChangeListener()` 接收

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/config/push
```

**数据流向**：
```
IoT 平台 ──PUBLISH──> 设备（订阅）
```

**Payload 格式**：
```json
{
  "id": "123",
  "version": "1.0",
  "params": {
    "configId": "config123",
    "configSize": 1024,
    "sign": "xxx",
    "signMethod": "Sha256",
    "url": "https://iot-config.oss.aliyuncs.com/xxx",
    "getType": "file"
  },
  "method": "thing.config.push"
}
```

**代码示例**：
```java
public void setCOTAChangeListener() {
    LinkKit.getInstance().registerOnNotifyListener(new IConnectNotifyListener() {
        public void onNotify(String connectId, String topic, AMessage aMessage) {
            if (topic.contains("thing/config/push")) {
                // 接收到配置下发
                String payload = new String((byte[]) aMessage.data);
                JSONObject json = JSON.parseObject(payload);
                
                // 下载并应用配置
                downloadAndApplyConfig(json);
            }
        }
    });
}
```

**触发方式**：
- IoT 控制台推送配置
- 配置更新时自动推送

**使用场景**：
- 远程配置更新
- 参数调整

---

### 3.6 MQTT 自定义消息订阅

#### 方法：`MqttSample.subscribe()`

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/deviceinfo/update
（或其他自定义 Topic）
```

**数据流向**：
```
IoT 平台 ──PUBLISH──> 设备（订阅）
```

**代码示例**：
```java
public void subscribe() {
    MqttSubscribeRequest request = new MqttSubscribeRequest();
    request.topic = "/sys/" + productKey + "/" + deviceName + "/thing/deviceinfo/update";
    request.isSubscribe = true;
    
    // 订阅自定义下行 Topic
    LinkKit.getInstance().subscribe(request, new IConnectSubscribeListener() {
        public void onSuccess() {
            // 订阅成功
        }
    });
    
    // 注册监听器接收消息
    LinkKit.getInstance().registerOnPushListener(new IConnectNotifyListener() {
        public void onNotify(String connectId, String topic, AMessage aMessage) {
            // 接收到自定义消息
            String payload = new String((byte[]) aMessage.data);
            processCustomMessage(payload);
        }
    });
}
```

**使用场景**：
- 自定义业务消息接收

---

### 3.7 网关子设备控制

#### 方法：`GatewaySample.subDevDisable()` 接收

**Topic**：
```
/sys/{productKey}/{deviceName}/thing/disable
/sys/{productKey}/{deviceName}/thing/enable
/sys/{productKey}/{deviceName}/thing/delete
```

**数据流向**：
```
IoT 平台 ──PUBLISH──> 网关（订阅）
```

**Payload 格式**：
```json
{
  "id": "123",
  "version": "1.0",
  "params": {
    "productKey": "subProductKey",
    "deviceName": "subDeviceName"
  },
  "method": "thing.disable"
}
```

**代码示例**：
```java
public void subDevDisable() {
    LinkKit.getInstance().registerOnNotifyListener(new IConnectNotifyListener() {
        public void onNotify(String connectId, String topic, AMessage aMessage) {
            if (topic.contains("thing/disable")) {
                // 接收到子设备禁用指令
                String payload = new String((byte[]) aMessage.data);
                JSONObject json = JSON.parseObject(payload);
                
                // 禁用子设备
                disableSubDevice(json);
            }
        }
    });
}
```

**触发方式**：
- IoT 控制台禁用/启用子设备
- 删除子设备

**使用场景**：
- 网关管理子设备
- 子设备权限控制

---

## 4. Topic 格式规范

### 4.1 物模型相关 Topic

| 功能 | 上行 Topic | 下行 Topic |
|------|-----------|-----------|
| **属性上报** | `/sys/{pk}/{dn}/thing/event/property/post` | - |
| **属性设置** | `/sys/{pk}/{dn}/thing/service/property/set_reply` | `/sys/{pk}/{dn}/thing/service/property/set` |
| **事件上报** | `/sys/{pk}/{dn}/thing/event/{eventId}/post` | - |
| **服务调用** | `/sys/{pk}/{dn}/thing/service/{serviceId}_reply` | `/sys/{pk}/{dn}/thing/service/{serviceId}` |

### 4.2 设备影子相关 Topic

| 功能 | 上行 Topic | 下行 Topic |
|------|-----------|-----------|
| **更新影子** | `/shadow/update/{pk}/{dn}` | - |
| **获取影子** | `/shadow/get/{pk}/{dn}` | `/shadow/get/{pk}/{dn}` |

### 4.3 RRPC 相关 Topic

| 功能 | 上行 Topic | 下行 Topic |
|------|-----------|-----------|
| **系统 RRPC** | `/sys/{pk}/{dn}/rrpc/response/{msgId}` | `/sys/{pk}/{dn}/rrpc/request/{msgId}` |
| **自定义 RRPC** | `/ext/rrpc/{msgId}/response` | `/ext/rrpc/{msgId}/request` |

### 4.4 其他 Topic

| 功能 | 上行 Topic | 下行 Topic |
|------|-----------|-----------|
| **设备信息更新** | `/sys/{pk}/{dn}/thing/deviceinfo/update` | - |
| **COTA 配置** | `/sys/{pk}/{dn}/thing/config/get` | `/sys/{pk}/{dn}/thing/config/push` |
| **网关拓扑** | `/sys/{pk}/{dn}/thing/topo/add` | - |
| **子设备注册** | `/sys/{pk}/{dn}/thing/sub/register` | - |
| **子设备登录** | `/sys/{pk}/{dn}/thing/sub/login` | - |

---

## 5. 完整方法与 Topic 对照表

### 5.1 ThingSample.java

| 方法 | 方向 | Topic | 说明 |
|------|------|-------|------|
| `reportDemoProperty()` | 上行 | `/sys/{pk}/{dn}/thing/event/property/post` | 上报属性 |
| `reportDemoProperty1()` | 上行 | `/sys/{pk}/{dn}/thing/event/property/post` | 上报属性（动态参数） |
| `reportDemoEvent()` | 上行 | `/sys/{pk}/{dn}/thing/event/{eventId}/post` | 上报事件 |
| `setServiceHandler()` | 下行 | `/sys/{pk}/{dn}/thing/service/property/set`<br>`/sys/{pk}/{dn}/thing/service/{serviceId}` | 注册服务处理器（订阅） |
| `mCommonHandler.onProcess()` | 下行 | 接收上述下行 Topic | 处理属性设置和服务调用 |
| `connectNotifyListener.onNotify()` | 下行 | `/sys/{pk}/{dn}/rrpc/request/{msgId}`<br>`/ext/rrpc/{msgId}/request` | 处理 RRPC 请求 |

### 5.2 MqttSample.java

| 方法 | 方向 | Topic | 说明 |
|------|------|-------|------|
| `publish()` | 上行 | `/sys/{pk}/{dn}/thing/deviceinfo/update` | 发布自定义消息 |
| `subscribe()` | 下行 | `/sys/{pk}/{dn}/thing/deviceinfo/update` | 订阅自定义消息 |
| `unSubscribe()` | - | `/sys/{pk}/{dn}/thing/deviceinfo/update` | 取消订阅 |
| `registerResource()` | 下行 | 自定义 Topic | 注册资源监听器 |

### 5.3 COTASample.java

| 方法 | 方向 | Topic | 说明 |
|------|------|-------|------|
| `setCOTAChangeListener()` | 下行 | `/sys/{pk}/{dn}/thing/config/push` | 监听配置下发 |
| `cOTAGet()` | 上行 | `/sys/{pk}/{dn}/thing/config/get` | 请求获取配置 |

### 5.4 DeviceShadowSample.java

| 方法 | 方向 | Topic | 说明 |
|------|------|-------|------|
| `shadowGet()` | 上行 | `/shadow/get/{pk}/{dn}` | 获取设备影子 |
| `shadowUpdate()` | 上行 | `/shadow/update/{pk}/{dn}` | 更新设备影子 |
| `shadowDelete()` | 上行 | `/shadow/update/{pk}/{dn}` | 删除影子属性 |
| `shadowDeleteAll()` | 上行 | `/shadow/update/{pk}/{dn}` | 删除所有影子属性 |
| `listenDownStream()` | 下行 | `/shadow/get/{pk}/{dn}` | 监听影子控制指令 |

### 5.5 LabelSample.java

| 方法 | 方向 | Topic | 说明 |
|------|------|-------|------|
| `labelUpdate()` | 上行 | `/sys/{pk}/{dn}/thing/deviceinfo/update` | 更新设备标签 |
| `labelDelete()` | 上行 | `/sys/{pk}/{dn}/thing/deviceinfo/delete` | 删除设备标签 |

### 5.6 GatewaySample.java

| 方法 | 方向 | Topic | 说明 |
|------|------|-------|------|
| `subdevRegister()` | 上行 | `/sys/{pk}/{dn}/thing/sub/register` | 子设备动态注册 |
| `getSubDevices()` | 上行 | `/sys/{pk}/{dn}/thing/list/found` | 获取子设备列表 |
| `addSubDevice()` | 上行 | `/sys/{pk}/{dn}/thing/topo/add` | 添加子设备 |
| `deleteSubDevice()` | 上行 | `/sys/{pk}/{dn}/thing/topo/delete` | 删除子设备 |
| `subDevOnline()` | 上行 | `/ext/session/{pk}/{dn}/combine/login` | 子设备上线 |
| `subDevOffline()` | 上行 | `/ext/session/{pk}/{dn}/combine/logout` | 子设备下线 |
| `subDevSubscribe()` | 下行 | 代理子设备订阅 Topic | 代理子设备订阅 |
| `subDevPublish()` | 上行 | 代理子设备发布 Topic | 代理子设备发布 |
| `subDevDisable()` | 下行 | `/sys/{pk}/{dn}/thing/disable` | 监听子设备禁用 |

### 5.7 SubThingSample.java

| 方法 | 方向 | Topic | 说明 |
|------|------|-------|------|
| `reportProperty()` | 上行 | `/sys/{subPk}/{subDn}/thing/event/property/post` | 子设备上报属性 |
| `reportEvent()` | 上行 | `/sys/{subPk}/{subDn}/thing/event/{eventId}/post` | 子设备上报事件 |
| `setServiceHandler()` | 下行 | `/sys/{subPk}/{subDn}/thing/service/property/set`<br>`/sys/{subPk}/{subDn}/thing/service/{serviceId}` | 子设备服务处理 |

---

## 总结

### 上行 Topic 特点
- 设备主动发布（PUBLISH）
- 用于数据上报、状态同步
- 不需要提前订阅

### 下行 Topic 特点
- 设备需要订阅（SUBSCRIBE）
- 用于接收控制指令、配置下发
- SDK 会自动订阅系统 Topic

### 关键理解
```
上行 = 设备说话（PUBLISH）
下行 = 设备听话（SUBSCRIBE + 接收 PUBLISH）

物模型 Topic = 标准化的设备通信协议
自定义 Topic = 灵活的业务数据传输
```

---

**文档版本**：v1.0  
**更新时间**：2026-03-03  
**适用项目**：parkingLock1（阿里云 IoT LinkKit Java SDK）
