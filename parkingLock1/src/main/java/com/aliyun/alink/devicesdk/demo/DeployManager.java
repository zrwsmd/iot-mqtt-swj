package com.aliyun.alink.devicesdk.demo;

import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tmp.listener.IPublishResourceListener;
import com.aliyun.alink.linksdk.tools.AError;
import com.aliyun.alink.linksdk.tools.ALog;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 项目部署管理器
 *
 * 负责处理控制端下发的 deployProject 服务调用，完整流程：
 *   1. 从 OSS 下载 zip 压缩包
 *   2. 解压到指定目录
 *   3. 执行部署命令
 *   4. 上报 deployStatus 属性到云端
 *
 * 使用方式（在 ThingSample 的 onProcess 里调用）：
 *   DeployManager deployManager = new DeployManager(pk, dn);
 *   deployManager.handleDeploy(params, callback);
 */
public class DeployManager extends BaseSample {

    private static final String TAG = "DeployManager";

    // 物模型属性标识符
    public static final String PROP_DEPLOY_STATUS = "deployStatus";

    // 下载超时（毫秒）
    private static final int DOWNLOAD_TIMEOUT_MS = 60 * 1000;

    // 部署命令执行超时（毫秒）
    private static final int DEPLOY_TIMEOUT_MS = 10 * 60 * 1000;

