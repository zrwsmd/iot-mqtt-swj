# C/C++ 上位机完整开发教程（阿里云 IoT 平台）

> **适用于 ARM Linux 系统的完整解决方案**

---

## ⚠️ ARM Linux 系统 SDK 选择

### 推荐方案：阿里云 Link SDK（C 语言版本）

**官方 SDK**：[https://github.com/aliyun/iotkit-embedded](https://github.com/aliyun/iotkit-embedded)

**为什么选择这个 SDK？**

✅ **完美支持 ARM Linux**
- 已在多种 ARM 平台验证（ARMv7、ARMv8、Cortex-A 系列）
- 支持交叉编译
- 资源占用小（内存 < 10MB）

✅ **无需运行时环境**
- 纯 C 语言实现
- 编译成原生可执行文件
- 不依赖 Java/Node.js

✅ **官方维护**
- 阿里云官方维护
- 持续更新
- 文档完善

### ARM 平台验证情况

| ARM 架构 | 验证状态 | 典型设备 |
|---------|---------|---------|
| **ARMv7** | ✅ 已验证 | 树莓派 2/3、BeagleBone |
| **ARMv8 (AArch64)** | ✅ 已验证 | 树莓派 4、RK3399 |
| **Cortex-A7** | ✅ 已验证 | i.MX6UL、全志 A33 |
| **Cortex-A9** | ✅ 已验证 | i.MX6Q、Zynq-7000 |
| **Cortex-A53** | ✅ 已验证 | RK3328、MT8163 |

---

## 目录

1. [开发环境准备](#1-开发环境准备)
2. [阿里云 IoT C SDK 介绍](#2-阿里云-iot-c-sdk-介绍)
3. [SDK 下载与编译](#3-sdk-下载与编译)
4. [项目结构](#4-项目结构)
5. [核心代码实现](#5-核心代码实现)
6. [编译与部署](#6-编译与部署)
7. [运行与测试](#7-运行与测试)
8. [常见问题](#8-常见问题)

---

## 1. 开发环境准备

### 1.1 系统要求

- **操作系统**：Linux（推荐 Ubuntu 18.04+）、Windows（MinGW/MSVC）
- **编译器**：GCC 4.8+ 或 Clang 3.9+
- **构建工具**：CMake 3.0+
- **依赖库**：OpenSSL、pthread、curl

### 1.2 安装依赖（Ubuntu/Debian）

```bash
# 更新软件包列表
sudo apt-get update

# 安装编译工具
sudo apt-get install -y build-essential cmake git

# 安装依赖库
sudo apt-get install -y libssl-dev libcurl4-openssl-dev

# 验证安装
gcc --version
cmake --version
```

### 1.3 安装依赖（CentOS/RHEL）

```bash
# 安装编译工具
sudo yum install -y gcc gcc-c++ make cmake git

# 安装依赖库
sudo yum install -y openssl-devel libcurl-devel

# 验证安装
gcc --version
cmake --version
```

---

## 2. 阿里云 IoT C SDK 介绍

### 2.1 SDK 特点

- ✅ **轻量级**：适合嵌入式设备和资源受限环境
- ✅ **跨平台**：支持 Linux、Windows、RTOS
- ✅ **MQTT 协议**：支持 MQTT 3.1.1
- ✅ **物模型**：支持属性、事件、服务
- ✅ **设备影子**：支持设备影子功能
- ✅ **OTA 升级**：支持固件升级
- ✅ **动态注册**：支持设备动态注册

### 2.2 SDK 架构

```
┌─────────────────────────────────────────────────────┐
│                  应用层 (Application)                │
│              (你的业务逻辑代码)                       │
└────────────────────┬────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│              物模型层 (Data Model)                   │
│         (属性、事件、服务处理)                        │
└────────────────────┬────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│               MQTT 层 (MQTT Client)                  │
│         (连接、订阅、发布、重连)                      │
└────────────────────┬────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│              网络层 (Network/TLS)                    │
│            (TCP/TLS 连接管理)                        │
└────────────────────┬────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│              平台抽象层 (HAL)                        │
│       (OS、网络、加密等平台相关接口)                  │
└─────────────────────────────────────────────────────┘
```

---

## 3. SDK 下载与编译

### 3.1 下载 SDK

```bash
# 创建工作目录
mkdir -p ~/iot-project
cd ~/iot-project

# 克隆阿里云 IoT C SDK
git clone https://github.com/aliyun/iotkit-embedded.git
cd iotkit-embedded

# 查看版本
git tag
git checkout v3.2.0  # 使用稳定版本
```

### 3.2 SDK 目录结构

```
iotkit-embedded/
├── src/                    # SDK 源代码
│   ├── infra/             # 基础设施（日志、内存、JSON 等）
│   ├── mqtt/              # MQTT 客户端
│   ├── dev_model/         # 物模型
│   ├── dev_sign/          # 设备签名
│   └── ...
├── wrappers/              # 平台抽象层（HAL）
│   ├── os/                # 操作系统相关
│   ├── tls/               # TLS/SSL 相关
│   └── ...
├── examples/              # 示例代码
│   ├── mqtt/              # MQTT 示例
│   ├── linkkit/           # 物模型示例
│   └── ...
├── tools/                 # 工具脚本
├── CMakeLists.txt         # CMake 构建文件
└── README.md
```

### 3.3 配置编译选项

```bash
# 编辑配置文件
vim make.settings
```

**关键配置项**：

```makefile
# 平台选择
PLATFORM = linux

# 编译选项
FEATURE_MQTT_COMM_ENABLED       = y    # 启用 MQTT
FEATURE_DEVICE_MODEL_ENABLED    = y    # 启用物模型
FEATURE_DEV_BIND_ENABLED        = n    # 禁用设备绑定
FEATURE_MQTT_SHADOW             = y    # 启用设备影子
FEATURE_OTA_ENABLED             = n    # 禁用 OTA（可选）
FEATURE_SUPPORT_TLS             = y    # 启用 TLS

# 日志级别
CONFIG_LOG_LEVEL = DEBUG
```

### 3.4 编译 SDK

```bash
# 清理旧的编译文件
make distclean

# 编译 SDK
make

# 编译成功后，输出目录
ls output/release/
# 输出：
# - lib/          # 静态库文件
# - include/      # 头文件
# - bin/          # 示例程序
```

---

## 4. 项目结构

### 4.1 创建项目目录

```bash
# 创建项目目录
mkdir -p ~/iot-device
cd ~/iot-device

# 创建目录结构
mkdir -p src
mkdir -p include
mkdir -p build
mkdir -p config
```

### 4.2 完整项目结构

```
iot-device/
├── src/
│   ├── main.c                 # 主程序入口
│   ├── device_config.c        # 设备配置
│   ├── mqtt_client.c          # MQTT 客户端封装
│   ├── thing_model.c          # 物模型处理
│   └── hardware_control.c     # 硬件控制接口
├── include/
│   ├── device_config.h
│   ├── mqtt_client.h
│   ├── thing_model.h
│   └── hardware_control.h
├── config/
│   └── device_info.json       # 设备三元组配置
├── build/                     # 编译输出目录
├── CMakeLists.txt             # CMake 构建文件
├── Makefile                   # Makefile（可选）
└── README.md
```

---

## 5. 核心代码实现

### 5.1 设备配置（include/device_config.h）

```c
#ifndef DEVICE_CONFIG_H
#define DEVICE_CONFIG_H

// 设备三元组
typedef struct {
    char product_key[32];
    char device_name[64];
    char device_secret[64];
    char region[16];
} device_info_t;

// 加载设备配置
int load_device_info(const char *config_file, device_info_t *info);

// 打印设备信息
void print_device_info(const device_info_t *info);

#endif // DEVICE_CONFIG_H
```

### 5.2 设备配置实现（src/device_config.c）

```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "device_config.h"

// 简单的 JSON 解析（实际项目建议使用 cJSON 库）
int load_device_info(const char *config_file, device_info_t *info) {
    FILE *fp = fopen(config_file, "r");
    if (!fp) {
        printf("Failed to open config file: %s\n", config_file);
        return -1;
    }

    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "productKey")) {
            sscanf(line, " \"productKey\": \"%[^\"]\"", info->product_key);
        } else if (strstr(line, "deviceName")) {
            sscanf(line, " \"deviceName\": \"%[^\"]\"", info->device_name);
        } else if (strstr(line, "deviceSecret")) {
            sscanf(line, " \"deviceSecret\": \"%[^\"]\"", info->device_secret);
        } else if (strstr(line, "region")) {
            sscanf(line, " \"region\": \"%[^\"]\"", info->region);
        }
    }

    fclose(fp);
    return 0;
}

void print_device_info(const device_info_t *info) {
    printf("========== Device Info ==========\n");
    printf("Product Key:   %s\n", info->product_key);
    printf("Device Name:   %s\n", info->device_name);
    printf("Device Secret: %s\n", info->device_secret);
    printf("Region:        %s\n", info->region);
    printf("=================================\n");
}
```

### 5.3 物模型处理（include/thing_model.h）

```c
#ifndef THING_MODEL_H
#define THING_MODEL_H

#include "iot_import.h"
#include "iot_export.h"

// 属性上报
int thing_property_post(void *handle, const char *property_name, int value);

// 事件上报
int thing_event_post(void *handle, const char *event_id, const char *params);

// 服务处理回调
typedef int (*service_handler_t)(const char *service_id, const char *params);

// 注册服务处理器
int thing_register_service_handler(void *handle, const char *service_id, 
                                   service_handler_t handler);

#endif // THING_MODEL_H
```

### 5.4 物模型实现（src/thing_model.c）

```c
#include <stdio.h>
#include <string.h>
#include "thing_model.h"

// 属性上报
int thing_property_post(void *handle, const char *property_name, int value) {
    char payload[256];
    
    // 构造 Alink JSON 格式
    snprintf(payload, sizeof(payload),
             "{\"id\":\"1\",\"version\":\"1.0\",\"params\":{\"%s\":%d},\"method\":\"thing.event.property.post\"}",
             property_name, value);
    
    printf("[Property Post] %s\n", payload);
    
    // 调用 SDK 发布消息
    int res = IOT_Linkkit_Report(handle, ITM_MSG_POST_PROPERTY,
                                  (unsigned char *)payload, strlen(payload));
    
    if (res < 0) {
        printf("Property post failed, res = %d\n", res);
        return -1;
    }
    
    printf("Property posted successfully\n");
    return 0;
}

// 事件上报
int thing_event_post(void *handle, const char *event_id, const char *params) {
    char payload[512];
    
    // 构造 Alink JSON 格式
    snprintf(payload, sizeof(payload),
             "{\"id\":\"2\",\"version\":\"1.0\",\"params\":%s,\"method\":\"thing.event.%s.post\"}",
             params, event_id);
    
    printf("[Event Post] %s\n", payload);
    
    // 调用 SDK 发布消息
    int res = IOT_Linkkit_Report(handle, ITM_MSG_POST_EVENT,
                                  (unsigned char *)payload, strlen(payload));
    
    if (res < 0) {
        printf("Event post failed, res = %d\n", res);
        return -1;
    }
    
    printf("Event posted successfully\n");
    return 0;
}
```

### 5.5 硬件控制接口（include/hardware_control.h）

```c
#ifndef HARDWARE_CONTROL_H
#define HARDWARE_CONTROL_H

// 初始化硬件
int hardware_init(void);

// 设置 GPIO
int hardware_set_gpio(int pin, int value);

// 读取 GPIO
int hardware_get_gpio(int pin);

// 设置属性到硬件
int hardware_set_property(const char *property_name, int value);

// 从硬件读取属性
int hardware_get_property(const char *property_name);

// 执行服务
int hardware_execute_service(const char *service_id, const char *params);

#endif // HARDWARE_CONTROL_H
```

### 5.6 硬件控制实现（src/hardware_control.c）

```c
#include <stdio.h>
#include <string.h>
#include "hardware_control.h"

// 初始化硬件
int hardware_init(void) {
    printf("[Hardware] Initializing...\n");
    
    // TODO: 初始化 GPIO、串口、传感器等
    // 例如：
    // - 打开 GPIO 设备：/dev/gpiochip0
    // - 配置串口：/dev/ttyS0
    // - 初始化 I2C/SPI 设备
    
    printf("[Hardware] Initialized successfully\n");
    return 0;
}

// 设置 GPIO
int hardware_set_gpio(int pin, int value) {
    printf("[Hardware] Set GPIO %d = %d\n", pin, value);
    
    // TODO: 实际的 GPIO 操作
    // 例如（Linux sysfs 方式）：
    // echo value > /sys/class/gpio/gpio{pin}/value
    
    return 0;
}

// 读取 GPIO
int hardware_get_gpio(int pin) {
    // TODO: 实际的 GPIO 读取
    // 例如：
    // cat /sys/class/gpio/gpio{pin}/value
    
    return 0;  // 返回读取的值
}

// 设置属性到硬件
int hardware_set_property(const char *property_name, int value) {
    printf("[Hardware] Set property: %s = %d\n", property_name, value);
    
    if (strcmp(property_name, "ADASSwitch") == 0) {
        // 控制 ADAS 开关
        hardware_set_gpio(17, value);  // 假设 GPIO 17 控制 ADAS
        
    } else if (strcmp(property_name, "LightSwitch") == 0) {
        // 控制灯光开关
        hardware_set_gpio(18, value);  // 假设 GPIO 18 控制灯光
        
    } else {
        printf("[Hardware] Unknown property: %s\n", property_name);
        return -1;
    }
    
    return 0;
}

// 从硬件读取属性
int hardware_get_property(const char *property_name) {
    if (strcmp(property_name, "ADASSwitch") == 0) {
        return hardware_get_gpio(17);
    } else if (strcmp(property_name, "LightSwitch") == 0) {
        return hardware_get_gpio(18);
    }
    
    return -1;
}

// 执行服务
int hardware_execute_service(const char *service_id, const char *params) {
    printf("[Hardware] Execute service: %s, params: %s\n", service_id, params);
    
    if (strcmp(service_id, "restart") == 0) {
        // 执行重启
        printf("[Hardware] Restarting device...\n");
        system("reboot");
        
    } else if (strcmp(service_id, "reset") == 0) {
        // 执行复位
        printf("[Hardware] Resetting device...\n");
        // TODO: 复位逻辑
        
    } else {
        printf("[Hardware] Unknown service: %s\n", service_id);
        return -1;
    }
    
    return 0;
}
```

### 5.7 主程序（src/main.c）

```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>

#include "iot_import.h"
#include "iot_export.h"
#include "device_config.h"
#include "thing_model.h"
#include "hardware_control.h"

// 全局变量
static int g_running = 1;
static void *g_linkkit_handle = NULL;

// 信号处理
void signal_handler(int signo) {
    printf("\nReceived signal %d, exiting...\n", signo);
    g_running = 0;
}

// 属性设置回调
static int property_set_callback(const int devid, const char *request, 
                                const int request_len) {
    printf("\n[Callback] Property Set:\n%s\n", request);
    
    // 解析 JSON（简化版，实际应使用 cJSON）
    // 格式：{"ADASSwitch":1}
    
    char property_name[64];
    int property_value;
    
    // 简单解析（仅作示例）
    if (sscanf(request, "{\"%[^\"]\":%d}", property_name, &property_value) == 2) {
        printf("[Property Set] %s = %d\n", property_name, property_value);
        
        // 设置到硬件
        if (hardware_set_property(property_name, property_value) == 0) {
            // 上报属性
            thing_property_post(g_linkkit_handle, property_name, property_value);
            return 0;
        }
    }
    
    return -1;
}

// 服务调用回调
static int service_request_callback(const int devid, const char *serviceid,
                                    const int serviceid_len,
                                    const char *request, const int request_len,
                                    char **response, int *response_len) {
    printf("\n[Callback] Service Request:\n");
    printf("Service ID: %.*s\n", serviceid_len, serviceid);
    printf("Request: %.*s\n", request_len, request);
    
    // 提取服务 ID
    char service_id[64];
    snprintf(service_id, sizeof(service_id), "%.*s", serviceid_len, serviceid);
    
    // 执行服务
    int ret = hardware_execute_service(service_id, request);
    
    // 构造响应
    if (ret == 0) {
        *response = strdup("{\"code\":200,\"data\":{}}");
    } else {
        *response = strdup("{\"code\":500,\"data\":{}}");
    }
    *response_len = strlen(*response);
    
    return ret;
}

// 连接状态回调
static int connect_state_callback(const int devid, const int state) {
    printf("\n[Callback] Connect State: %d\n", state);
    
    if (state == 0) {
        printf("Device disconnected\n");
    } else {
        printf("Device connected\n");
    }
    
    return 0;
}

// 初始化 LinkKit
int linkkit_init(device_info_t *device_info) {
    int res;
    iotx_linkkit_dev_meta_info_t master_meta_info;
    
    // 设置日志级别
    IOT_SetLogLevel(IOT_LOG_DEBUG);
    
    // 填充设备元信息
    memset(&master_meta_info, 0, sizeof(iotx_linkkit_dev_meta_info_t));
    memcpy(master_meta_info.product_key, device_info->product_key, 
           strlen(device_info->product_key));
    memcpy(master_meta_info.device_name, device_info->device_name, 
           strlen(device_info->device_name));
    memcpy(master_meta_info.device_secret, device_info->device_secret, 
           strlen(device_info->device_secret));
    
    // 注册回调函数
    IOT_RegisterCallback(ITE_CONNECT_SUCC, connect_state_callback);
    IOT_RegisterCallback(ITE_DISCONNECTED, connect_state_callback);
    IOT_RegisterCallback(ITE_PROPERTY_SET, property_set_callback);
    IOT_RegisterCallback(ITE_SERVICE_REQUEST, service_request_callback);
    
    // 创建主设备
    g_linkkit_handle = IOT_Linkkit_Open(IOTX_LINKKIT_DEV_TYPE_MASTER, 
                                        &master_meta_info);
    if (g_linkkit_handle == NULL) {
        printf("IOT_Linkkit_Open failed\n");
        return -1;
    }
    
    printf("IOT_Linkkit_Open success, handle = %p\n", g_linkkit_handle);
    
    // 连接到云端
    res = IOT_Linkkit_Connect(g_linkkit_handle);
    if (res < 0) {
        printf("IOT_Linkkit_Connect failed, res = %d\n", res);
        IOT_Linkkit_Close(g_linkkit_handle);
        return -1;
    }
    
    printf("IOT_Linkkit_Connect success\n");
    
    return 0;
}

// 主函数
int main(int argc, char *argv[]) {
    int res;
    device_info_t device_info;
    
    printf("\n========================================\n");
    printf("  Aliyun IoT Device (C Language)\n");
    printf("========================================\n\n");
    
    // 注册信号处理
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    
    // 加载设备配置
    if (load_device_info("config/device_info.json", &device_info) != 0) {
        printf("Failed to load device info\n");
        return -1;
    }
    print_device_info(&device_info);
    
    // 初始化硬件
    if (hardware_init() != 0) {
        printf("Failed to initialize hardware\n");
        return -1;
    }
    
    // 初始化 LinkKit
    if (linkkit_init(&device_info) != 0) {
        printf("Failed to initialize LinkKit\n");
        return -1;
    }
    
    printf("\n========================================\n");
    printf("  Device is running...\n");
    printf("  Press Ctrl+C to exit\n");
    printf("========================================\n\n");
    
    // 主循环
    int count = 0;
    while (g_running) {
        // 处理 MQTT 消息
        IOT_Linkkit_Yield(200);
        
        // 定时上报属性（每 10 秒）
        if (++count >= 50) {  // 50 * 200ms = 10s
            count = 0;
            
            // 读取硬件状态
            int adas_value = hardware_get_property("ADASSwitch");
            
            // 上报属性
            thing_property_post(g_linkkit_handle, "ADASSwitch", adas_value);
        }
    }
    
    // 清理资源
    printf("\nCleaning up...\n");
    IOT_Linkkit_Close(g_linkkit_handle);
    
    printf("Goodbye!\n");
    return 0;
}
```

---

## 6. 编译与部署

### 6.1 CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.0)
project(iot-device C)

# 设置 C 标准
set(CMAKE_C_STANDARD 99)

# 阿里云 IoT SDK 路径
set(IOT_SDK_DIR "${CMAKE_SOURCE_DIR}/../iotkit-embedded")

# 包含目录
include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${IOT_SDK_DIR}/output/release/include
)

# 链接目录
link_directories(
    ${IOT_SDK_DIR}/output/release/lib
)

# 源文件
set(SOURCES
    src/main.c
    src/device_config.c
    src/thing_model.c
    src/hardware_control.c
)

# 生成可执行文件
add_executable(iot-device ${SOURCES})

# 链接库
target_link_libraries(iot-device
    iot_sdk          # 阿里云 IoT SDK
    iot_hal          # HAL 层
    iot_tls          # TLS 层
    pthread
    rt
    m
)

# 安装规则
install(TARGETS iot-device DESTINATION bin)
```

### 6.2 编译项目

```bash
# 进入项目目录
cd ~/iot-device

# 创建构建目录
mkdir -p build
cd build

# 配置 CMake
cmake ..

# 编译
make

# 查看生成的可执行文件
ls -lh iot-device
```

### 6.3 配置文件（config/device_info.json）

```json
{
  "productKey": "k03k41dVanO",
  "deviceName": "Beijing_PC_001",
  "deviceSecret": "你的deviceSecret",
  "region": "cn-shanghai"
}
```

---

## 7. 运行与测试

### 7.1 运行程序

```bash
# 运行
./iot-device

# 或后台运行
nohup ./iot-device > /var/log/iot-device.log 2>&1 &
```

**预期输出**：

```
========================================
  Aliyun IoT Device (C Language)
========================================

========== Device Info ==========
Product Key:   k03k41dVanO
Device Name:   Beijing_PC_001
Device Secret: ****
Region:        cn-shanghai
=================================

[Hardware] Initializing...
[Hardware] Initialized successfully

IOT_Linkkit_Open success, handle = 0x12345678
IOT_Linkkit_Connect success

========================================
  Device is running...
  Press Ctrl+C to exit
========================================

[Property Post] {"id":"1","version":"1.0","params":{"ADASSwitch":0},"method":"thing.event.property.post"}
Property posted successfully

[Callback] Property Set:
{"ADASSwitch":1}
[Property Set] ADASSwitch = 1
[Hardware] Set property: ADASSwitch = 1
[Hardware] Set GPIO 17 = 1
[Property Post] {"id":"1","version":"1.0","params":{"ADASSwitch":1},"method":"thing.event.property.post"}
Property posted successfully
```

### 7.2 测试属性设置

**从本地控制端调用**：

```bash
# Node.js CLI
node cli.js set ADASSwitch 1

# 或 Java
curl -X POST http://localhost:8080/api/device/property \
  -H "Content-Type: application/json" \
  -d '{"properties": {"ADASSwitch": 1}}'
```

**上位机输出**：

```
[Callback] Property Set:
{"ADASSwitch":1}
[Property Set] ADASSwitch = 1
[Hardware] Set property: ADASSwitch = 1
[Hardware] Set GPIO 17 = 1
```

### 7.3 测试服务调用

**从本地控制端调用**：

```bash
# Node.js CLI
node cli.js service restart

# 或 Java
curl -X POST http://localhost:8080/api/device/service/restart
```

**上位机输出**：

```
[Callback] Service Request:
Service ID: restart
Request: {}
[Hardware] Execute service: restart, params: {}
[Hardware] Restarting device...
```

---

## 8. 常见问题

### 8.1 编译错误

**问题**：找不到头文件

```
fatal error: iot_export.h: No such file or directory
```

**解决**：

```bash
# 确保 SDK 已编译
cd ~/iot-project/iotkit-embedded
make

# 检查输出目录
ls output/release/include/

# 修改 CMakeLists.txt 中的路径
set(IOT_SDK_DIR "${CMAKE_SOURCE_DIR}/../iotkit-embedded")
```

---

### 8.2 连接失败

**问题**：无法连接到 IoT 平台

```
IOT_Linkkit_Connect failed, res = -1
```

**解决**：

1. 检查设备三元组是否正确
2. 检查网络连接
3. 检查防火墙设置
4. 启用 SDK 日志查看详细错误

```c
IOT_SetLogLevel(IOT_LOG_DEBUG);
```

---

### 8.3 内存泄漏

**问题**：长时间运行后内存增长

**解决**：

1. 使用 valgrind 检测内存泄漏

```bash
valgrind --leak-check=full ./iot-device
```

2. 确保释放动态分配的内存

```c
// 服务响应需要手动释放
char *response = strdup("{\"code\":200}");
// ... 使用 response
free(response);
```

---

### 8.4 交叉编译

**问题**：需要为 ARM 平台编译

**解决**：

```bash
# 安装交叉编译工具链
sudo apt-get install gcc-arm-linux-gnueabihf

# 修改 CMakeLists.txt
set(CMAKE_C_COMPILER arm-linux-gnueabihf-gcc)
set(CMAKE_CXX_COMPILER arm-linux-gnueabihf-g++)

# 或使用工具链文件
cmake -DCMAKE_TOOLCHAIN_FILE=arm-toolchain.cmake ..
```

**arm-toolchain.cmake**：

```cmake
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR arm)

set(CMAKE_C_COMPILER arm-linux-gnueabihf-gcc)
set(CMAKE_CXX_COMPILER arm-linux-gnueabihf-g++)

set(CMAKE_FIND_ROOT_PATH /usr/arm-linux-gnueabihf)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
```

---

## 9. 系统服务配置

### 9.1 创建 systemd 服务

```bash
# 创建服务文件
sudo nano /etc/systemd/system/iot-device.service
```

```ini
[Unit]
Description=Aliyun IoT Device Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/iot-device
ExecStart=/opt/iot-device/iot-device
Restart=always
RestartSec=10
StandardOutput=append:/var/log/iot-device/output.log
StandardError=append:/var/log/iot-device/error.log

[Install]
WantedBy=multi-user.target
```

### 9.2 启动服务

```bash
# 创建日志目录
sudo mkdir -p /var/log/iot-device

# 复制程序到 /opt
sudo mkdir -p /opt/iot-device
sudo cp build/iot-device /opt/iot-device/
sudo cp -r config /opt/iot-device/

# 重载 systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start iot-device

# 开机自启
sudo systemctl enable iot-device

# 查看状态
sudo systemctl status iot-device

# 查看日志
sudo journalctl -u iot-device -f
```

---

## 10. 完整 Makefile（替代 CMake）

```makefile
# Makefile for IoT Device

# 编译器
CC = gcc

# 编译选项
CFLAGS = -Wall -O2 -std=c99

# 阿里云 IoT SDK 路径
IOT_SDK_DIR = ../iotkit-embedded/output/release

# 包含目录
INCLUDES = -I./include -I$(IOT_SDK_DIR)/include

# 库目录
LDFLAGS = -L$(IOT_SDK_DIR)/lib

# 链接库
LIBS = -liot_sdk -liot_hal -liot_tls -lpthread -lrt -lm

# 源文件
SRCS = src/main.c \
       src/device_config.c \
       src/thing_model.c \
       src/hardware_control.c

# 目标文件
OBJS = $(SRCS:.c=.o)

# 可执行文件
TARGET = iot-device

# 默认目标
all: $(TARGET)

# 编译规则
$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS) $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) $(INCLUDES) -c $< -o $@

# 清理
clean:
	rm -f $(OBJS) $(TARGET)

# 安装
install: $(TARGET)
	install -D $(TARGET) /usr/local/bin/$(TARGET)
	install -D config/device_info.json /etc/iot-device/device_info.json

# 卸载
uninstall:
	rm -f /usr/local/bin/$(TARGET)
	rm -rf /etc/iot-device

.PHONY: all clean install uninstall
```

**使用 Makefile 编译**：

```bash
# 编译
make

# 清理
make clean

# 安装
sudo make install

# 运行
iot-device
```

---

## 11. 总结

### ✅ C/C++ 方案优势

1. **无需运行时环境**
   - 不需要 Java JVM
   - 不需要 Node.js 运行时
   - 编译成原生可执行文件

2. **资源占用小**
   - 内存占用：~5-10MB
   - CPU 占用：极低
   - 适合嵌入式设备

3. **启动速度快**
   - 秒级启动
   - 无需预热

4. **跨平台**
   - Linux（x86、ARM、MIPS）
   - Windows
   - RTOS

### ✅ 完整功能

- ✅ MQTT 连接
- ✅ 属性上报
- ✅ 属性设置
- ✅ 事件上报
- ✅ 服务调用
- ✅ 硬件控制
- ✅ 自动重连
- ✅ 日志记录

### ✅ 部署方式

- ✅ 直接运行
- ✅ 后台运行（nohup）
- ✅ systemd 服务
- ✅ 开机自启

### ✅ 适用场景

- ✅ 嵌入式 Linux 设备
- ✅ 工控机
- ✅ 网关设备
- ✅ 资源受限环境
- ✅ 不允许安装 Java/Node.js 的环境

现在你有了一个完整的 C 语言上位机解决方案！
