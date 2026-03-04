# ARM Linux 交叉编译完整指南

---

## 目录

1. [ARM Linux 系统 SDK 选择](#1-arm-linux-系统-sdk-选择)
2. [交叉编译环境搭建](#2-交叉编译环境搭建)
3. [SDK 交叉编译](#3-sdk-交叉编译)
4. [项目交叉编译](#4-项目交叉编译)
5. [部署到 ARM 设备](#5-部署到-arm-设备)
6. [常见 ARM 平台适配](#6-常见-arm-平台适配)

---

## 1. ARM Linux 系统 SDK 选择

### 1.1 推荐 SDK：阿里云 Link SDK (C 语言版本)

**官方仓库**：[https://github.com/aliyun/iotkit-embedded](https://github.com/aliyun/iotkit-embedded)

**版本选择**：
- **稳定版本**：v3.2.0（推荐）
- **最新版本**：v4.x（功能更多，但可能不稳定）

### 1.2 为什么适合 ARM Linux？

✅ **完美支持 ARM 架构**
```
已验证平台：
- ARMv7 (32-bit)：树莓派 2/3、BeagleBone Black
- ARMv8 (64-bit)：树莓派 4、RK3399、Jetson Nano
- Cortex-A 系列：i.MX6、全志、瑞芯微等
```

✅ **资源占用极小**
```
内存占用：< 10MB
存储占用：< 5MB
CPU 占用：< 5%
```

✅ **无需运行时环境**
```
❌ 不需要 Java JVM
❌ 不需要 Node.js
❌ 不需要 Python
✅ 编译成原生 ARM 可执行文件
```

### 1.3 SDK 功能特性

| 功能 | 支持情况 | 说明 |
|------|---------|------|
| **MQTT 连接** | ✅ | 支持 TLS 加密 |
| **物模型** | ✅ | 属性、事件、服务 |
| **设备影子** | ✅ | 离线消息缓存 |
| **OTA 升级** | ✅ | 固件远程升级 |
| **动态注册** | ✅ | 自动获取 deviceSecret |
| **子设备管理** | ✅ | 网关场景 |
| **本地通信** | ✅ | 局域网发现 |

---

## 2. 交叉编译环境搭建

### 2.1 确定 ARM 架构

**在 ARM 设备上执行**：

```bash
# 查看 CPU 架构
uname -m
# 输出示例：
# - armv7l        → ARMv7 (32-bit)
# - aarch64       → ARMv8 (64-bit)

# 查看详细 CPU 信息
cat /proc/cpuinfo | grep "model name"

# 查看系统信息
cat /etc/os-release
```

### 2.2 安装交叉编译工具链

#### 方式一：使用系统包管理器（推荐）

**Ubuntu/Debian（开发机）**：

```bash
# ARMv7 (32-bit) 工具链
sudo apt-get update
sudo apt-get install -y gcc-arm-linux-gnueabihf g++-arm-linux-gnueabihf

# ARMv8 (64-bit) 工具链
sudo apt-get install -y gcc-aarch64-linux-gnu g++-aarch64-linux-gnu

# 验证安装
arm-linux-gnueabihf-gcc --version
aarch64-linux-gnu-gcc --version
```

#### 方式二：使用 Linaro 工具链

```bash
# 下载 Linaro 工具链
cd ~/Downloads

# ARMv7 工具链
wget https://releases.linaro.org/components/toolchain/binaries/7.5-2019.12/arm-linux-gnueabihf/gcc-linaro-7.5.0-2019.12-x86_64_arm-linux-gnueabihf.tar.xz

# ARMv8 工具链
wget https://releases.linaro.org/components/toolchain/binaries/7.5-2019.12/aarch64-linux-gnu/gcc-linaro-7.5.0-2019.12-x86_64_aarch64-linux-gnu.tar.xz

# 解压
tar xf gcc-linaro-7.5.0-2019.12-x86_64_arm-linux-gnueabihf.tar.xz
tar xf gcc-linaro-7.5.0-2019.12-x86_64_aarch64-linux-gnu.tar.xz

# 移动到 /opt
sudo mv gcc-linaro-7.5.0-2019.12-x86_64_arm-linux-gnueabihf /opt/
sudo mv gcc-linaro-7.5.0-2019.12-x86_64_aarch64-linux-gnu /opt/

# 添加到 PATH
echo 'export PATH=/opt/gcc-linaro-7.5.0-2019.12-x86_64_arm-linux-gnueabihf/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 验证
arm-linux-gnueabihf-gcc --version
```

#### 方式三：使用设备厂商提供的工具链

```bash
# 例如：瑞芯微 RK3399
# 从厂商获取工具链：gcc-linaro-6.3.1-2017.05-x86_64_aarch64-linux-gnu

# 全志 A33
# 从厂商获取工具链：arm-linux-gnueabi-gcc
```

---

## 3. SDK 交叉编译

### 3.1 下载 SDK

```bash
# 创建工作目录
mkdir -p ~/arm-iot-project
cd ~/arm-iot-project

# 克隆 SDK
git clone https://github.com/aliyun/iotkit-embedded.git
cd iotkit-embedded

# 切换到稳定版本
git checkout v3.2.0
```

### 3.2 配置交叉编译

**编辑配置文件**：

```bash
vim make.settings
```

**关键配置**：

```makefile
# ===== 平台配置 =====
# 修改为 arm-linux
PLATFORM = arm-linux

# ===== 交叉编译工具链 =====
# ARMv7 (32-bit)
OVERRIDE_CC = arm-linux-gnueabihf-gcc
OVERRIDE_AR = arm-linux-gnueabihf-ar
OVERRIDE_LD = arm-linux-gnueabihf-ld

# 或 ARMv8 (64-bit)
# OVERRIDE_CC = aarch64-linux-gnu-gcc
# OVERRIDE_AR = aarch64-linux-gnu-ar
# OVERRIDE_LD = aarch64-linux-gnu-ld

# ===== 功能开关 =====
FEATURE_MQTT_COMM_ENABLED       = y    # MQTT 通信
FEATURE_DEVICE_MODEL_ENABLED    = y    # 物模型
FEATURE_MQTT_SHADOW             = y    # 设备影子
FEATURE_OTA_ENABLED             = n    # OTA（可选）
FEATURE_SUPPORT_TLS             = y    # TLS 加密

# ===== 编译选项 =====
CONFIG_LOG_LEVEL = DEBUG
```

### 3.3 编译 SDK

```bash
# 清理旧的编译文件
make distclean

# 开始编译
make

# 编译成功后查看输出
ls -lh output/release/
```

**输出目录结构**：

```
output/release/
├── lib/
│   ├── libiot_sdk.a          # SDK 静态库
│   ├── libiot_hal.a          # HAL 层库
│   └── libiot_tls.a          # TLS 层库
├── include/
│   ├── iot_export.h          # 导出头文件
│   ├── iot_import.h
│   └── ...
└── bin/
    ├── linkkit-example-solo  # 示例程序（ARM 可执行文件）
    └── mqtt-example
```

### 3.4 验证编译结果

```bash
# 查看可执行文件架构
file output/release/bin/linkkit-example-solo

# 预期输出（ARMv7）：
# linkkit-example-solo: ELF 32-bit LSB executable, ARM, EABI5 version 1 (SYSV), dynamically linked, ...

# 预期输出（ARMv8）：
# linkkit-example-solo: ELF 64-bit LSB executable, ARM aarch64, version 1 (SYSV), dynamically linked, ...
```

---

## 4. 项目交叉编译

### 4.1 创建 CMake 工具链文件

**arm-toolchain.cmake**（ARMv7）：

```cmake
# ARM Linux 交叉编译工具链文件

set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR arm)

# 工具链路径
set(TOOLCHAIN_PREFIX arm-linux-gnueabihf)

# 编译器
set(CMAKE_C_COMPILER ${TOOLCHAIN_PREFIX}-gcc)
set(CMAKE_CXX_COMPILER ${TOOLCHAIN_PREFIX}-g++)
set(CMAKE_AR ${TOOLCHAIN_PREFIX}-ar)
set(CMAKE_RANLIB ${TOOLCHAIN_PREFIX}-ranlib)

# 查找路径
set(CMAKE_FIND_ROOT_PATH /usr/${TOOLCHAIN_PREFIX})
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)

# 编译选项
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=hard")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=hard")
```

**aarch64-toolchain.cmake**（ARMv8）：

```cmake
# ARM64 Linux 交叉编译工具链文件

set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)

# 工具链路径
set(TOOLCHAIN_PREFIX aarch64-linux-gnu)

# 编译器
set(CMAKE_C_COMPILER ${TOOLCHAIN_PREFIX}-gcc)
set(CMAKE_CXX_COMPILER ${TOOLCHAIN_PREFIX}-g++)
set(CMAKE_AR ${TOOLCHAIN_PREFIX}-ar)
set(CMAKE_RANLIB ${TOOLCHAIN_PREFIX}-ranlib)

# 查找路径
set(CMAKE_FIND_ROOT_PATH /usr/${TOOLCHAIN_PREFIX})
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
```

### 4.2 修改项目 CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.0)
project(iot-device C)

# 设置 C 标准
set(CMAKE_C_STANDARD 99)

# 阿里云 IoT SDK 路径
set(IOT_SDK_DIR "${CMAKE_SOURCE_DIR}/../iotkit-embedded/output/release")

# 包含目录
include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${IOT_SDK_DIR}/include
)

# 链接目录
link_directories(
    ${IOT_SDK_DIR}/lib
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
    iot_sdk
    iot_hal
    iot_tls
    pthread
    rt
    m
    dl
)

# 安装规则
install(TARGETS iot-device DESTINATION bin)
```

### 4.3 交叉编译项目

```bash
# 进入项目目录
cd ~/arm-iot-project/iot-device

# 创建构建目录
mkdir -p build-arm
cd build-arm

# 使用工具链文件配置 CMake（ARMv7）
cmake -DCMAKE_TOOLCHAIN_FILE=../arm-toolchain.cmake ..

# 或 ARMv8
# cmake -DCMAKE_TOOLCHAIN_FILE=../aarch64-toolchain.cmake ..

# 编译
make

# 查看生成的可执行文件
file iot-device
# 输出：iot-device: ELF 32-bit LSB executable, ARM, EABI5 version 1 (SYSV), ...
```

### 4.4 使用 Makefile 交叉编译

```makefile
# Makefile for ARM cross-compilation

# 交叉编译工具链
# ARMv7
CROSS_COMPILE = arm-linux-gnueabihf-
# ARMv8
# CROSS_COMPILE = aarch64-linux-gnu-

CC = $(CROSS_COMPILE)gcc
AR = $(CROSS_COMPILE)ar
LD = $(CROSS_COMPILE)ld

# 编译选项
CFLAGS = -Wall -O2 -std=c99
# ARMv7 特定选项
CFLAGS += -march=armv7-a -mfpu=neon -mfloat-abi=hard

# SDK 路径
IOT_SDK_DIR = ../iotkit-embedded/output/release

# 包含目录
INCLUDES = -I./include -I$(IOT_SDK_DIR)/include

# 库目录
LDFLAGS = -L$(IOT_SDK_DIR)/lib

# 链接库
LIBS = -liot_sdk -liot_hal -liot_tls -lpthread -lrt -lm -ldl

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

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS) $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) $(INCLUDES) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)

.PHONY: all clean
```

**编译**：

```bash
make
```

---

## 5. 部署到 ARM 设备

### 5.1 传输文件到 ARM 设备

```bash
# 使用 scp 传输
scp build-arm/iot-device root@192.168.1.100:/opt/iot-device/
scp config/device_info.json root@192.168.1.100:/opt/iot-device/

# 或使用 U 盘、SD 卡等
```

### 5.2 检查依赖库

**在 ARM 设备上执行**：

```bash
# 查看依赖库
ldd /opt/iot-device/iot-device

# 预期输出：
# libpthread.so.0 => /lib/arm-linux-gnueabihf/libpthread.so.0
# librt.so.1 => /lib/arm-linux-gnueabihf/librt.so.1
# libm.so.6 => /lib/arm-linux-gnueabihf/libm.so.6
# libc.so.6 => /lib/arm-linux-gnueabihf/libc.so.6
# ...

# 如果缺少库，安装
sudo apt-get install -y libc6 libpthread-stubs0-dev
```

### 5.3 运行程序

```bash
# 添加执行权限
chmod +x /opt/iot-device/iot-device

# 运行
cd /opt/iot-device
./iot-device

# 或后台运行
nohup ./iot-device > /var/log/iot-device.log 2>&1 &
```

### 5.4 配置开机自启

**创建 systemd 服务**：

```bash
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

**启动服务**：

```bash
sudo systemctl daemon-reload
sudo systemctl start iot-device
sudo systemctl enable iot-device
sudo systemctl status iot-device
```

---

## 6. 常见 ARM 平台适配

### 6.1 树莓派（Raspberry Pi）

**架构**：
- 树莓派 2/3：ARMv7 (32-bit)
- 树莓派 4：ARMv8 (64-bit)

**工具链**：

```bash
# 树莓派 2/3
sudo apt-get install gcc-arm-linux-gnueabihf

# 树莓派 4（64-bit OS）
sudo apt-get install gcc-aarch64-linux-gnu
```

**编译选项**：

```makefile
# 树莓派 2/3
CFLAGS += -march=armv7-a -mfpu=neon-vfpv4 -mfloat-abi=hard

# 树莓派 4
CFLAGS += -march=armv8-a
```

---

### 6.2 瑞芯微（Rockchip）

**常见型号**：
- RK3288：ARMv7 Cortex-A17
- RK3328：ARMv8 Cortex-A53
- RK3399：ARMv8 Cortex-A72 + A53

**工具链**：

```bash
# RK3288 (ARMv7)
CROSS_COMPILE = arm-linux-gnueabihf-

# RK3328/RK3399 (ARMv8)
CROSS_COMPILE = aarch64-linux-gnu-
```

**编译选项**：

```makefile
# RK3288
CFLAGS += -march=armv7-a -mtune=cortex-a17 -mfpu=neon-vfpv4

# RK3399
CFLAGS += -march=armv8-a -mtune=cortex-a72.cortex-a53
```

---

### 6.3 全志（Allwinner）

**常见型号**：
- A33：ARMv7 Cortex-A7
- H3：ARMv7 Cortex-A7
- H6：ARMv8 Cortex-A53

**工具链**：

```bash
# A33/H3 (ARMv7)
CROSS_COMPILE = arm-linux-gnueabihf-

# H6 (ARMv8)
CROSS_COMPILE = aarch64-linux-gnu-
```

**编译选项**：

```makefile
# A33/H3
CFLAGS += -march=armv7-a -mtune=cortex-a7 -mfpu=neon-vfpv4

# H6
CFLAGS += -march=armv8-a -mtune=cortex-a53
```

---

### 6.4 NXP i.MX 系列

**常见型号**：
- i.MX6Q：ARMv7 Cortex-A9
- i.MX6UL：ARMv7 Cortex-A7
- i.MX8M：ARMv8 Cortex-A53

**工具链**：

```bash
# i.MX6 系列 (ARMv7)
CROSS_COMPILE = arm-linux-gnueabihf-

# i.MX8 系列 (ARMv8)
CROSS_COMPILE = aarch64-linux-gnu-
```

**编译选项**：

```makefile
# i.MX6Q
CFLAGS += -march=armv7-a -mtune=cortex-a9 -mfpu=neon

# i.MX6UL
CFLAGS += -march=armv7-a -mtune=cortex-a7 -mfpu=neon-vfpv4

# i.MX8M
CFLAGS += -march=armv8-a -mtune=cortex-a53
```

---

## 7. 常见问题

### 7.1 编译错误：找不到头文件

**错误**：

```
fatal error: openssl/ssl.h: No such file or directory
```

**解决**：

```bash
# 需要交叉编译 OpenSSL
# 或使用静态链接

# 下载 OpenSSL
wget https://www.openssl.org/source/openssl-1.1.1k.tar.gz
tar xf openssl-1.1.1k.tar.gz
cd openssl-1.1.1k

# 配置（ARMv7）
./Configure linux-armv4 --prefix=/opt/arm-openssl \
    --cross-compile-prefix=arm-linux-gnueabihf-

# 编译安装
make
make install

# 在 CMakeLists.txt 中添加
include_directories(/opt/arm-openssl/include)
link_directories(/opt/arm-openssl/lib)
```

---

### 7.2 运行错误：Illegal instruction

**错误**：

```
Illegal instruction (core dumped)
```

**原因**：编译选项与目标 CPU 不匹配

**解决**：

```makefile
# 使用更通用的编译选项
# 不要指定 -march=armv8-a（如果设备是 ARMv7）

# 安全的选项（ARMv7）
CFLAGS += -march=armv7-a -mfloat-abi=hard

# 或完全不指定 -march
CFLAGS += -O2 -Wall
```

---

### 7.3 运行错误：找不到共享库

**错误**：

```
error while loading shared libraries: libiot_sdk.so: cannot open shared object file
```

**解决**：

```bash
# 方式一：使用静态链接（推荐）
# 在 CMakeLists.txt 中
target_link_libraries(iot-device
    ${IOT_SDK_DIR}/lib/libiot_sdk.a  # 使用 .a 而不是 .so
    ...
)

# 方式二：复制共享库到设备
scp ../iotkit-embedded/output/release/lib/*.so root@192.168.1.100:/usr/lib/

# 方式三：设置 LD_LIBRARY_PATH
export LD_LIBRARY_PATH=/opt/iot-device/lib:$LD_LIBRARY_PATH
```

---

## 8. 完整示例：树莓派 3 部署

### 8.1 开发机编译

```bash
# 1. 安装工具链
sudo apt-get install gcc-arm-linux-gnueabihf

# 2. 编译 SDK
cd ~/iotkit-embedded
vim make.settings
# 设置：PLATFORM = arm-linux
#      OVERRIDE_CC = arm-linux-gnueabihf-gcc
make

# 3. 编译项目
cd ~/iot-device
mkdir build-arm && cd build-arm
cmake -DCMAKE_TOOLCHAIN_FILE=../arm-toolchain.cmake ..
make

# 4. 打包
tar czf iot-device-arm.tar.gz iot-device ../config/device_info.json
```

### 8.2 树莓派部署

```bash
# 1. 传输文件
scp iot-device-arm.tar.gz pi@raspberrypi.local:/home/pi/

# 2. SSH 登录树莓派
ssh pi@raspberrypi.local

# 3. 解压
tar xzf iot-device-arm.tar.gz
sudo mv iot-device /opt/
sudo mv device_info.json /opt/iot-device/config/

# 4. 运行
cd /opt/iot-device
./iot-device

# 5. 配置开机自启
sudo cp iot-device.service /etc/systemd/system/
sudo systemctl enable iot-device
sudo systemctl start iot-device
```

---

## 总结

### ✅ ARM Linux 完整方案

1. **SDK 选择**：阿里云 Link SDK (C 语言)
2. **工具链**：arm-linux-gnueabihf（ARMv7）或 aarch64-linux-gnu（ARMv8）
3. **编译方式**：CMake + 工具链文件 或 Makefile
4. **部署方式**：scp 传输 + systemd 服务

### ✅ 适用平台

- ✅ 树莓派（所有型号）
- ✅ 瑞芯微（RK3288/3328/3399）
- ✅ 全志（A33/H3/H6）
- ✅ NXP i.MX（i.MX6/i.MX8）
- ✅ 其他 ARM Linux 设备

### ✅ 优势

- 无需 Java/Node.js 运行时
- 资源占用小（< 10MB）
- 启动速度快（秒级）
- 官方维护，稳定可靠
