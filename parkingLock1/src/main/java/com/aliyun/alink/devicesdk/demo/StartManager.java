package com.aliyun.alink.devicesdk.demo;

import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tools.ALog;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 项目启动管理器
 *
 * 负责处理控制端下发的 startProject 服务调用：
 *   在指定目录后台执行启动命令，不下载不构建
 *
 * 使用方式（在 ThingSample 的 onProcess 里调用）：
 *   StartManager startManager = new StartManager(pk, dn);
 *   startManager.handleStartProject(params, callback);
 */
public class StartManager extends BaseSample {

    private static final String TAG = "StartManager";

    // 异步执行线程池（单线程，避免并发启动）
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "start-worker");
                t.setDaemon(true);
                return t;
            });

    public StartManager(String pk, String dn) {
        super(pk, dn);
    }

    // -------------------------------------------------------------------------
    // 服务处理入口（在 ThingSample.onProcess 中调用）
    // -------------------------------------------------------------------------

    /**
     * 处理 startProject 服务调用
     * 职责：在指定目录后台执行 startCommand，不下载不构建
     *
     * 控制端传入的 params：
     * {
     *   "projectName":  "my-project",
     *   "deployPath":   "/home/user/projects",
     *   "startCommand": "pm2 start dist/index.js --name my-project"
     * }
     *
     * 回调给控制端的结果：
     * {
     *   "success": true/false,
     *   "message": "service started in background / start failed: xxx"
     * }
     */
    public void handleStartProject(Map<String, ValueWrapper> params, StartProjectCallback callback) {
        String projectName  = getStringParam(params, "projectName",  "project");
        String deployPath   = getStringParam(params, "deployPath",   "/tmp/deploy");
        String startCommand = getStringParam(params, "startCommand", "");

        String targetPath = deployPath + File.separator + projectName;

        ALog.i(TAG, "received startProject:"
                + " projectName=" + projectName
                + " targetPath=" + targetPath
                + " startCommand=" + startCommand);

        if (startCommand.isEmpty()) {
            String msg = "start failed: startCommand is empty";
            ALog.e(TAG, msg);
            callback.onResult(false, msg);
            return;
        }

        // 提交到线程池，runBackground 最多等 2 秒即返回
        executor.submit(() -> {
            StringBuilder log = new StringBuilder();
            try {
                log.append("=== start service ===\n");
                log.append("$ ").append(startCommand).append("\n");
                runBackground(startCommand, targetPath, log);
                ALog.i(TAG, "startProject success: " + projectName);
                callback.onResult(true, "service started in background");
            } catch (Exception e) {
                ALog.e(TAG, "startProject failed: " + e.getMessage());
                callback.onResult(false, "start failed: " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // 执行命令
    // -------------------------------------------------------------------------

    private void runBackground(String command, String workDir, StringBuilder log) throws IOException, InterruptedException {
        ALog.i(TAG, "run background: " + command);

        ProcessBuilder pb = new ProcessBuilder();
        // Linux 用 bash -l（login shell）加载 ~/.profile，确保 nvm 等工具的 PATH 可见
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("/bin/bash", "-l", "-c", command);
        }

        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 等 2 秒，看进程是否立即退出（命令不存在会立即退出）
        boolean exited = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        if (exited) {
            // 2 秒内就退出了，读取输出看报错原因
            String output = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), "UTF-8"))
                    .lines().collect(java.util.stream.Collectors.joining("\n"));
            int exitCode = process.exitValue();
            log.append("start command exited immediately, exit code: ").append(exitCode).append("\n");
            if (!output.isEmpty()) {
                log.append("output: ").append(output).append("\n");
            }
            // 退出码非 0 才算启动失败
            if (exitCode != 0) {
                throw new IOException("start command failed immediately, exit code: " + exitCode + ", output: " + output);
            }
        } else {
            // 2 秒后还在跑，认为后台启动成功
            log.append("service started in background\n");
            ALog.i(TAG, "background process running");
        }
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    private String getStringParam(Map<String, ValueWrapper> params, String key, String defaultVal) {
        if (params == null || !params.containsKey(key)) return defaultVal;
        ValueWrapper w = params.get(key);
        if (w == null || w.getValue() == null) return defaultVal;
        return w.getValue().toString();
    }

    /**
     * 关闭线程池（上位机关闭时调用）
     */
    public void shutdown() {
        executor.shutdown();
        ALog.i(TAG, "StartManager shutdown");
    }

    // -------------------------------------------------------------------------
    // 回调接口
    // -------------------------------------------------------------------------

    /**
     * startProject 启动结果回调
     */
    public interface StartProjectCallback {
        /**
         * @param success 是否启动成功
         * @param message 返回给控制端的描述信息
         */
        void onResult(boolean success, String message);
    }
}
