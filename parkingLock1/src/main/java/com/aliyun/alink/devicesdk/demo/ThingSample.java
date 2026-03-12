package com.aliyun.alink.devicesdk.demo;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.alink.apiclient.utils.StringUtils;
import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linksdk.cmp.connect.channel.MqttPublishRequest;
import com.aliyun.alink.linksdk.cmp.core.base.AMessage;
import com.aliyun.alink.linksdk.cmp.core.base.ARequest;
import com.aliyun.alink.linksdk.cmp.core.base.AResponse;
import com.aliyun.alink.linksdk.cmp.core.base.ConnectState;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectNotifyListener;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectSendListener;
import com.aliyun.alink.linksdk.tmp.api.InputParams;
import com.aliyun.alink.linksdk.tmp.api.OutputParams;
import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tmp.devicemodel.Service;
import com.aliyun.alink.linksdk.tmp.listener.IPublishResourceListener;
import com.aliyun.alink.linksdk.tmp.listener.ITResRequestHandler;
import com.aliyun.alink.linksdk.tmp.listener.ITResResponseCallback;
import com.aliyun.alink.linksdk.tmp.utils.ErrorInfo;
import com.aliyun.alink.linksdk.tools.AError;
import com.aliyun.alink.linksdk.tools.ALog;
import com.aliyun.alink.linksdk.tools.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThingSample extends BaseSample {
    private static final String TAG = "ThingSample";

    private final static String SERVICE_SET = "set";
    private final static String SERVICE_GET = "get";
    private final static String CONNECT_ID  = "LINK_PERSISTENT";

    // IDE 连接管理服务标识符（与物模型中定义的一致）
    private final static String SERVICE_REQUEST_CONNECT    = "requestConnect";
    private final static String SERVICE_REQUEST_DISCONNECT = "requestDisconnect";
    private final static String SERVICE_IDE_HEARTBEAT      = "ideHeartbeat";
    private final static String SERVICE_DEPLOY_PROJECT     = "deployProject";
    private final static String SERVICE_START_PROJECT      = "startProject";

    // IDE 连接管理器，在 setServiceHandler 时初始化
    private IDEConnectionManager ideConnectionManager;
    private DeployManager deployManager;

    public ThingSample(String pk, String dn) {
        super(pk, dn);
    }

    /*  上报属性  */
    public void reportDemoProperty() {
        String identity = "ADASSwitch";
        ValueWrapper intWrapper = new ValueWrapper.BooleanValueWrapper(1);

        Map<String, ValueWrapper> reportData = new HashMap<String, ValueWrapper>();
        reportData.put(identity, intWrapper);

        LinkKit.getInstance().getDeviceThing().thingPropertyPost(reportData, new IPublishResourceListener() {

            public void onSuccess(String alinkId, Object o) {
                ALog.d(TAG, "上报成功 onSuccess() called with: alinkId = [" + alinkId + "], o = [" + o + "]");
            }

            public void onError(String alinkId, AError aError) {
                ALog.d(TAG, "上报失败onError() called with: alinkId = [" + alinkId + "], aError = [" + getError(aError) + "]");
            }
        });
    }

    public void reportDemoProperty1(Map<String, ValueWrapper> reportData) {
        LinkKit.getInstance().getDeviceThing().thingPropertyPost(reportData, new IPublishResourceListener() {

            public void onSuccess(String alinkId, Object o) {
                // 属性上报成功
                ALog.d(TAG, "上报成功 onSuccess() called with: alinkId = [" + alinkId + "], o = [" + o + "]");
            }

            public void onError(String alinkId, AError aError) {
                // 属性上报失败
                ALog.d(TAG, "上报失败onError() called with: alinkId = [" + alinkId + "], aError = [" + getError(aError) + "]");
            }
        });
    }

    /**
     * 上报事件
     */
    public void reportDemoEvent() {
        String identity = "ErrorEvent";

        HashMap<String, ValueWrapper> valueWrapperMap = new HashMap<String, ValueWrapper>();
        ValueWrapper intWrapper = new ValueWrapper.IntValueWrapper(1);
        valueWrapperMap.put("ErrorCode", intWrapper);
        ValueWrapper StringWrapper = new ValueWrapper.StringValueWrapper("hello123");
        valueWrapperMap.put("ErrorDesc", StringWrapper);

        OutputParams params = new OutputParams(valueWrapperMap);

        LinkKit.getInstance().getDeviceThing().thingEventPost(identity, params, new IPublishResourceListener() {
            public void onSuccess(String alinkId, Object o) {
                ALog.d(TAG, "onSuccess() called with: alinkId = [" + alinkId + "], o = [" + o + "]");
            }

            public void onError(String alinkId, AError aError) {
                ALog.w(TAG, "onError() called with: alinkId = [" + alinkId + "], aError = [" + getError(aError) + "]");
            }
        });
    }

    /**
     * 设备端接收服务端的属性下发和服务下发的消息，并作出反馈
     */
    public void setServiceHandler() {
        ALog.d(TAG, "setServiceHandler() called");

        // 初始化 IDE 连接管理器
        ideConnectionManager = new IDEConnectionManager(productKey, deviceName);
        deployManager = new DeployManager(productKey, deviceName);

        List<Service> srviceList = LinkKit.getInstance().getDeviceThing().getServices();
        for (int i = 0; srviceList != null && i < srviceList.size(); i++) {
            Service service = srviceList.get(i);
            LinkKit.getInstance().getDeviceThing().setServiceHandler(service.getIdentifier(), mCommonHandler);
        }
        LinkKit.getInstance().registerOnNotifyListener(connectNotifyListener);
    }

    private ITResRequestHandler mCommonHandler = new ITResRequestHandler() {
        public void onProcess(String identify, Object result, ITResResponseCallback itResResponseCallback) {
            ALog.d(TAG, "onProcess() called with: s = [" + identify + "], o = [" + result + "]");
            try {
                if (SERVICE_SET.equals(identify)) {
                    // 云端下发属性设置
                    if (result instanceof InputParams) {
                        Map<String, ValueWrapper> data = (Map<String, ValueWrapper>) ((InputParams) result).getData();
                        ALog.d(TAG, "收到属性设置下行数据: " + data);
                        reportDemoProperty1(data);
                        itResResponseCallback.onComplete(identify, null, null);
                    } else {
                        itResResponseCallback.onComplete(identify, null, null);
                    }

                } else if (SERVICE_REQUEST_CONNECT.equals(identify)) {
                    // ── 处理 requestConnect 服务 ──────────────────────────────
                    Map<String, ValueWrapper> params = getInputParams(result);
                    ideConnectionManager.handleRequestConnect(params, (success, message) -> {
                        // 构造回复给控制端的输出参数
                        HashMap<String, ValueWrapper> output = new HashMap<>();
                        output.put("success", new ValueWrapper.BooleanValueWrapper(success ? 1 : 0));
                        output.put("message", new ValueWrapper.StringValueWrapper(message));
                        itResResponseCallback.onComplete(identify, null, new OutputParams(output));
                    });

                } else if (SERVICE_REQUEST_DISCONNECT.equals(identify)) {
                    // ── 处理 requestDisconnect 服务 ───────────────────────────
                    Map<String, ValueWrapper> params = getInputParams(result);
                    ideConnectionManager.handleDisconnect(params, (success, message) -> {
                        HashMap<String, ValueWrapper> output = new HashMap<>();
                        output.put("success", new ValueWrapper.BooleanValueWrapper(success ? 1 : 0));
                        output.put("message", new ValueWrapper.StringValueWrapper(message));
                        itResResponseCallback.onComplete(identify, null, new OutputParams(output));
                    });

                } else if (SERVICE_IDE_HEARTBEAT.equals(identify)) {
                    // ── 处理 ideHeartbeat 服务 ────────────────────────────────
                    Map<String, ValueWrapper> params = getInputParams(result);
                    ideConnectionManager.handleHeartbeat(params, (success, message) -> {
                        HashMap<String, ValueWrapper> output = new HashMap<>();
                        output.put("success", new ValueWrapper.BooleanValueWrapper(success ? 1 : 0));
                        output.put("message", new ValueWrapper.StringValueWrapper(message));
                        itResResponseCallback.onComplete(identify, null, new OutputParams(output));
                    });

                } else if (SERVICE_DEPLOY_PROJECT.equals(identify)) {
                    // ── 处理 deployProject 服务（下载 + 构建，不启动）────────
                    Map<String, ValueWrapper> params = getInputParams(result);
                    deployManager.handleDeploy(params, (success, message, deployLog) -> {
                        HashMap<String, ValueWrapper> output = new HashMap<>();
                        output.put("success",   new ValueWrapper.BooleanValueWrapper(success ? 1 : 0));
                        output.put("message",   new ValueWrapper.StringValueWrapper(message));
                        output.put("deployLog", new ValueWrapper.StringValueWrapper(deployLog));
                        itResResponseCallback.onComplete(identify, null, new OutputParams(output));
                    });

                } else if (SERVICE_START_PROJECT.equals(identify)) {
                    // ── 处理 startProject 服务（后台启动，不下载不构建）──────
                    Map<String, ValueWrapper> params = getInputParams(result);
                    deployManager.handleStartProject(params, (success, message) -> {
                        HashMap<String, ValueWrapper> output = new HashMap<>();
                        output.put("success", new ValueWrapper.BooleanValueWrapper(success ? 1 : 0));
                        output.put("message", new ValueWrapper.StringValueWrapper(message));
                        itResResponseCallback.onComplete(identify, null, new OutputParams(output));
                    });

                } else {
                    // 其他未知服务，直接回复成功
                    ALog.d(TAG, "收到未处理的服务: " + identify);
                    itResResponseCallback.onComplete(identify, null, null);
                }

            } catch (Exception e) {
                ALog.e(TAG, "onProcess 异常: " + e.getMessage());
                AError error = new AError();
                error.setCode(500);
                error.setMsg(e.getMessage());
                itResResponseCallback.onComplete(identify, new ErrorInfo(error), null);
            }
        }

        public boolean shouldHandle(String s, String s1) {
            return true;
        }

        public void onConnectStateChange(String s, ConnectState connectState) {
        }

        @Override
        public void onSuccess(Object o, OutputParams outputParams) {
            ALog.d(TAG, "onSuccess() called with: o = [" + o + "], outputParams = [" + outputParams + "]");
            ALog.d(TAG, "注册服务成功");
        }

        @Override
        public void onFail(Object o, ErrorInfo errorInfo) {
            ALog.w(TAG, "onFail() called with: o = [" + o + "], errorInfo = [" + errorInfo + "]");
            ALog.d(TAG, "注册服务失败");
        }
    };

    private IConnectNotifyListener connectNotifyListener = new IConnectNotifyListener() {
        @Override
        public void onNotify(String connectId, String topic, AMessage aMessage) {
            ALog.d(TAG, "onNotify() called with: connectId = [" + connectId + "], topic = [" + topic + "], aMessage = [" + printAMessage(aMessage) + "]");
            try {
                if (CONNECT_ID.equals(connectId) && !StringUtils.isEmptyString(topic)) {
                    ALog.d(TAG, "收到下行指令" + printAMessage(aMessage));
                    String s = printAMessage(aMessage);
                    JSONObject jsonObject = JSON.parseObject(s);

                    if (StrUtil.endWith(topic, "thing/service/property/set")) {
                        JSONObject params = jsonObject.getJSONObject("params");
                        Set<String> strings = params.keySet();
                        Map<String, ValueWrapper> reportData = new HashMap<>();
                        strings.forEach(key -> reportData.put(key, new ValueWrapper(params.get(key))));
                        reportDemoProperty1(reportData);
                    }
                }
            } catch (Exception e) {
                ALog.e(TAG, "onNotify 异常: " + e.getMessage());
            }
        }

        @Override
        public boolean shouldHandle(String s, String s1) {
            return true;
        }

        @Override
        public void onConnectStateChange(String s, ConnectState connectState) {
        }
    };

    // -------------------------------------------------------------------------
    // 工具
    // -------------------------------------------------------------------------

    /**
     * 从 onProcess 的 result 对象中提取 InputParams 的 data map
     */
    @SuppressWarnings("unchecked")
    private Map<String, ValueWrapper> getInputParams(Object result) {
        if (result instanceof InputParams) {
            Object data = ((InputParams) result).getData();
            if (data instanceof Map) {
                return (Map<String, ValueWrapper>) data;
            }
        }
        return new HashMap<>();
    }

    private String printAMessage(AMessage aMessage) {
        if (aMessage == null || aMessage.data == null) return "";
        try {
            return new String((byte[]) aMessage.data);
        } catch (Exception e) {
            return aMessage.data.toString();
        }
    }
}
