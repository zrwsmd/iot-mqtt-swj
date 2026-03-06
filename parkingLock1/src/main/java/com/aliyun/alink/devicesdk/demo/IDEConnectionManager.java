package com.aliyun.alink.devicesdk.demo;

import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tmp.listener.IPublishResourceListener;
import com.aliyun.alink.linksdk.tools.AError;
import com.aliyun.alink.linksdk.tools.ALog;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * IDE 控制端连接锁管理器
 *
 * 负责管理三个物模型属性：
 *   hasIDEConnected  (bool)   - 是否有IDE已连接
 *   IDEInfo          (text)   - 已连接IDE的信息（clientId、IP等）
 *   IDEHeartbeat     (text)   - 最后心跳时间戳（毫秒字符串）
 *
 * 核心规则：
 *   - 上位机是唯一写入方，所有状态变更都由上位机决定
 *   - 控制端通过调用 requestConnect 服务发起连接申请
 *   - 心跳超过 HEARTBEAT_TIMEOUT_MS 未更新，视为控制端已掉线，锁自动释放
 *
 * 使用方式（在 ThingSample 的 onProcess 里调用）：
 *   IDEConnectionManager manager = new IDEConnectionManager(pk, dn);
 *   manager.handleRequestConnect(params, callback);
 *   manager.handleDisconnect(params, callback);
 *   manager.handleHeartbeat(params, callback);
 */
public class IDEConnectionManager extends BaseSample {

    private static final String TAG = "IDEConnectionManager";

    // 物模型属性标识符
    public static final String PROP_HAS_IDE_CONNECTED = "hasIDEConnected";
    public static final String PROP_IDE_INFO          = "IDEInfo";
    public static final String PROP_IDE_HEARTBEAT     = "IDEHeartbeat";

    // 心跳超时时间：2分钟内没收到心跳，认为控制端掉线
    private static final long HEARTBEAT_TIMEOUT_MS = 2 * 60 * 1000L;

    // 心跳检查周期：每30秒检查一次
    private static final long HEARTBEAT_CHECK_INTERVAL_SEC = 30;

    // 当前连接状态（内存缓存，避免每次查属性）
    private volatile boolean connected        = false;
    private volatile String  currentClientId  = "";
    private volatile long    lastHeartbeatMs  = 0;

    // 心跳超时检查定时器
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ide-heartbeat-checker");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> heartbeatCheckTask;

    public IDEConnectionManager(String pk, String dn) {
        super(pk, dn);
        startHeartbeatChecker();
    }

    // -------------------------------------------------------------------------
    // 服务处理入口（在 ThingSample.onProcess 中调用）
    // -------------------------------------------------------------------------

