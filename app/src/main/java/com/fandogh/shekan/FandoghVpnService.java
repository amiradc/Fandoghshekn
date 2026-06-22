package com.fandogh.shekan;

import android.content.Intent;
import android.net.ProxyInfo;
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
            showStatus("🌐 گام ۳: در حال فعال‌سازی تونل سیستمی...");
            Builder builder = new Builder();
            builder.setSession("FandoghShekan")
                    .addAddress("10.0.0.2", 24)
                    .addDisallowedApplication(getPackageName());

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", 10809));
            }

            mInterface = builder.establish();
            if (mInterface == null) {
                throw new Exception("سیستم‌عامل اجازه ایجاد تونل را نداد!");
            }
            showStatus("🚀 فندق‌شکن متصل شد.");
            
            String[] cmd = {xrayBin.getAbsolutePath(), "run", "-config", new File(baseDir, "config.json").getAbsolutePath()};
            mXrayProcess = Runtime.getRuntime().exec(cmd);
            
            // ذخیره آنلاین ارورهای هسته برای نمایش به کاربر در صورت کرش
            final StringBuilder xrayErrors = new StringBuilder();
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(mXrayProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        xrayErrors.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            errorReader.start();

            // ۱.۵ ثانیه صبر می‌کنیم تا ببینیم هسته پایدار می‌ماند یا فوراً کرش می‌کند
            Thread.sleep(1500);
            try {
                int exitCode = mXrayProcess.exitValue();
                // اگر به این خط برسیم یعنی هسته زنده نمانده و متوقف شده است
                String coreError = xrayErrors.toString().trim();
                if (coreError.isEmpty()) {
                    coreError = "هسته بدون لاگ متوقف شد. کد خروج: " + exitCode;
                }
                throw new Exception(coreError);
            } catch (IllegalThreadStateException e) {
                // این یعنی فرآیند هسته با موفقیت در حال اجراست و کرش نکرده است
            }

            while (mThread != null && !mThread.isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            showStatus("🛑 فندق‌شکن قطع شد.");
        } catch (Exception e) {
            Log.e(TAG, "خطای جدی در فرآیند اتصال هسته", e);
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
        Map<String, String> queryPairs = new HashMap<>();
        if (!queryString.isEmpty()) {
            for (String pair : queryString.split("&")) {
                int idx = pair.indexOf("=");
                if (idx != -1) queryPairs.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        String path = queryPairs.containsKey("path") ? java.net.URLDecoder.decode(queryPairs.get("path"), "UTF-8") : "/";
        String sni = queryPairs.containsKey("host") ? queryPairs.get("host") : host;
        
        String json = "{\n" +
                "  \"log\": {\"loglevel\": \"info\"},\n" +
                "  \"inbounds\": [\n" +
                "    {\"port\": 10808, \"protocol\": \"socks\", \"settings\": {\"auth\": \"noauth\", \"udp\": true}},\n" +
                "    {\"port\": 10809, \"protocol\": \"http\", \"settings\": {}}\n" +
                "  ],\n" +
                "  \"outbounds\": [{\n" +
                "    \"protocol\": \"vless\",\n" +
                "    \"settings\": {\"vnext\": [{\"address\": \"" + host + "\", \"port\": " + port + ", \"users\": [{\"id\": \"" + uuid + "\", \"encryption\": \"none\"}]}]},\n" +
                "    \"streamSettings\": {\"network\": \"ws\", \"security\": \"none\", \"wsSettings\": {\"path\": \"" + path + "\", \"headers\": {\"Host\": \"" + sni + "\"}}}\n" +
                "  }]\n" +
                "}";
        FileOutputStream fos = new FileOutputStream(new File(dir, "config.json"));
        fos.write(json.getBytes());
        fos.flush(); fos.close();
    }

    private void stopVpn() {
        try {
            if (mXrayProcess != null) { mXrayProcess.destroy(); mXrayProcess = null; }
            if (mThread != null) { mThread.interrupt(); mThread = null; }
            if (mInterface != null) { mInterface.close(); mInterface = null; }
        } catch (Exception e) { Log.e(TAG, "خطا در توقف: " + e.getMessage()); }
    }
}
