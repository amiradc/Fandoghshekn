package com.fandogh.shekan;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class FandoghVpnService extends VpnService implements Runnable {
    private static final String TAG = "FandoghVpnService";
    private Thread mThread;
    private ParcelFileDescriptor mInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
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
            Log.i(TAG, "تنظیمات تونل فندق‌شکن در حال پیکربندی...");
            
            // تشکیل تونل مجازی برای مصادره ترافیک
            Builder builder = new Builder();
            mInterface = builder.setSession("FandoghShekan")
                    .addAddress("10.0.0.2", 24) // آی‌پی داخلی تونل
                    .addDnsServer("8.8.8.8")    // دی‌ان‌اس امن
                    .addRoute("0.0.0.0", 0)     // هدایت ۱۰۰٪ ترافیک کل سیستم به برنامه
                    .establish();

            Log.i(TAG, "تونل مجازی با موفقیت باز شد.");

            // حلقه زنده نگه داشتن سرویس پس‌زمینه
            while (mThread != null && !mThread.isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در کارکرد سرویس: " + e.getMessage());
        } finally {
            stopVpn();
        }
    }

    private void stopVpn() {
        try {
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
            if (mInterface != null) {
                mInterface.close();
                mInterface = null;
            }
            Log.i(TAG, "تونل فندق‌شکن بسته شد.");
        } catch (Exception e) {
            Log.e(TAG, "خطا در بستن تونل: " + e.getMessage());
        }
    }
}