    /**
     * 处理 requestConnect 服务调用
     *
     * 控制端传入的 params 示例：
     * {
     *   "clientId": "ide-client-001",
     *   "clientInfo": "user:张三 ip:192.168.1.100"
     * }
     *
     * 回复给控制端的 OutputParams：
     * {
     *   "success": true/false,
     *   "message": "连接成功" / "当前已被 ide-client-001 占用"
     * }
     */
    public void handleRequestConnect(Map<String, ValueWrapper> params,
                                     RequestConnectCallback callback) {
        String clientId   = getStringParam(params, "clientId",   "unknown");
        String clientInfo = getStringParam(params, "clientInfo", "");

        ALog.i(TAG, "收到 requestConnect 申请: clientId=" + clientId
                + " clientInfo=" + clientInfo);

        // ① 如果当前没有人连接，直接允许
        if (!connected) {
            doLock(clientId, clientInfo);
            callback.onResult(true, "连接成功");
            ALog.i(TAG, "连接成功: clientId=" + clientId);
            return;
        }

        // ② 如果是同一个控制端重连，允许（刷新信息）
        if (currentClientId.equals(clientId)) {
            doLock(clientId, clientInfo);
            callback.onResult(true, "重连成功");
            ALog.i(TAG, "重连成功: clientId=" + clientId);
            return;
        }

        // ③ 已有别人连接，检查心跳是否超时
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatMs > HEARTBEAT_TIMEOUT_MS) {
            // 心跳超时，上一个控制端已掉线，允许抢占
            ALog.i(TAG, "原连接心跳超时，允许抢占: oldClient=" + currentClientId
                    + " newClient=" + clientId);
            doLock(clientId, clientInfo);
            callback.onResult(true, "原连接已超时，连接成功");
        } else {
            // 心跳正常，拒绝
            String msg = "当前已被 " + currentClientId + " 占用，请稍后再试";
            ALog.i(TAG, "拒绝连接: " + msg);
            callback.onResult(false, msg);
        }
    }

    /**
     * 处理 requestDisconnect 服务调用（控制端主动断开）
     *
     * 控制端传入的 params 示例：
     * { "clientId": "ide-client-001" }
     */
    public void handleDisconnect(Map<String, ValueWrapper> params,
                                 RequestConnectCallback callback) {
        String clientId = getStringParam(params, "clientId", "unknown");

        ALog.i(TAG, "收到 requestDisconnect: clientId=" + clientId);

        if (!connected) {
            callback.onResult(true, "当前无连接");
            return;
        }

        if (!currentClientId.equals(clientId)) {
            // 不是当前占用者，无权断开
            callback.onResult(false, "无权断开，当前连接者为 " + currentClientId);
            return;
        }

        doUnlock();
        callback.onResult(true, "断开成功");
        ALog.i(TAG, "连接已断开: clientId=" + clientId);
    }

    /**
     * 处理 ideHeartbeat 服务调用（控制端定时心跳）
     *
     * 控制端传入的 params 示例：
     * { "clientId": "ide-client-001" }
     */
    public void handleHeartbeat(Map<String, ValueWrapper> params,
                                RequestConnectCallback callback) {
        String clientId = getStringParam(params, "clientId", "unknown");

        if (!connected || !currentClientId.equals(clientId)) {
            callback.onResult(false, "心跳失败：你不是当前连接者");
            return;
        }

        // 更新内存心跳时间
        lastHeartbeatMs = System.currentTimeMillis();

        // 更新云端 IDEHeartbeat 属性
        reportHeartbeat(lastHeartbeatMs);

        callback.onResult(true, "心跳更新成功");
        ALog.d(TAG, "心跳更新: clientId=" + clientId + " ts=" + lastHeartbeatMs);
    }

    // -------------------------------------------------------------------------
    // 内部：加锁 / 解锁 / 属性上报
    // -------------------------------------------------------------------------

    /**
     * 加锁：更新内存状态 + 上报三个属性到云端
     */
    private void doLock(String clientId, String clientInfo) {
        connected       = true;
        currentClientId = clientId;
        lastHeartbeatMs = System.currentTimeMillis();

        Map<String, ValueWrapper> props = new HashMap<>();
        props.put(PROP_HAS_IDE_CONNECTED, new ValueWrapper.BooleanValueWrapper(1));
        // 构造标准 JSON 格式，与 Node.js 端解析逻辑对应
        String ideInfoJson = "{\"clientId\":\"" + clientId + "\","
                + "\"clientInfo\":" + (isValidJson(clientInfo) ? clientInfo : "\"" + clientInfo + "\"") + ","
                + "\"connectTime\":" + lastHeartbeatMs + "}";
        props.put(PROP_IDE_INFO,      new ValueWrapper.StringValueWrapper(ideInfoJson));
        props.put(PROP_IDE_HEARTBEAT, new ValueWrapper.StringValueWrapper(String.valueOf(lastHeartbeatMs)));
//        props.put(PROP_IDE_INFO,          new ValueWrapper.StringValueWrapper(
//                "clientId:" + clientId + " " + clientInfo));
//        props.put(PROP_IDE_HEARTBEAT,     new ValueWrapper.StringValueWrapper(
//                String.valueOf(lastHeartbeatMs)));
        reportProperties(props, "doLock clientId=" + clientId);
    }

    private boolean isValidJson(String s) {
        if (s == null || s.isEmpty()) return false;
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    /**
     * 解锁：更新内存状态 + 清空云端属性
     */
    private void doUnlock() {
        connected       = false;
        currentClientId = "";
        lastHeartbeatMs = 0;

        Map<String, ValueWrapper> props = new HashMap<>();
        props.put(PROP_HAS_IDE_CONNECTED, new ValueWrapper.BooleanValueWrapper(0));
        props.put(PROP_IDE_INFO,          new ValueWrapper.StringValueWrapper(""));
        props.put(PROP_IDE_HEARTBEAT,     new ValueWrapper.StringValueWrapper("0"));

        reportProperties(props, "doUnlock");
    }

    /**
     * 单独更新心跳时间戳到云端
     */
    private void reportHeartbeat(long ts) {
        Map<String, ValueWrapper> props = new HashMap<>();
        props.put(PROP_IDE_HEARTBEAT, new ValueWrapper.StringValueWrapper(String.valueOf(ts)));
        reportProperties(props, "reportHeartbeat");
    }

    /**
     * 批量上报属性到云端
     */
    private void reportProperties(Map<String, ValueWrapper> props, String tag) {
        LinkKit.getInstance().getDeviceThing().thingPropertyPost(
                props,
                new IPublishResourceListener() {
                    @Override
                    public void onSuccess(String alinkId, Object o) {
                        ALog.d(TAG, "属性上报成功 [" + tag + "]");
                    }

                    @Override
                    public void onError(String alinkId, AError aError) {
                        ALog.e(TAG, "属性上报失败 [" + tag + "]: "
                                + (aError == null ? "null" : aError.getMsg()));
                    }
                }
        );
    }

    // -------------------------------------------------------------------------
    // 心跳超时自动检查
    // -------------------------------------------------------------------------

    /**
     * 启动定时任务，每30秒检查一次心跳是否超时
     * 超时则自动释放锁，避免控制端崩溃后锁永远不释放
     */
    private void startHeartbeatChecker() {
        heartbeatCheckTask = scheduler.scheduleAtFixedRate(() -> {
            if (!connected) return;

            long now = System.currentTimeMillis();
            if (now - lastHeartbeatMs > HEARTBEAT_TIMEOUT_MS) {
                ALog.i(TAG, "心跳超时，自动释放连接锁: clientId=" + currentClientId
                        + " 最后心跳=" + lastHeartbeatMs
                        + " 已超时=" + (now - lastHeartbeatMs) + "ms");
                doUnlock();
            }
        }, HEARTBEAT_CHECK_INTERVAL_SEC, HEARTBEAT_CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /**
     * 停止心跳检查（上位机关闭时调用）
     */
    public void shutdown() {
        if (heartbeatCheckTask != null) {
            heartbeatCheckTask.cancel(false);
        }
        scheduler.shutdown();
        ALog.i(TAG, "IDEConnectionManager 已关闭");
    }

    // -------------------------------------------------------------------------
    // 工具
    // -------------------------------------------------------------------------

    private String getStringParam(Map<String, ValueWrapper> params,
                                  String key, String defaultVal) {
        if (params == null || !params.containsKey(key)) return defaultVal;
        ValueWrapper w = params.get(key);
        if (w == null || w.getValue() == null) return defaultVal;
        return w.getValue().toString();
    }

    // -------------------------------------------------------------------------
    // 回调接口
    // -------------------------------------------------------------------------

    /**
     * requestConnect / requestDisconnect / ideHeartbeat 服务的处理结果回调
     */
    public interface RequestConnectCallback {
        /**
         * @param success true=允许/成功，false=拒绝/失败
         * @param message 返回给控制端的描述信息
         */
        void onResult(boolean success, String message);
    }
}
