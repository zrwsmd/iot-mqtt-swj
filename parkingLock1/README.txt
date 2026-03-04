Demo 使用说明
1.修改 device_id.son文件，填入三元组等信息：
      对于企业实例, 或者2021年07月30日之后（含当日）开通的物联网平台服务下公共实例,
      实例的详情页面下会有实例id,一般格式为iot-*******，请将其填入device_id.json的instanceId字段，
      请参考https://help.aliyun.com/document_detail/147356.htm

      对于2021年07月30日之前（不含当日）开通的物联网平台服务下公共实例，
      请将开通物联网平台时所选的区域信息，填入device_id.json的region字段
      具体包括如下选项：
          上海  ------  cn-shanghai
          新加坡 -----  ap-southeast-1
          日本  -----   ap-northeast-1
          美西  -----   us-west-1
          德国  -----   eu-central-1
       比如上海的region的话，请填入cn-shanghai
       注：北京和深圳地域的用户请在device_id.json中填写instanceId,不要填写region

2.执行HelloWorld工程的main方法

# 阿里云 IoT 平台设备端 SDK Demo

## 项目简介

本项目是基于阿里云 IoT 平台 LinkKit Java SDK 开发的设备端示例程序，用于演示如何将物理设备（上位机）接入阿里云 IoT 平台，实现设备与云端的双向通信。

## 项目功能

### 核心功能

1. **设备连接与认证**
   - 支持一机一密认证方式
   - 支持一型一密动态注册
   - 建立 MQTT 长连接与 IoT 平台通信

2. **物模型功能**
   - 属性上报：设备主动上报属性数据到云端
   - 属性设置：接收云端下发的属性设置指令
   - 服务调用：响应云端调用的设备服务（如 restart 重启服务）
   - 事件上报：上报设备事件（如 ErrorEvent 错误事件）

3. **设备影子**
   - 支持设备影子功能，实现设备离线状态缓存

4. **网关与子设备**
   - 支持网关设备管理
   - 支持子设备接入与管理

5. **OTA 升级**
   - 支持配置 OTA（COTA）功能

## 项目架构

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  本地控制端      │         │   IoT 平台       │         │   设备端（JAR）  │
│  (Node.js)       │         │  (阿里云)        │         │   (上位机)       │
└────────┬────────┘         └────────┬────────┘         └────────┬────────┘
         │                           │                           │
         │ ① HTTP API 调用           │                           │
         │ (setProperty/             │                           │
         │  invokeService)           │                           │
         ├──────────────────────────►│                           │
         │                           │                           │
         │                           │ ② MQTT 下发               │
         │                           ├──────────────────────────►│
         │                           │                           │
         │                           │                           │ ③ 设备处理
         │                           │                           │ (ThingSample)
         │                           │                           │
         │                           │ ④ MQTT 上报               │
         │                           │◄──────────────────────────┤
         │                           │                           │
         │ ⑤ HTTP Response           │                           │
         │◄──────────────────────────┤                           │
```

## 项目结构

```
parkingLock1/
├── src/main/java/com/aliyun/alink/devicesdk/demo/
│   ├── HelloWorld.java              # 主入口，设备初始化
│   ├── ThingSample.java             # 物模型功能实现
│   ├── BaseSample.java              # 基础工具类
│   ├── MqttSample.java              # MQTT 基础示例
│   ├── DeviceShadowSample.java      # 设备影子示例
│   ├── GatewaySample.java           # 网关功能示例
│   ├── SubThingSample.java          # 子设备物模型示例
│   ├── COTASample.java              # 配置 OTA 示例
│   ├── LabelSample.java             # 设备标签示例
│   └── docs/                        # 完整开发文档
│       ├── 完整部署测试指南.md       # JAR 部署到云服务器指南
│       ├── NodeJS本地控制端从零开发完整教程.md  # Node.js 控制端开发
│       └── ...
├── device_id.json                   # 设备三元组配置文件
├── pom.xml                          # Maven 配置文件
└── README.txt                       # 本文件

