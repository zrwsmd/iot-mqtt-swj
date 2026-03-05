package com.aliyun.alink.devicesdk.demo;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linksdk.cmp.connect.channel.MqttPublishRequest;
import com.aliyun.alink.linksdk.cmp.core.base.ARequest;
import com.aliyun.alink.linksdk.cmp.core.base.AResponse;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectSendListener;
import com.aliyun.alink.linksdk.tools.AError;
import com.aliyun.alink.linksdk.tools.ALog;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trace 数据模拟发送器
 *
 * 数据结构：时间戳从 1 开始递增，每个时间戳对应一个 frame，
 * frame 内包含所有变量在该时刻的采样值。
 *
 * payload 示例：
 * {
 *   "taskId": "sim_trace_001",
 *   "seq": 1,
 *   "period": 1,
 *   "frames": [
 *     {
 *       "ts": 1,
 *       "axis1_position": 12.345,
 *       "axis1_velocity": 3.141,
 *       "axis1_torque":   48.200,
 *       "axis2_position": 55.678,
 *       "axis2_velocity": 10.234,
 *       "motor_rpm":      1487.500,
 *       "motor_temp":     64.320,
 *       "servo_current":  4.812,
 *       "servo_voltage":  48.100,
 *       "pressure_bar":   5.230
 *     },
 *     {
 *       "ts": 2,
 *       "axis1_position": 13.001,
 *       ...
 *     }
 *   ]
 * }
 *
 * 使用方式（在 HelloWorld.executeScheduler 中调用）:
 *   TraceSimulator simulator = new TraceSimulator(pk, dn);
 *   simulator.start();
 */
public class TraceSimulator extends BaseSample {

    private static final String TAG = "TraceSimulator";

    // 自定义 Topic，需在阿里云 IoT 控制台提前创建，设备操作权限选"发布"
    private static final String TRACE_TOPIC_SUFFIX = "/user/trace/data";

    // 采集周期 ms
    private final int periodMs;

    // 每包包含多少个 frame
    private final int batchSize;

    // 从 1 开始递增的时间戳，每个 frame +1
    private final AtomicLong tsCounter = new AtomicLong(0);

    // 发包序号，接收端用于检测丢包
    private final AtomicLong seqCounter = new AtomicLong(0);

    // 最大发送帧数，到达后自动停止
    private static final long MAX_FRAMES = 10000;

    private ScheduledExecutorService scheduler;

    /**
     * 默认：1ms 采集周期，每包 100 个 frame，每 100ms 发一次
     */
    public TraceSimulator(String pk, String dn) {
        this(pk, dn, 1, 100);
    }

    /**
     * @param pk        productKey
     * @param dn        deviceName
     * @param periodMs  采集周期 ms
     * @param batchSize 每包 frame 数量
     */
    public TraceSimulator(String pk, String dn, int periodMs, int batchSize) {
        super(pk, dn);
        this.periodMs  = periodMs;
        this.batchSize = batchSize;
    }

