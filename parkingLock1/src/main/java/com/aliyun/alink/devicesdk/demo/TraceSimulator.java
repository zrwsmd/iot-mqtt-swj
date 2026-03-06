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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trace 数据模拟发送器
 *
 * 核心机制：单线程同步逐包发送，每包等待 MQTT ACK 后再发下一包，
 * 失败自动重试（最多 MAX_RETRY 次），强保证所有包全部成功送达。
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

    // 最大发送帧数，到达后自动停止
    private static final long MAX_FRAMES = 10000;

    // 单包发送失败最大重试次数
    private static final int MAX_RETRY = 3;

    // 每包 ACK 等待超时（秒）
    private static final int ACK_TIMEOUT_SECONDS = 10;

    // 每包发送间隔（毫秒），避免 MQTT 积压
    private static final long SEND_INTERVAL_MS = 100;

    // 发送工作线程
    private Thread sendThread;
    private volatile boolean running = false;

    // 发送统计
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);

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

    /**
     * 异步启动发送线程。
     * 发送线程会逐包同步发送，每包等待 ACK 成功后才发下一包。
     * 如果需要等待全部发送完成，调用 start() 后再调用 awaitComplete()。
     */
    public void start() {
        if (running) {
            ALog.w(TAG, "TraceSimulator 已在运行，请勿重复启动");
            return;
        }

        // 重置计数器
        successCount.set(0);
        retryCount.set(0);
        running = true;

        long expectedBatches = (MAX_FRAMES + batchSize - 1) / batchSize;

        ALog.i(TAG, "TraceSimulator 启动:"
                + " periodMs=" + periodMs
                + " batchSize=" + batchSize
                + " maxFrames=" + MAX_FRAMES
                + " expectedBatches=" + expectedBatches
                + " maxRetry=" + MAX_RETRY
                + " ackTimeout=" + ACK_TIMEOUT_SECONDS + "s"
                + " sendInterval=" + SEND_INTERVAL_MS + "ms"
                + " topic=" + buildTopic());

        // 非守护线程，保证 JVM 不会提前退出
        sendThread = new Thread(this::sendLoop, "trace-simulator");
        sendThread.setDaemon(false);
        sendThread.start();
    }

    /**
     * 阻塞等待所有数据发送完成。
     * 调用此方法保证 MAX_FRAMES 帧全部成功发送后才返回。
     */
    public void awaitComplete() {
        if (sendThread == null) {
            ALog.w(TAG, "TraceSimulator 未启动，无需等待");
            return;
        }
        try {
            ALog.i(TAG, "等待所有 Trace 数据发送完成...");
            sendThread.join();
            ALog.i(TAG, "所有 Trace 数据已发送完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ALog.e(TAG, "等待被中断: " + e.getMessage());
        }
    }

    /**
     * 同步启动并等待发送完成（便捷方法）。
     * 调用后会阻塞直到所有帧全部成功发送完毕。
     */
    public void startAndWait() {
        start();
        awaitComplete();
    }

    public void stop() {
        running = false;
        if (sendThread != null) {
            sendThread.interrupt();
            sendThread = null;
        }
    }

    // -------------------------------------------------------------------------
    // 核心：同步逐包发送循环
    // -------------------------------------------------------------------------

    private void sendLoop() {
        long totalBatches = (MAX_FRAMES + batchSize - 1) / batchSize;
        long frameSent = 0;
        long tsCounter = 0;

        ALog.i(TAG, "发送循环开始，共需发送 " + totalBatches + " 包（" + MAX_FRAMES + " 帧）");

        for (long seq = 1; seq <= totalBatches && running; seq++) {
            // 构造当前包的 frames
            JSONArray frames = new JSONArray();
            int frameCount = 0;

            for (int i = 0; i < batchSize; i++) {
                tsCounter++;
                if (tsCounter > MAX_FRAMES) {
                    break;
                }

                JSONObject frame = new JSONObject();
                frame.put("ts", tsCounter);
                frame.put("axis1_position", simulateAxis1Position(tsCounter));
                frame.put("axis1_velocity", simulateAxis1Velocity(tsCounter));
                frame.put("axis1_torque",   simulateAxis1Torque(tsCounter));
                frame.put("motor_rpm",      simulateMotorRpm(tsCounter));
                frame.put("pressure_bar",   simulatePressure(tsCounter));
                frames.add(frame);
                frameCount++;
            }

            JSONObject payload = new JSONObject();
            payload.put("taskId", "sim_trace_001");
            payload.put("seq",    seq);
            payload.put("period", periodMs);
            payload.put("frames", frames);

            String payloadStr = payload.toJSONString();
            frameSent += frameCount;
            long tsStart = tsCounter - frameCount + 1;

            // 同步发送，失败重试
            boolean sent = false;
            for (int attempt = 1; attempt <= MAX_RETRY && !sent && running; attempt++) {
                if (attempt > 1) {
                    retryCount.incrementAndGet();
                    ALog.w(TAG, "重试第 " + attempt + " 次: seq=" + seq);
                    try {
                        Thread.sleep(500 * attempt);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                sent = publishAndWaitAck(seq, payloadStr, frameCount, tsStart, tsCounter, frameSent);
            }

            if (!sent) {
                ALog.e(TAG, "seq=" + seq + " 重试 " + MAX_RETRY + " 次后仍失败！终止发送。");
                break;
            }

            // 发送间隔，避免 MQTT 积压
            if (seq < totalBatches) {
                try {
                    Thread.sleep(SEND_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // 打印最终统计
        running = false;
        ALog.i(TAG, "========== TraceSimulator 最终统计 ==========");
        ALog.i(TAG, "总帧数: " + frameSent + " / " + MAX_FRAMES);
        ALog.i(TAG, "总包数: " + successCount.get() + " / " + totalBatches);
        ALog.i(TAG, "发送成功: " + successCount.get() + " 包");
        ALog.i(TAG, "重试次数: " + retryCount.get() + " 次");
        ALog.i(TAG, "结果: " + (successCount.get() == totalBatches ? "全部成功" : "存在失败"));
        ALog.i(TAG, "============================================");
    }

    /**
     * 发送一包数据并同步等待 ACK。
     * 返回 true 表示成功，false 表示失败（需重试）。
     */
    private boolean publishAndWaitAck(long seq, String payloadStr, int frameCount,
                                       long tsStart, long tsEnd, long totalSent) {
        final CountDownLatch ackLatch = new CountDownLatch(1);
        final AtomicBoolean ackSuccess = new AtomicBoolean(false);

        ALog.d(TAG, "PUBLISH seq=" + seq
                + " frames=" + frameCount
                + " ts=[" + tsStart + "~" + tsEnd + "]"
                + " totalSent=" + totalSent + "/" + MAX_FRAMES
                + " size=" + payloadStr.length() + "bytes");

        MqttPublishRequest request = new MqttPublishRequest();
        request.topic      = buildTopic();
        request.qos        = 1;
        request.payloadObj = payloadStr;

        LinkKit.getInstance().publish(request, new IConnectSendListener() {
            @Override
            public void onResponse(ARequest aRequest, AResponse aResponse) {
                ackSuccess.set(true);
                ackLatch.countDown();
            }

            @Override
            public void onFailure(ARequest aRequest, AError aError) {
                ALog.e(TAG, "PUBLISH 回调失败: seq=" + seq
                        + " error=" + (aError == null ? "null"
                        : aError.getCode() + "/" + aError.getMsg()));
                ackSuccess.set(false);
                ackLatch.countDown();
            }
        });

        // 同步等待 ACK
        try {
            boolean completed = ackLatch.await(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                ALog.e(TAG, "PUBLISH 超时: seq=" + seq + " (等待 " + ACK_TIMEOUT_SECONDS + "s 无响应)");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (ackSuccess.get()) {
            long ok = successCount.incrementAndGet();
            ALog.d(TAG, "PUBLISH 成功: seq=" + seq + " (成功" + ok + "包/共" + ((MAX_FRAMES + batchSize - 1) / batchSize) + "包)");
            return true;
        }
        return false;
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
