# Java + Spring Boot 本地控制端完整开发教程

---

## 目录

1. [项目初始化](#1-项目初始化)
2. [添加依赖](#2-添加依赖)
3. [项目结构](#3-项目结构)
4. [核心代码实现](#4-核心代码实现)
5. [配置文件](#5-配置文件)
6. [运行和测试](#6-运行和测试)
7. [API 接口文档](#7-api-接口文档)
8. [部署上线](#8-部署上线)

---

## 1. 项目初始化

### 1.1 使用 Maven 创建项目

```bash
# 创建项目目录
mkdir iot-controller-java
cd iot-controller-java

# 使用 Maven 创建项目
mvn archetype:generate \
  -DgroupId=com.example.iot \
  -DartifactId=iot-controller \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

### 1.2 或使用 Spring Initializr

访问 https://start.spring.io/，配置如下：

- **Project**: Maven
- **Language**: Java
- **Spring Boot**: 2.7.x
- **Group**: com.example.iot
- **Artifact**: iot-controller
- **Name**: iot-controller
- **Package name**: com.example.iot
- **Packaging**: Jar
- **Java**: 8 或 11

**Dependencies**:
- Spring Web
- Lombok (可选)

点击 "Generate" 下载项目，解压后进入项目目录。

---

## 2. 添加依赖

### 2.1 编辑 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.example.iot</groupId>
    <artifactId>iot-controller</artifactId>
    <version>1.0.0</version>
    <name>iot-controller</name>
    <description>阿里云 IoT 平台本地控制端（Java + Spring Boot）</description>

    <properties>
        <java.version>1.8</java.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 阿里云 IoT SDK -->
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

        <!-- Lombok（可选，简化代码） -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot 配置处理器 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2.2 安装依赖

```bash
mvn clean install
```

---

## 3. 项目结构

### 3.1 创建目录结构

```
iot-controller/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── iot/
│   │   │               ├── IotControllerApplication.java
│   │   │               ├── config/
│   │   │               │   └── IoTConfig.java
│   │   │               ├── controller/
│   │   │               │   └── DeviceController.java
│   │   │               ├── service/
│   │   │               │   ├── IoTService.java
│   │   │               │   └── impl/
│   │   │               │       └── IoTServiceImpl.java
│   │   │               ├── dto/
│   │   │               │   ├── ApiResponse.java
│   │   │               │   ├── PropertyRequest.java
│   │   │               │   └── ServiceRequest.java
│   │   │               └── exception/
│   │   │                   └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/
│           └── com/
│               └── example/
│                   └── iot/
│                       └── IoTServiceTest.java
├── pom.xml
└── README.md
```

---

## 4. 核心代码实现

### 4.1 配置类（config/IoTConfig.java）

```java
package com.example.iot.config;

import com.aliyun.iot20180120.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IoTConfig {

    @Value("${aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.iot.region}")
    private String region;

    @Bean
    public Client iotClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setEndpoint("iot." + region + ".aliyuncs.com");

        return new Client(config);
    }
}
```

---

### 4.2 DTO 类

#### ApiResponse.java（通用响应）

```java
package com.example.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Success");
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, LocalDateTime.now());
    }
}
```

#### PropertyRequest.java（属性设置请求）

```java
package com.example.iot.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PropertyRequest {
    private Map<String, Object> properties;
}
```

#### ServiceRequest.java（服务调用请求）

```java
package com.example.iot.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceRequest {
    private Map<String, Object> args;
}
```

---

### 4.3 服务接口（service/IoTService.java）

```java
package com.example.iot.service;

import java.util.Map;

public interface IoTService {
    
    /**
     * 调用设备服务
     */
    Map<String, Object> invokeService(String serviceId, String args) throws Exception;
    
    /**
     * 设置设备属性
     */
    Map<String, Object> setProperty(String propertyJson) throws Exception;
    
    /**
     * 查询设备属性
     */
    Map<String, Object> queryProperty() throws Exception;
    
    /**
     * 查询设备详情
     */
    Map<String, Object> queryDeviceDetail() throws Exception;
    
    /**
     * 获取设备状态（综合信息）
     */
    Map<String, Object> getDeviceStatus() throws Exception;
}
```

---

### 4.4 服务实现（service/impl/IoTServiceImpl.java）

```java
package com.example.iot.service.impl;

import com.aliyun.iot20180120.Client;
import com.aliyun.iot20180120.models.*;
import com.example.iot.service.IoTService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IoTServiceImpl implements IoTService {

    private static final Logger logger = LoggerFactory.getLogger(IoTServiceImpl.class);

    @Autowired
    private Client iotClient;

    @Value("${aliyun.iot.instance-id}")
    private String instanceId;

    @Value("${aliyun.iot.product-key}")
    private String productKey;

    @Value("${aliyun.iot.device-name}")
    private String deviceName;

    @Override
    public Map<String, Object> invokeService(String serviceId, String args) throws Exception {
        logger.info("Invoking service: {}, args: {}", serviceId, args);

        InvokeThingServiceRequest request = new InvokeThingServiceRequest()
                .setIotInstanceId(instanceId)
                .setProductKey(productKey)
                .setDeviceName(deviceName)
                .setIdentifier(serviceId)
                .setArgs(args);

        InvokeThingServiceResponse response = iotClient.invokeThingService(request);

        Map<String, Object> result = new HashMap<>();
        result.put("success", response.getBody().getSuccess());
        result.put("messageId", response.getBody().getData().getMessageId());
        result.put("data", response.getBody().getData());

        logger.info("Service invoked successfully: {}", serviceId);
        return result;
    }

    @Override
    public Map<String, Object> setProperty(String propertyJson) throws Exception {
        logger.info("Setting property: {}", propertyJson);

        SetDevicePropertyRequest request = new SetDevicePropertyRequest()
                .setIotInstanceId(instanceId)
                .setProductKey(productKey)
                .setDeviceName(deviceName)
                .setItems(propertyJson);

        SetDevicePropertyResponse response = iotClient.setDeviceProperty(request);

        Map<String, Object> result = new HashMap<>();
        result.put("success", response.getBody().getSuccess());
        result.put("messageId", response.getBody().getData().getMessageId());

        logger.info("Property set successfully");
        return result;
    }

    @Override
    public Map<String, Object> queryProperty() throws Exception {
        logger.info("Querying property");

        QueryDevicePropertyStatusRequest request = new QueryDevicePropertyStatusRequest()
                .setIotInstanceId(instanceId)
                .setProductKey(productKey)
                .setDeviceName(deviceName);

        QueryDevicePropertyStatusResponse response = iotClient.queryDevicePropertyStatus(request);

        List<QueryDevicePropertyStatusResponseBody.QueryDevicePropertyStatusResponseBodyDataListPropertyStatusInfo> properties =
                response.getBody().getData().getList().getPropertyStatusInfo();

        Map<String, Object> propertyMap = new HashMap<>();
        for (QueryDevicePropertyStatusResponseBody.QueryDevicePropertyStatusResponseBodyDataListPropertyStatusInfo prop : properties) {
            Map<String, Object> propData = new HashMap<>();
            propData.put("value", prop.getValue());
            propData.put("time", prop.getTime());
            propertyMap.put(prop.getIdentifier(), propData);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("properties", propertyMap);

        logger.info("Property queried successfully, count: {}", properties.size());
        return result;
    }

    @Override
    public Map<String, Object> queryDeviceDetail() throws Exception {
        logger.info("Querying device detail");

        QueryDeviceDetailRequest request = new QueryDeviceDetailRequest()
                .setIotInstanceId(instanceId)
                .setProductKey(productKey)
                .setDeviceName(deviceName);

        QueryDeviceDetailResponse response = iotClient.queryDeviceDetail(request);
        QueryDeviceDetailResponseBody.QueryDeviceDetailResponseBodyData deviceInfo = response.getBody().getData();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deviceName", deviceInfo.getDeviceName());
        result.put("productKey", deviceInfo.getProductKey());
        result.put("status", deviceInfo.getStatus());
        result.put("online", deviceInfo.getOnline());
        result.put("gmtCreate", deviceInfo.getGmtCreate());
        result.put("gmtActive", deviceInfo.getGmtActive());
        result.put("gmtOnline", deviceInfo.getGmtOnline());
        result.put("ipAddress", deviceInfo.getIpAddress());
        result.put("nodeType", deviceInfo.getNodeType());

        logger.info("Device detail queried successfully, status: {}, online: {}", 
                    deviceInfo.getStatus(), deviceInfo.getOnline());
        return result;
    }

    @Override
    public Map<String, Object> getDeviceStatus() throws Exception {
        logger.info("Getting device status");

        Map<String, Object> detail = queryDeviceDetail();
        Map<String, Object> properties = queryProperty();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("online", detail.get("online"));
        result.put("status", detail.get("status"));
        result.put("properties", properties.get("properties"));
        result.put("lastOnlineTime", detail.get("gmtOnline"));

        logger.info("Device status retrieved successfully");
        return result;
    }
}
```

---

### 4.5 控制器（controller/DeviceController.java）

```java
package com.example.iot.controller;

import com.example.iot.dto.ApiResponse;
import com.example.iot.dto.PropertyRequest;
import com.example.iot.dto.ServiceRequest;
import com.example.iot.service.IoTService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

    @Autowired
    private IoTService iotService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 调用设备服务
     */
    @PostMapping("/service/{serviceId}")
    public ApiResponse<Map<String, Object>> invokeService(
            @PathVariable String serviceId,
            @RequestBody(required = false) ServiceRequest request) {
        try {
            String args = "{}";
            if (request != null && request.getArgs() != null) {
                args = objectMapper.writeValueAsString(request.getArgs());
            }

            Map<String, Object> result = iotService.invokeService(serviceId, args);
            return ApiResponse.success(result, "服务 " + serviceId + " 调用成功");
        } catch (Exception e) {
            logger.error("Failed to invoke service: {}", serviceId, e);
            return ApiResponse.error("调用服务失败: " + e.getMessage());
        }
    }

    /**
     * 设置设备属性
     */
    @PostMapping("/property")
    public ApiResponse<Map<String, Object>> setProperty(@RequestBody PropertyRequest request) {
        try {
            if (request.getProperties() == null || request.getProperties().isEmpty()) {
                return ApiResponse.error("属性参数不能为空");
            }

            String propertyJson = objectMapper.writeValueAsString(request.getProperties());
            Map<String, Object> result = iotService.setProperty(propertyJson);
            return ApiResponse.success(result, "属性设置成功");
        } catch (Exception e) {
            logger.error("Failed to set property", e);
            return ApiResponse.error("设置属性失败: " + e.getMessage());
        }
    }

    /**
     * 查询设备属性
     */
    @GetMapping("/property")
    public ApiResponse<Map<String, Object>> queryProperty() {
        try {
            Map<String, Object> result = iotService.queryProperty();
            return ApiResponse.success(result, "查询属性成功");
        } catch (Exception e) {
            logger.error("Failed to query property", e);
            return ApiResponse.error("查询属性失败: " + e.getMessage());
        }
    }

    /**
     * 查询设备详情
     */
    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> queryDeviceDetail() {
        try {
            Map<String, Object> result = iotService.queryDeviceDetail();
            return ApiResponse.success(result, "查询设备详情成功");
        } catch (Exception e) {
            logger.error("Failed to query device detail", e);
            return ApiResponse.error("查询设备详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备状态（综合信息）
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getDeviceStatus() {
        try {
            Map<String, Object> result = iotService.getDeviceStatus();
            return ApiResponse.success(result, "获取设备状态成功");
        } catch (Exception e) {
            logger.error("Failed to get device status", e);
            return ApiResponse.error("获取设备状态失败: " + e.getMessage());
        }
    }
}
```

---

### 4.6 全局异常处理（exception/GlobalExceptionHandler.java）

```java
package com.example.iot.exception;

import com.example.iot.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        logger.error("Unhandled exception", e);
        return ApiResponse.error("服务器内部错误: " + e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("Invalid argument", e);
        return ApiResponse.error("参数错误: " + e.getMessage());
    }
}
```

---

### 4.7 应用入口（IotControllerApplication.java）

```java
package com.example.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IotControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotControllerApplication.class, args);
        System.out.println("\n🚀 IoT Controller API is running!");
        System.out.println("📖 API Documentation: http://localhost:8080");
        System.out.println("💚 Health Check: http://localhost:8080/actuator/health\n");
    }
}
```

---

## 5. 配置文件

### 5.1 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: iot-controller

# 阿里云配置
aliyun:
  access-key-id: ${ALIYUN_ACCESS_KEY_ID:your_access_key_id}
  access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET:your_access_key_secret}
  iot:
    instance-id: ${IOT_INSTANCE_ID:iot-06z00bmkq776uhu}
    region: ${IOT_REGION:cn-shanghai}
    product-key: ${PRODUCT_KEY:k03k41dVanO}
    device-name: ${DEVICE_NAME:Beijing_PC_001}

# 日志配置
logging:
  level:
    com.example.iot: DEBUG
    com.aliyun: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  file:
    name: logs/iot-controller.log
    max-size: 10MB
    max-history: 30
```

---

### 5.2 application-prod.yml（生产环境）

```yaml
server:
  port: 8080

# 生产环境日志配置
logging:
  level:
    com.example.iot: INFO
    com.aliyun: WARN
  file:
    name: /var/log/iot-controller/app.log
```

---

## 6. 运行和测试

### 6.1 配置环境变量

创建 `.env` 文件（或设置系统环境变量）：

```bash
export ALIYUN_ACCESS_KEY_ID=你的AccessKeyId
export ALIYUN_ACCESS_KEY_SECRET=你的AccessKeySecret
export IOT_INSTANCE_ID=iot-06z00bmkq776uhu
export IOT_REGION=cn-shanghai
export PRODUCT_KEY=k03k41dVanO
export DEVICE_NAME=Beijing_PC_001
```

### 6.2 编译项目

```bash
mvn clean package
```

### 6.3 运行项目

```bash
# 方式一：使用 Maven
mvn spring-boot:run

# 方式二：运行 JAR
java -jar target/iot-controller-1.0.0.jar

# 方式三：指定配置文件
java -jar target/iot-controller-1.0.0.jar --spring.profiles.active=prod
```

**预期输出**：

```
🚀 IoT Controller API is running!
📖 API Documentation: http://localhost:8080
💚 Health Check: http://localhost:8080/actuator/health
```

---

### 6.4 测试代码（test/IoTServiceTest.java）

```java
package com.example.iot;

import com.example.iot.service.IoTService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest
public class IoTServiceTest {

    @Autowired
    private IoTService iotService;

    @Test
    public void testQueryDeviceDetail() throws Exception {
        Map<String, Object> result = iotService.queryDeviceDetail();
        System.out.println("设备状态: " + result.get("status"));
        System.out.println("在线状态: " + result.get("online"));
    }

    @Test
    public void testQueryProperty() throws Exception {
        Map<String, Object> result = iotService.queryProperty();
        System.out.println("设备属性: " + result.get("properties"));
    }

    @Test
    public void testSetProperty() throws Exception {
        Map<String, Object> result = iotService.setProperty("{\"ADASSwitch\": 1}");
        System.out.println("设置结果: " + result.get("success"));
    }

    @Test
    public void testInvokeService() throws Exception {
        Map<String, Object> result = iotService.invokeService("restart", "{}");
        System.out.println("调用结果: " + result.get("success"));
        System.out.println("消息ID: " + result.get("messageId"));
    }

    @Test
    public void testGetDeviceStatus() throws Exception {
        Map<String, Object> result = iotService.getDeviceStatus();
        System.out.println("在线: " + result.get("online"));
        System.out.println("属性: " + result.get("properties"));
    }
}
```

运行测试：

```bash
mvn test
```

---

## 7. API 接口文档

### 7.1 调用设备服务

**接口**: `POST /api/device/service/{serviceId}`

**请求示例**：

```bash
curl -X POST http://localhost:8080/api/device/service/restart \
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
  "timestamp": "2026-03-03T15:30:00"
}
```

---

### 7.2 设置设备属性

**接口**: `POST /api/device/property`

**请求示例**：

```bash
curl -X POST http://localhost:8080/api/device/property \
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
  "timestamp": "2026-03-03T15:31:00"
}
```

---

### 7.3 查询设备属性

**接口**: `GET /api/device/property`

**请求示例**：

```bash
curl http://localhost:8080/api/device/property
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
  "timestamp": "2026-03-03T15:32:00"
}
```

---

### 7.4 查询设备详情

**接口**: `GET /api/device/detail`

**请求示例**：

```bash
curl http://localhost:8080/api/device/detail
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
  "timestamp": "2026-03-03T15:33:00"
}
```

---

### 7.5 获取设备状态

**接口**: `GET /api/device/status`

**请求示例**：

```bash
curl http://localhost:8080/api/device/status
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
  "timestamp": "2026-03-03T15:34:00"
}
```

---

## 8. 部署上线

### 8.1 打包部署

```bash
# 打包
mvn clean package -DskipTests

# 生成的 JAR 文件
ls target/iot-controller-1.0.0.jar
```

---

### 8.2 使用 systemd 管理（Linux）

创建 `/etc/systemd/system/iot-controller.service`：

```ini
[Unit]
Description=IoT Controller Service
After=network.target

[Service]
Type=simple
User=iot
WorkingDirectory=/opt/iot-controller
ExecStart=/usr/bin/java -jar /opt/iot-controller/iot-controller-1.0.0.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
Environment="ALIYUN_ACCESS_KEY_ID=your_ak"
Environment="ALIYUN_ACCESS_KEY_SECRET=your_sk"
Environment="IOT_INSTANCE_ID=iot-06z00bmkq776uhu"
Environment="PRODUCT_KEY=k03k41dVanO"
Environment="DEVICE_NAME=Beijing_PC_001"

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
# 重载配置
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start iot-controller

# 开机自启
sudo systemctl enable iot-controller

# 查看状态
sudo systemctl status iot-controller

# 查看日志
sudo journalctl -u iot-controller -f
```

---

### 8.3 使用 Docker 部署

创建 `Dockerfile`：

```dockerfile
FROM openjdk:8-jdk-alpine

WORKDIR /app

COPY target/iot-controller-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  iot-controller:
    build: .
    ports:
      - "8080:8080"
    environment:
      - ALIYUN_ACCESS_KEY_ID=${ALIYUN_ACCESS_KEY_ID}
      - ALIYUN_ACCESS_KEY_SECRET=${ALIYUN_ACCESS_KEY_SECRET}
      - IOT_INSTANCE_ID=${IOT_INSTANCE_ID}
      - PRODUCT_KEY=${PRODUCT_KEY}
      - DEVICE_NAME=${DEVICE_NAME}
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

### 8.4 使用 Nginx 反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 9. 项目配置文件示例

### 9.1 .gitignore

```
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties
dependency-reduced-pom.xml
buildNumber.properties
.mvn/timing.properties

# IDE
.idea/
*.iml
.vscode/
.classpath
.project
.settings/

# 日志
logs/
*.log

# 环境变量
.env

# 系统文件
.DS_Store
Thumbs.db
```

---

### 9.2 README.md

```markdown
# IoT Controller API (Java + Spring Boot)

阿里云 IoT 平台本地控制端，基于 Java + Spring Boot 开发。

## 功能特性

- ✅ 调用设备服务
- ✅ 设置设备属性
- ✅ 查询设备属性
- ✅ 查询设备详情
- ✅ 获取设备状态
- ✅ RESTful API
- ✅ 全局异常处理
- ✅ 日志记录

## 技术栈

- Java 8
- Spring Boot 2.7.x
- Maven
- 阿里云 IoT SDK

## 快速开始

### 1. 配置环境变量

\`\`\`bash
export ALIYUN_ACCESS_KEY_ID=你的AK
export ALIYUN_ACCESS_KEY_SECRET=你的SK
export IOT_INSTANCE_ID=iot-06z00bmkq776uhu
export PRODUCT_KEY=k03k41dVanO
export DEVICE_NAME=Beijing_PC_001
\`\`\`

### 2. 编译项目

\`\`\`bash
mvn clean package
\`\`\`

### 3. 运行项目

\`\`\`bash
java -jar target/iot-controller-1.0.0.jar
\`\`\`

### 4. 测试

\`\`\`bash
mvn test
\`\`\`

## API 文档

访问 http://localhost:8080 查看 API 列表。

## 许可证

MIT
```

---

## 总结

本教程从零开始，完整演示了如何使用 Java + Spring Boot 开发阿里云 IoT 平台本地控制端，包括：

- ✅ Maven 项目初始化
- ✅ 依赖配置
- ✅ 完整的项目结构
- ✅ 核心代码实现（配置、服务、控制器）
- ✅ 全局异常处理
- ✅ RESTful API 设计
- ✅ 单元测试
- ✅ 多种部署方案

**优势**：
- 类型安全
- 企业级框架
- 高性能
- 易于维护

**适用场景**：
- 企业级应用
- 高并发场景
- 大型项目
- 长期维护
