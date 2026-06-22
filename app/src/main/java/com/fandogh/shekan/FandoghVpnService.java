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

    // نمایش مستقیم ارورها روی صفحه گوشی بدون نیاز به کامپیوتر
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
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            File xrayBin = new File(nativeDir, "libxray.so");
            File tun2socksBin = new File(nativeDir, "libtun2socks.so");

            if (!xrayBin.exists()) throw new Exception("هسته Xray یافت نشد!");

            File baseDir = getFilesDir();
            if (mVlessLink != null && mVlessLink.startsWith("vless://")) {
                generateXrayConfigManual(mVlessLink, baseDir);
            } else {
                throw new Exception("لینک VLESS نامعتبر است!");
            }

            Builder builder = new Builder();
            builder.setSession("FandoghShekan")
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDisallowedApplication(getPackageName());

            mInterface = builder.establish();
            if (mInterface == null) throw new Exception("تونل VPN ایجاد نشد!");
            int tunFd = mInterface.getFd();

            // 🔓 هک سیستمی: شکستن قفل CLOEXEC اندروید برای مجاز کردن Tun2Socks به استفاده از تونل
            try {
                android.system.Os.fcntlInt(mInterface.getFileDescriptor(), android.system.OsConstants.F_SETFD, 0);
            } catch (Exception e) {
                Log.e(TAG, "خطا در باز کردن قفل تونل", e);
            }

            // 🚀 روشن کردن Xray
            String[] xrayCmd = {xrayBin.getAbsolutePath(), "run", "-config", new File(baseDir, "config.json").getAbsolutePath()};
            mXrayProcess = Runtime.getRuntime().exec(xrayCmd);
            pipeLogsToScreen(mXrayProcess, "Xray");

            // 🛠️ روشن کردن Tun2Socks
            if (tun2socksBin.exists()) {
                String[] t2sCmd = {
                    tun2socksBin.getAbsolutePath(),
                    "-device", "fd://" + tunFd,
                    "-proxy", "socks5://127.0.0.1:10808"
                };
                mTun2SocksProcess = Runtime.getRuntime().exec(t2sCmd);
                pipeLogsToScreen(mTun2SocksProcess, "Tun2Socks");
            } else {
                showStatus("❌ فایل لایه مترجم شبکه (Tun2Socks) یافت نشد!");
            }

            showStatus("🚀 فندق‌شکن فعال شد. در حال مانیتور جریان شبکه...");

            while (mThread != null && !mThread.isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            showStatus("🛑 فندق‌شکن قطع شد.");
        } catch (Exception e) {
            showStatus("❌ خطای سیستم: " + e.getMessage());
        } finally {
            stopVpn();
        }
    }

    // 📻 لوله‌کشی جریان ارورها مستقیماً به روی اسکرین گوشی
    private void pipeLogsToScreen(Process process, String processName) {
        if (process == null) return;
        
        // شنود خروجی‌های استاندارد اِررور
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                int count = 0;
                while ((line = r.readLine()) != null && count < 3) {
                    // فرستادن اِررورهای حیاتی اولیه به صورت پاپ‌آپ روی گوشی
                    if (line.toLowerCase().contains("fail") || line.toLowerCase().contains("err") || line.toLowerCase().contains("fatal")) {
                        showStatus("⚠️ [" + processName + "]: " + line);
                        count++; // برای اینکه صفحه پر از توست نشود، فقط ارورهای اولیه را نشان بده
                    }
                }
            } catch (Exception ignored) {}
        }).start();
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
        if (colonIdx == -1) throw new Exception("پورت سرور یافت نشد");
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
        String flow = params.getOrDefault("flow", ""); 

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

        String userSettings = "{\"id\": \"" + uuid + "\", \"encryption\": \"none\"" + 
                (!flow.isEmpty() ? ", \"flow\": \"" + flow + "\"" : "") + "}";

        String json = "{\n" +
                "  \"log\": {\"loglevel\": \"warning\"},\n" +
                "  \"inbounds\": [\n" +
                "    {\"port\": 10808, \"protocol\": \"socks\", \"settings\": {\"auth\": \"noauth\", \"udp\": true}}\n" +
                "  ],\n" +
                "  \"outbounds\": [{\n" +
                "    \"protocol\": \"vless\",\n" +
                "    \"settings\": {\"vnext\": [{\"address\": \"" + host + "\", \"port\": " + port + ", \"users\": [" + userSettings + "]}]},\n" +
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
