package com.fandogh.shekan;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private boolean isConnected = false;
    private Button btnConnect;
    private ConfigManager configManager;
    private PingManager pingManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        btnConnect = findViewById(R.id.btnConnect);
        configManager = new ConfigManager();
        pingManager = new PingManager();

        btnConnect.setOnClickListener(v -> {
            if (!isConnected) {
                startFetchingConfig();
            } else {
                stopFandoghShekan();
            }
        });
    }

    private void startFetchingConfig() {
        btnConnect.setText("در حال دریافت کانفیگ...");
        btnConnect.setBackgroundColor(0xFF2196F3); // آبی
        btnConnect.setEnabled(false);

        configManager.fetchAndDecryptConfig(new ConfigManager.ConfigCallback() {
            @Override
            public void onSuccess(String decryptedConfig) {
                btnConnect.setText("در حال تست پینگ سرور...");
                parseAndPing(decryptedConfig);
            }

            @Override
            public void onError(String error) {
                resetButton("خطا در شبکه!", 0xFFF44336);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void parseAndPing(String config) {
        try {
            // ۱. پاکسازی کامل تمام فاصله‌ها و خطوط اضافه احتمالی از ابتدا و انتهای کانفیگ
            if (config == null) throw new Exception("کانفیگ خالی است");
            config = config.trim();
            
            if (!config.startsWith("vless://")) {
                throw new Exception("لینک vless معتبر نیست");
            }
            
            // ۲. حذف پروتکل ابتدایی
            String uriBody = config.substring(8); 
            
            // ۳. پیدا کردن علامت @ برای استخراج بخش سرور (از آخر به اول برای امنیت بیشتر)
            int atIndex = uriBody.lastIndexOf("@");
            if (atIndex == -1) throw new Exception("علامت @ پیدا نشد");
            
            String serverPart = uriBody.substring(atIndex + 1);
            
            // ۴. جدا کردن آدرس و پورت از بخش کوئری‌ها (مثل ?type=ws یا #remarks)
            String[] mainParts = serverPart.split("[?#]");
            String hostAndPort = mainParts[0];
            
            // ۵. تفکیک Host و Port با پیدا کردن آخرین دو نقطه (:)
            int colonIndex = hostAndPort.lastIndexOf(":");
            if (colonIndex == -1) throw new Exception("پورت سرور پیدا نشد");
            
            String host = hostAndPort.substring(0, colonIndex).trim();
            String portStr = hostAndPort.substring(colonIndex + 1).trim();
            
            int port = Integer.parseInt(portStr);

            // شروع عملیات پینگ روی پورت و هاست استخراج شده
            pingManager.checkTcpPing(host, port, new PingManager.PingCallback() {
                @Override
                public void onResult(long latencyMs) {
                    Toast.makeText(MainActivity.this, "پینگ سرور: " + latencyMs + "ms", Toast.LENGTH_SHORT).show();
                    
                    // درخواست مجوز رسمی VPN از سیستم‌عامل
                    Intent intent = VpnService.prepare(MainActivity.this);
                    if (intent != null) {
                        startActivityForResult(intent, 0);
                    } else {
                        onActivityResult(0, RESULT_OK, null);
                    }
                }

                @Override
                public void onError(String error) {
                    resetButton("سرور قطع است (Timeout)", 0xFFE91E63);
                }
            });

        } catch (Exception e) {
            // حالا اگر مشکلی پیش بیاید، دلیل دقیق را روی دکمه مینویسد تا بفهمیم ایراد کجاست
            resetButton("خطا: " + e.getMessage(), 0xFFF44336);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, FandoghVpnService.class);
            startService(intent);
            
            btnConnect.setEnabled(true);
            btnConnect.setText("فندق‌شکن فعال است 🛡️");
            btnConnect.setBackgroundColor(0xFF4CAF50); // سبز
            isConnected = true;
        } else {
            resetButton("عدم تایید مجوز VPN", 0xFFF44336);
        }
    }

    private void stopFandoghShekan() {
        Intent intent = new Intent(this, FandoghVpnService.class);
        intent.setAction("STOP");
        startService(intent);
        resetButton("اتصال هوشمند", 0xFFFF9800); // نارنجی
    }

    private void resetButton(String text, int color) {
        btnConnect.setEnabled(true);
        btnConnect.setText(text);
        btnConnect.setBackgroundColor(color);
        isConnected = false;
    }
}
