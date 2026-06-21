package com.fandogh.shekan;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private boolean isConnected = false;
    private Button btnConnect;
    private ConfigManager configManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        btnConnect = findViewById(R.id.btnConnect);
        configManager = new ConfigManager();

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
        btnConnect.setBackgroundColor(0xFF2196F3); // آبی رنگ
        btnConnect.setEnabled(false);

        configManager.fetchAndDecryptConfig(new ConfigManager.ConfigCallback() {
            @Override
            public void onSuccess(String decryptedConfig) {
                // کانفیگ با موفقیت دریافت و رمزگشایی شد!
                btnConnect.setEnabled(true);
                btnConnect.setText("متصل شد 🥜");
                btnConnect.setBackgroundColor(0xFF4CAF50); // سبز رنگ
                isConnected = true;
                
                // چاپ کانفیگ برای تست (بعداً این رو میدیم به هسته Xray)
                Toast.makeText(MainActivity.this, "کانفیگ دریافت شد!", Toast.LENGTH_SHORT).show();
                System.out.println("DECRYPTED CONFIG: " + decryptedConfig);
            }

            @Override
            public void onError(String error) {
                // اگر مشکلی پیش آمد، دکمه به حالت اول برگردد
                btnConnect.setEnabled(true);
                btnConnect.setText("خطا! تلاش مجدد");
                btnConnect.setBackgroundColor(0xFFF44336); // قرمز رنگ
                isConnected = false;
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void stopFandoghShekan() {
        btnConnect.setText("اتصال هوشمند");
        btnConnect.setBackgroundColor(0xFFFF9800); // نارنجی رنگ
        isConnected = false;
    }
}
