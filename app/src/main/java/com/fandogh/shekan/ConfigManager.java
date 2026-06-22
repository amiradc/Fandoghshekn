package com.fandogh.shekan;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ConfigManager {

    private static final String GIST_RAW_URL = "https://gist.githubusercontent.com/arcaneechoes08-hub/360f898ab276cb083f0901cbabd4aa6a/raw/configs.txt";
    private static final String SECRET_KEY = "FandoghSecretKey"; 

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
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String rawData = response.toString().trim();
                    if (rawData.isEmpty()) {
                        mainHandler.post(() -> callback.onError("خطا: دیتای دریافت شده از سرور خالی است."));
                        return;
                    }

                    // رمزگشایی ایمن
                    String decryptedConfig = decryptAES(rawData, SECRET_KEY);
                    mainHandler.post(() -> callback.onSuccess(decryptedConfig));
                } else {
                    mainHandler.post(() -> callback.onError("خطا در ارتباط با گیت‌هاب. کد: " + responseCode));
                }
            } catch (javax.crypto.BadPaddingException | javax.crypto.IllegalBlockSizeException ae) {
                mainHandler.post(() -> callback.onError("خطا در رمزگشایی: کلید نامعتبر یا دیتای جیست اشتباه است."));
            } catch (IllegalArgumentException be) {
                mainHandler.post(() -> callback.onError("خطا: ساختار دیتای جیست Base64 معتبر نیست."));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("خطا در شبکه یا اینترنت گوشی: " + e.getLocalizedMessage()));
            }
        });
    }

    private String decryptAES(String encryptedText, String key) throws Exception {
        // حذف تمام اینترها، اسپیس‌ها و کاراکترهای مخفی ورودی برای سازگاری کامل با جاوا ۸
        String cleanText = encryptedText.replaceAll("[\\n\\r\\s]+", "");
        
        byte[] encryptedBytes = Base64.decode(cleanText, Base64.DEFAULT);
        
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, "UTF-8");
    }
}