```

## 核心类说明

### 1. HelloWorld.java
- **功能**：程序主入口
- **职责**：
  - 读取 device_id.json 配置
  - 初始化 LinkKit SDK
  - 建立 MQTT 连接
  - 启动物模型功能

### 2. ThingSample.java
- **功能**：物模型核心实现
- **职责**：
  - 属性上报（reportDemoProperty）
  - 属性设置处理（setServiceHandler - set 服务）
  - 服务调用处理（setServiceHandler - restart 等服务）
  - 事件上报（reportDemoEvent）

### 3. DeviceShadowSample.java
- **功能**：设备影子功能
- **职责**：
  - 设备影子更新
  - 设备影子查询
  - 离线状态缓存

### 4. GatewaySample.java
- **功能**：网关设备管理
- **职责**：
  - 子设备添加/删除
  - 子设备上线/下线
  - 子设备拓扑关系管理

## 使用说明

### 1. 配置设备信息

修改 `device_id.json` 文件，填入设备三元组：

```json
{
  "productKey": "你的ProductKey",
  "deviceName": "你的DeviceName",
  "deviceSecret": "你的DeviceSecret",
  "instanceId": "iot-xxxxxxxx",
  "region": "cn-shanghai"
}
```

**配置说明**：

- **企业实例** 或 **2021年07月30日之后开通的公共实例**：
  - 填写 `instanceId`（格式：iot-*******）
  - 参考：https://help.aliyun.com/document_detail/147356.htm

- **2021年07月30日之前开通的公共实例**：
  - 填写 `region`（区域信息）
  - 可选区域：
    - 上海：cn-shanghai
    - 新加坡：ap-southeast-1
    - 日本：ap-northeast-1
    - 美西：us-west-1
    - 德国：eu-central-1
  - 注意：北京和深圳地域请填写 instanceId，不要填写 region

### 2. 本地运行测试

```bash
# 方式一：IDE 运行
在 IDE 中运行 HelloWorld.java 的 main 方法

# 方式二：Maven 运行
mvn clean compile
mvn exec:java -Dexec.mainClass="com.aliyun.alink.devicesdk.demo.HelloWorld"
```

### 3. 打包为 JAR

```bash
# 清理并打包
mvn clean package -DskipTests

# 生成的 JAR 文件
target/parkingLock1-1.0.0-jar-with-dependencies.jar
```

### 4. 部署到云服务器

详细步骤请参考：`src/main/java/com/aliyun/alink/devicesdk/demo/docs/完整部署测试指南.md`

**快速部署**：

```bash
# 1. 上传 JAR 包和配置文件到服务器
scp target/parkingLock1-1.0.0-jar-with-dependencies.jar root@your-server:/opt/iot-device/
scp device_id.json root@your-server:/opt/iot-device/

# 2. 在服务器上运行
cd /opt/iot-device
java -jar parkingLock1-1.0.0-jar-with-dependencies.jar

# 3. 配置 systemd 服务（推荐）
sudo systemctl start iot-device
sudo systemctl enable iot-device
```

### 5. 本地控制端开发

详细教程请参考：`src/main/java/com/aliyun/alink/devicesdk/demo/docs/NodeJS本地控制端从零开发完整教程.md`

**Node.js 控制端功能**：
- 设置设备属性（setProperty）
- 查询设备属性（queryProperty）
- 调用设备服务（invokeService）
- 查询设备详情（queryDeviceDetail）
- 获取设备状态（getDeviceStatus）

## 物模型定义

当前项目支持的物模型：

### 属性（Property）
- **ADASSwitch**：bool 类型，0-关 / 1-开

### 服务（Service）
- **restart**：异步调用，重启设备
- **set**：设置属性
- **get**：获取属性

### 事件（Event）
- **ErrorEvent**：告警事件，包含 ErrorCode 和 ErrorDesc 参数

## 通信方向说明

### 云端 → 设备（下行）
- 属性设置（SetDeviceProperty）
- 服务调用（InvokeThingService）

### 设备 → 云端（上行）
- 属性上报（thingPropertyPost）
- 事件上报（thingEventPost）

### 本地 → 云端 → 设备
- 本地控制端通过阿里云 OpenAPI 调用云端接口
- 云端通过 MQTT 下发指令到设备
- 设备处理后通过 MQTT 上报结果到云端
- 云端返回结果给本地控制端

## 依赖说明

### 核心依赖
- **lp-iot-linkkit-java**：阿里云 IoT LinkKit Java SDK
- **gson**：JSON 解析
- **fastjson**：JSON 解析
- **hutool-all**：Java 工具库

### 编译环境
- **Java**：JDK 8+
- **Maven**：3.6+

## 常见问题

### 1. 设备连接失败
- 检查 device_id.json 配置是否正确
- 检查网络连接
- 检查 instanceId 或 region 配置

### 2. 日志乱码
- 在 pom.xml 中添加 `<encoding>UTF-8</encoding>`
- 在启动参数中添加 `-Dfile.encoding=UTF-8`

### 3. 属性设置不生效
- 检查物模型定义是否正确
- 检查设备是否在线
- 查看设备端日志

### 4. RAM 用户权限不足
- 给 RAM 用户添加 `AliyunIOTFullAccess` 权限

## 参考文档

- 阿里云 IoT 平台官方文档：https://help.aliyun.com/product/30520.html
- LinkKit SDK 文档：https://help.aliyun.com/document_detail/96596.html
- 物模型说明：https://help.aliyun.com/document_detail/73727.html

## 联系方式

如有问题，请参考项目 docs 目录下的完整开发文档。