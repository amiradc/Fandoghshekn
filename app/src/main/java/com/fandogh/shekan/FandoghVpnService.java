package com.fandogh.shekan;

import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class FandoghVpnService extends VpnService implements Runnable {
    private static final String TAG = "FandoghVpnService";
    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    private Process mXrayProcess;
    private Process mTun2SocksProcess;
    private String mVlessLink;

    private void showStatus(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show()
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("STOP".equals(intent.getAction())) {
                stopVpn();
                return START_NOT_STICKY;
            }
            mVlessLink = intent.getStringExtra("VLESS_LINK");
        }
        if (mThread != null && mThread.isAlive()) {
            stopVpn();
        }
        mThread = new Thread(this, "FandoghVpnThread");
        mThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void run() {
        try {
            showStatus("🔍 گام ۱: بارگذاری هسته ایمن Xray...");
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            File xrayBin = new File(nativeDir, "libxray.so");
            File tun2socksBin = new File(nativeDir, "libtun2socks.so");

            if (!xrayBin.exists()) {
                throw new Exception("هسته سیستمی یافت نشد!");
            }

            showStatus("⚙️ گام ۲: تبدیل لینک به فایل کانفیگ...");
            File baseDir = getFilesDir();
            if (mVlessLink != null && mVlessLink.startsWith("vless://")) {
                generateXrayConfigManual(mVlessLink, baseDir);
            } else {
                throw new Exception("لینک VLESS نامعتبر است!");
            }

            showStatus("🌐 گام ۳: ایجاد تونل کامل شبکه...");
            Builder builder = new Builder();
            builder.setSession("FandoghShekan")
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0) // 🔥 خفت کردن کل اینترنت گوشی
                    .addDnsServer("8.8.8.8") // ⚡ حل مشکل DNS
                    .addDisallowedApplication(getPackageName());

            mInterface = builder.establish();
            if (mInterface == null) {
                throw new Exception("سیستم‌عامل اجازه ایجاد تونل را نداد!");
            }
            int tunFd = mInterface.getFd();

            showStatus("🚀 پرتاب هسته Xray...");
            String[] xrayCmd = {xrayBin.getAbsolutePath(), "run", "-config", new File(baseDir, "config.json").getAbsolutePath()};
            mXrayProcess = Runtime.getRuntime().exec(xrayCmd);

            // 🛠️ جادو: اگر فایل مترجم وجود دارد، آن را روشن کن تا لوله‌کشی کامل شود
            if (tun2socksBin.exists()) {
                String[] t2sCmd = {
                    tun2socksBin.getAbsolutePath(),
                    "-device", "fd://" + tunFd,
                    "-proxy", "socks5://127.0.0.1:10808"
                };
                mTun2SocksProcess = Runtime.getRuntime().exec(t2sCmd);
            } else {
                Log.e(TAG, "فایل libtun2socks.so هنوز اضافه نشده است!");
            }

            while (mThread != null && !mThread.isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            showStatus("🛑 فندق‌شکن قطع شد.");
        } catch (Exception e) {
            Log.e(TAG, "خطای جدی", e);
            showStatus("❌ خطا: " + e.getMessage());
        } finally {
            stopVpn();
        }
    }

    private void generateXrayConfigManual(String link, File dir) throws Exception {
        String current = link.substring(8);
        String[] hashSplit = current.split("#", 2);
        String mainPart = hashSplit[0];
        String[] querySplit = mainPart.split("\\?", 2);
        String credentialsAndServer = querySplit[0];
        String queryString = querySplit.length > 1 ? querySplit[1] : "";
        
        int atIdx = credentialsAndServer.lastIndexOf("@");
        if (atIdx == -1) throw new Exception("فرمت کانفیگ اشتباه است (@ ندارد)");
        String uuid = credentialsAndServer.substring(0, atIdx);
        String serverPart = credentialsAndServer.substring(atIdx + 1);
        
        int colonIdx = serverPart.lastIndexOf(":");
        if (colonIdx == -1) throw new Exception("پورت سرور پیدا نشد");
        String host = serverPart.substring(0, colonIdx).trim();
        int port = Integer.parseInt(serverPart.substring(colonIdx + 1).trim());
        
        Map<String, String> params = new HashMap<>();
        if (!queryString.isEmpty()) {
            for (String pair : queryString.split("&")) {
                int idx = pair.indexOf("=");
                if (idx != -1) {
                    params.put(pair.substring(0, idx), java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                }
            }
        }
        
        String security = params.getOrDefault("security", "none");
        String network = params.getOrDefault("type", "tcp");
        String sni = params.getOrDefault("sni", params.getOrDefault("host", host));
        String path = params.getOrDefault("path", "/");
        String pbk = params.getOrDefault("pbk", "");
        String sid = params.getOrDefault("sid", "");
        String fp = params.getOrDefault("fp", "chrome");

        StringBuilder streamStr = new StringBuilder();
        streamStr.append("{\n")
                 .append("  \"network\": \"").append(network).append("\",\n")
                 .append("  \"security\": \"").append(security).append("\"");

        if ("reality".equals(security)) {
            streamStr.append(",\n  \"realitySettings\": {\n")
                     .append("    \"show\": false,\n")
                     .append("    \"fingerprint\": \"").append(fp).append("\",\n")
                     .append("    \"serverName\": \"").append(sni).append("\",\n")
                     .append("    \"publicKey\": \"").append(pbk).append("\",\n")
                     .append("    \"shortId\": \"").append(sid).append("\"\n")
                     .append("  }");
        } else if ("tls".equals(security)) {
            streamStr.append(",\n  \"tlsSettings\": {\n")
                     .append("    \"serverName\": \"").append(sni).append("\",\n")
                     .append("    \"fingerprint\": \"").append(fp).append("\"\n")
                     .append("  }");
        }

        if ("ws".equals(network)) {
            streamStr.append(",\n  \"wsSettings\": {\n")
                     .append("    \"path\": \"").append(path).append("\",\n")
                     .append("    \"headers\": {\"Host\": \"").append(sni).append("\"}\n")
                     .append("  }");
        } else if ("grpc".equals(network)) {
            String serviceName = params.getOrDefault("serviceName", "");
            streamStr.append(",\n  \"grpcSettings\": {\n")
                     .append("    \"serviceName\": \"").append(serviceName).append("\"\n")
                     .append("  }");
        }
        streamStr.append("\n}");

        String json = "{\n" +
                "  \"log\": {\"loglevel\": \"warning\"},\n" +
                "  \"inbounds\": [\n" +
                "    {\"port\": 10808, \"protocol\": \"socks\", \"settings\": {\"auth\": \"noauth\", \"udp\": true}}\n" +
                "  ],\n" +
                "  \"outbounds\": [{\n" +
                "    \"protocol\": \"vless\",\n" +
                "    \"settings\": {\"vnext\": [{\"address\": \"" + host + "\", \"port\": " + port + ", \"users\": [{\"id\": \"" + uuid + "\", \"encryption\": \"none\"}]}]},\n" +
                "    \"streamSettings\": " + streamStr.toString() + "\n" +
                "  }]\n" +
                "}";

        FileOutputStream fos = new FileOutputStream(new File(dir, "config.json"));
        fos.write(json.getBytes());
        fos.flush(); fos.close();
    }

    private void stopVpn() {
        try {
            if (mTun2SocksProcess != null) { mTun2SocksProcess.destroy(); mTun2SocksProcess = null; }
            if (mXrayProcess != null) { mXrayProcess.destroy(); mXrayProcess = null; }
            if (mThread != null) { mThread.interrupt(); mThread = null; }
            if (mInterface != null) { mInterface.close(); mInterface = null; }
        } catch (Exception e) { Log.e(TAG, "خطا در توقف: " + e.getMessage()); }
    }
}