    // -------------------------------------------------------------------------
    // 启动 / 停止
    // -------------------------------------------------------------------------

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            ALog.w(TAG, "TraceSimulator 已在运行，请勿重复启动");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trace-simulator");
            t.setDaemon(true);
            return t;
        });

        long flushIntervalMs = (long) periodMs * batchSize;

        ALog.i(TAG, "TraceSimulator 启动:"
                + " periodMs=" + periodMs
                + " batchSize=" + batchSize
                + " flushInterval=" + flushIntervalMs + "ms"
                + " topic=" + buildTopic());

        scheduler.scheduleAtFixedRate(() -> {
            try {
                publishBatch();
            } catch (Exception e) {
                ALog.e(TAG, "publishBatch 异常: " + e.getMessage());
            }
        }, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            ALog.i(TAG, "TraceSimulator 已停止");
        }
    }

    // -------------------------------------------------------------------------
    // 核心：构造一批 frames 并 PUBLISH
    // -------------------------------------------------------------------------

    private void publishBatch() {
        // 已达最大帧数，自动停止
        if (tsCounter.get() >= MAX_FRAMES) {
            ALog.i(TAG, "已发送 " + MAX_FRAMES + " 帧，自动停止");
            stop();
            return;
        }

        long seq = seqCounter.incrementAndGet();

        JSONArray frames = new JSONArray();

        for (int i = 0; i < batchSize; i++) {
            // ts 从 1 开始，每个 frame 递增 1
            long ts = tsCounter.incrementAndGet();

            // 到达最大帧数，当前包截断发送
            if (ts > MAX_FRAMES) {
                tsCounter.set(MAX_FRAMES);
                break;
            }

            JSONObject frame = new JSONObject();
            frame.put("ts", ts);

            // 同一时间戳下所有变量的采样值
            frame.put("axis1_position", simulateAxis1Position(ts));
            frame.put("axis1_velocity", simulateAxis1Velocity(ts));
            frame.put("axis1_torque",   simulateAxis1Torque(ts));
            frame.put("motor_rpm",      simulateMotorRpm(ts));
            frame.put("pressure_bar",   simulatePressure(ts));

            frames.add(frame);
        }

        JSONObject payload = new JSONObject();
        payload.put("taskId",  "sim_trace_001");
        payload.put("seq",     seq);
        payload.put("period",  periodMs);
        payload.put("frames",  frames);

        String payloadStr = payload.toJSONString();
        long tsEnd = tsCounter.get();
        long tsStart = tsEnd - batchSize + 1;

        ALog.d(TAG, "PUBLISH seq=" + seq
                + " frames=" + batchSize
                + " ts=[" + tsStart + "~" + tsEnd + "]"
                + " size=" + payloadStr.length() + "bytes");

        MqttPublishRequest request = new MqttPublishRequest();
        request.topic      = buildTopic();
        request.qos        = 1;
        request.payloadObj = payloadStr;

        LinkKit.getInstance().publish(request, new IConnectSendListener() {
            @Override
            public void onResponse(ARequest aRequest, AResponse aResponse) {
                ALog.d(TAG, "PUBLISH 成功: seq=" + seq);
            }

            @Override
            public void onFailure(ARequest aRequest, AError aError) {
                ALog.e(TAG, "PUBLISH 失败: seq=" + seq
                        + " error=" + (aError == null ? "null"
                        : aError.getCode() + "/" + aError.getMsg()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // 各变量模拟函数
    // ts * periodMs / 1000.0 把整数时间戳换算成秒，用于波形计算
    // -------------------------------------------------------------------------

    // 轴1位置 (mm)：正弦往复，范围 -100 ~ 100
    private double simulateAxis1Position(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(100.0 * Math.sin(2 * Math.PI * 0.5 * t) + noise(0.5));
    }

    // 轴1速度 (mm/s)：位置的导数
    private double simulateAxis1Velocity(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(Math.PI * 100.0 * Math.cos(2 * Math.PI * 0.5 * t) + noise(1.0));
    }

    // 轴1力矩 (N·m)：范围 30 ~ 70
    private double simulateAxis1Torque(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(50.0 + 20.0 * Math.sin(2 * Math.PI * 1.0 * t) + noise(0.5));
    }

    // 轴2位置 (mm)：不同频率，范围 -80 ~ 80
    private double simulateAxis2Position(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(80.0 * Math.sin(2 * Math.PI * 0.3 * t + 1.0) + noise(0.5));
    }

    // 轴2速度 (mm/s)
    private double simulateAxis2Velocity(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(80.0 * 0.3 * 2 * Math.PI * Math.cos(2 * Math.PI * 0.3 * t + 1.0) + noise(0.5));
    }

    // 电机转速 (RPM)：范围 1200 ~ 1800
    private double simulateMotorRpm(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(1500.0 + 300.0 * Math.sin(2 * Math.PI * 0.1 * t) + noise(5.0));
    }

    // 电机温度 (°C)：缓慢变化，范围 55 ~ 75
    private double simulateMotorTemp(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(65.0 + 10.0 * Math.sin(2 * Math.PI * 0.02 * t) + noise(0.2));
    }

    // 伺服电流 (A)：范围 3 ~ 7
    private double simulateServoCurrent(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(5.0 + 2.0 * Math.sin(2 * Math.PI * 0.8 * t) + noise(0.1));
    }

    // 伺服电压 (V)：相对稳定，范围 47 ~ 49
    private double simulateServoVoltage(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(48.0 + 0.5 * Math.sin(2 * Math.PI * 0.05 * t) + noise(0.05));
    }

    // 压力 (bar)：范围 3.5 ~ 6.5
    private double simulatePressure(long ts) {
        double t = ts * periodMs / 1000.0;
        return round(5.0 + 1.5 * Math.sin(2 * Math.PI * 0.2 * t) + noise(0.05));
    }

    // -------------------------------------------------------------------------
    // 工具
    // -------------------------------------------------------------------------

    /** ±scale * 2% 的随机噪声 */
    private double noise(double scale) {
        return (Math.random() - 0.5) * 2 * scale * 0.02;
    }

    private double round(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }

    private String buildTopic() {
        return "/" + productKey + "/" + deviceName + TRACE_TOPIC_SUFFIX;
    }
}
