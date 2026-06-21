package com.fandogh.shekan;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigManager {

    // لینکی که مستقیماً به متن خام (Raw) در Gist اشاره می‌کند
    private static final String GIST_RAW_URL = "https://gist.github.com/arcaneechoes08-hub/97ed58c7ee1f162a7a0ae8608f08b25c.js";
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ConfigCallback {
        void onSuccess(String decryptedConfig);
        void onError(String error);
    }

    public void fetchAndDecryptConfig(ConfigCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(GIST_RAW_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // رمزگشایی متن (در اینجا: دی‌کد کردن Base64)
                    String encryptedText = response.toString().trim();
                    byte[] decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT);
                    String decryptedConfig = new String(decodedBytes, "UTF-8");

                    // ارسال نتیجه موفقیت‌آمیز به Thread اصلی
                    mainHandler.post(() -> callback.onSuccess(decryptedConfig));
                } else {
                    mainHandler.post(() -> callback.onError("خطا در ارتباط با سرور: " + responseCode));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("خطا: " + e.getMessage()));
            }
        });
    }
}