    // 异步执行线程池（单线程，避免并发部署）
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "deploy-worker");
                t.setDaemon(true);
                return t;
            });

    public DeployManager(String pk, String dn) {
        super(pk, dn);
    }

    // -------------------------------------------------------------------------
    // 服务处理入口（在 ThingSample.onProcess 中调用）
    // -------------------------------------------------------------------------

    /**
     * 处理 deployProject 服务调用
     *
     * 控制端传入的 params：
     * {
     *   "projectName":   "my-project",
     *   "downloadUrl":   "https://oss.xxx.com/xxx.zip?sign=xxx",
     *   "deployPath":    "/home/user/projects",
     *   "deployCommand": "npm install && npm run build"
     * }
     *
     * 回调给控制端的结果：
     * {
     *   "success":   true/false,
     *   "message":   "部署成功" / "部署失败：xxx",
     *   "deployLog": "完整日志..."
     * }
     */
    public void handleDeploy(Map<String, ValueWrapper> params, DeployCallback callback) {
        String projectName   = getStringParam(params, "projectName",   "project");
        String downloadUrl   = getStringParam(params, "downloadUrl",   "");
        String deployPath    = getStringParam(params, "deployPath",    "/tmp/deploy");
        String deployCommand = getStringParam(params, "deployCommand", "");

        ALog.i(TAG, "收到 deployProject 请求:"
                + " projectName=" + projectName
                + " deployPath=" + deployPath
                + " deployCommand=" + deployCommand);

        if (downloadUrl.isEmpty()) {
            String msg = "部署失败：downloadUrl 不能为空";
            ALog.e(TAG, msg);
            reportDeployStatus(false, msg, "", projectName, deployPath);
            callback.onResult(false, msg, "");
            return;
        }

        // 先立即回复控制端（异步部署中），避免服务调用超时
        callback.onResult(true, "部署任务已接收，正在执行...", "");

        // 异步执行部署，完成后上报 deployStatus 属性
        executor.submit(() -> {
            StringBuilder log = new StringBuilder();
            try {
                // 1. 下载 zip
                log.append("=== 开始下载 ===\n");
                reportDeployStatus(false, "下载中...", log.toString(), projectName, deployPath);
                String zipFilePath = downloadZip(downloadUrl, projectName, log);
                log.append("下载完成: ").append(zipFilePath).append("\n\n");

                // 2. 解压
                log.append("=== 开始解压 ===\n");
                reportDeployStatus(false, "解压中...", log.toString(), projectName, deployPath);
                String targetPath = deployPath + File.separator + projectName;
                extractZip(zipFilePath, targetPath, log);
                log.append("解压完成: ").append(targetPath).append("\n\n");

                // 3. 清理 zip 临时文件
                new File(zipFilePath).delete();
                ALog.d(TAG, "临时文件已删除: " + zipFilePath);

                // 4. 执行部署命令
                if (!deployCommand.isEmpty()) {
                    log.append("=== 执行部署命令 ===\n");
                    log.append("$ ").append(deployCommand).append("\n");
                    reportDeployStatus(false, "部署命令执行中...", log.toString(), projectName, targetPath);
                    runCommand(deployCommand, targetPath, log);
                    log.append("\n部署命令执行完成\n");
                }

                // 5. 上报成功
                String successMsg = "部署成功";
                log.append("\n=== ✅ 部署成功 ===\n");
                ALog.i(TAG, successMsg + ": " + projectName);
                reportDeployStatus(true, successMsg, log.toString(), projectName, targetPath);

            } catch (Exception e) {
                String failMsg = "部署失败：" + e.getMessage();
                log.append("\n=== ❌ 部署失败 ===\n").append(e.getMessage()).append("\n");
                ALog.e(TAG, failMsg);
                reportDeployStatus(false, failMsg, log.toString(), projectName, deployPath);
            }
        });
    }

    // -------------------------------------------------------------------------
    // 下载
    // -------------------------------------------------------------------------

    /**
     * 从 OSS 签名 URL 下载 zip 到临时目录
     */
    private String downloadZip(String downloadUrl, String projectName, StringBuilder log) throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String zipFileName = projectName + "-" + System.currentTimeMillis() + ".zip";
        String zipFilePath = tmpDir + File.separator + zipFileName;

        ALog.i(TAG, "开始下载: " + downloadUrl + " -> " + zipFilePath);
        log.append("下载地址: ").append(downloadUrl).append("\n");
        log.append("保存路径: ").append(zipFilePath).append("\n");

        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(DOWNLOAD_TIMEOUT_MS);
        conn.setReadTimeout(DOWNLOAD_TIMEOUT_MS);
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("下载失败，HTTP 状态码: " + responseCode);
        }

        long totalBytes = conn.getContentLengthLong();
        long downloadedBytes = 0;

        try (InputStream is = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(zipFilePath)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
                downloadedBytes += len;
            }
        } finally {
            conn.disconnect();
        }

        log.append("下载大小: ").append(downloadedBytes / 1024).append(" KB\n");
        ALog.i(TAG, "下载完成，大小: " + downloadedBytes / 1024 + " KB");
        return zipFilePath;
    }

    // -------------------------------------------------------------------------
    // 解压
    // -------------------------------------------------------------------------

    /**
     * 解压 zip 到目标目录（先清空旧目录）
     */
    private void extractZip(String zipFilePath, String targetPath, StringBuilder log) throws IOException {
        File targetDir = new File(targetPath);

        // 清空旧目录
        if (targetDir.exists()) {
            deleteDirectory(targetDir);
            log.append("已清空旧目录: ").append(targetPath).append("\n");
        }
        targetDir.mkdirs();

        log.append("解压目标: ").append(targetPath).append("\n");
        int fileCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File entryFile = new File(targetPath, entry.getName());

                // 安全检查：防止 zip slip 攻击
                if (!entryFile.getCanonicalPath().startsWith(targetDir.getCanonicalPath())) {
                    throw new IOException("非法路径: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    fileCount++;
                }
                zis.closeEntry();
            }
        }

        log.append("解压文件数: ").append(fileCount).append(" 个\n");
        ALog.i(TAG, "解压完成，共 " + fileCount + " 个文件");
    }

    // -------------------------------------------------------------------------
    // 执行命令
    // -------------------------------------------------------------------------

    /**
     * 在指定目录下执行 shell 命令，捕获输出
     */
    private void runCommand(String command, String workDir, StringBuilder log) throws IOException, InterruptedException {
        ALog.i(TAG, "执行命令: " + command + " (工作目录: " + workDir + ")");

        ProcessBuilder pb = new ProcessBuilder();

        // 判断操作系统
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("/bin/sh", "-c", command);
        }

        pb.directory(new File(workDir));
        pb.redirectErrorStream(true); // 合并 stdout 和 stderr

        Process process = pb.start();

        // 读取输出
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
                ALog.d(TAG, "[CMD] " + line);
            }
        }

        // 等待执行完成
        boolean finished = process.waitFor(DEPLOY_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("部署命令执行超时（超过 " + DEPLOY_TIMEOUT_MS / 1000 + " 秒）");
        }

        int exitCode = process.exitValue();
        log.append("退出码: ").append(exitCode).append("\n");

        if (exitCode != 0) {
            throw new IOException("部署命令执行失败，退出码: " + exitCode);
        }

        ALog.i(TAG, "命令执行成功，退出码: " + exitCode);
    }

    // -------------------------------------------------------------------------
    // 上报部署状态
    // -------------------------------------------------------------------------

    /**
     * 上报 deployStatus 属性到云端
     */
    private void reportDeployStatus(boolean success, String message, String deployLog,
                                    String projectName, String deployPath) {
        // 构造 JSON（手动拼接，避免引入额外依赖）
        String statusJson = "{"
                + "\"success\":" + success + ","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"deployLog\":\"" + escapeJson(deployLog) + "\","
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"projectName\":\"" + escapeJson(projectName) + "\","
                + "\"deployPath\":\"" + escapeJson(deployPath) + "\""
                + "}";

        Map<String, ValueWrapper> props = new HashMap<>();
        props.put(PROP_DEPLOY_STATUS, new ValueWrapper.StringValueWrapper(statusJson));

        LinkKit.getInstance().getDeviceThing().thingPropertyPost(props,
                new IPublishResourceListener() {
                    @Override
                    public void onSuccess(String alinkId, Object o) {
                        ALog.d(TAG, "deployStatus 上报成功");
                    }

                    @Override
                    public void onError(String alinkId, AError aError) {
                        ALog.e(TAG, "deployStatus 上报失败: "
                                + (aError == null ? "null" : aError.getMsg()));
                    }
                });
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
     * 递归删除目录
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        dir.delete();
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 关闭线程池（上位机关闭时调用）
     */
    public void shutdown() {
        executor.shutdown();
        ALog.i(TAG, "DeployManager 已关闭");
    }

    // -------------------------------------------------------------------------
    // 回调接口
    // -------------------------------------------------------------------------

    /**
     * 部署结果回调（立即返回给控制端，实际部署异步进行）
     */
    public interface DeployCallback {
        /**
         * @param success   是否已接收（注意：true 只代表任务已接收，不代表部署完成）
         * @param message   返回给控制端的描述信息
         * @param deployLog 当前日志（异步部署时为空）
         */
        void onResult(boolean success, String message, String deployLog);
    }
}
