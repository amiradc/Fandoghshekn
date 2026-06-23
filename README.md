# Fandogh-Shekan VPN

یک اپلیکیشن VPN اندروید برای آزادی اینترنت

## ویژگی‌ها

- رابط کاربری ساده و کاربرپسند
- پشتیبانی برای کانفیگ‌های VLESS
- استفاده از JNI برای عملکرد بهتر
- ذخیره‌سازی ایمن تنظیمات

## سیستم مورد نیاز

- Android API 21+
- Android Studio 2022.1+
- NDK for native development
- Java 11+

## نصب و ساخت

### 1. Clone کردن پروژه
```bash
git clone https://github.com/amiradc/Fandoghshekn.git
cd Fandoghshekn
```

### 2. باز کردن در Android Studio
```bash
android-studio .
```

### 3. ساخت و اجرا
```bash
./gradlew build
./gradlew installDebug
```

## ساختار پروژه

```
Fandoghshekn/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/fandogh/shekan/
│   │       │   ├── MainActivity.java              # فعالیت اصلی
│   │       │   ├── FandoghVpnService.java         # سرویس VPN
│   │       │   ├── Encryptor.java                 # کلاس رمزنگاری
│   │       │   ├── ConfigManager.java             # مدیریت تنظیمات
│   │       │   ├── ConfigListAdapter.java         # Adapter لیست
│   │       │   ├── ConfigModel.java               # مدل داده‌ها
│   │       │   └── PingManager.java                # تست اتصال
│   │       ├── cpp/
│   │       │   ├── native-lib.c                   # کد Native
│   │       │   └── CMakeLists.txt                 # پیکربندی CMake
│   │       ├── res/
│   │       │   ├── layout/                        # فایل‌های XML رابط
│   │       │   └── values/                        # منابع رشته‌ای و رنگ‌ها
│   │       └── AndroidManifest.xml                # پیکربندی اپ
│   └── build.gradle                               # پیکربندی Gradle
├── build.gradle                                   # پیکربندی پروژه
├── settings.gradle                                # تنظیمات Gradle
├── gradle.properties                              # خصوصیات Gradle
└── README.md                                      # این فایل
```

## نحوه استفاده

1. اپلیکیشن را باز کنید
2. کانفیگ VLESS را در قسمت مناسب کپی کنید
3. بر روی دکمه "اتصال" کلیک کنید
4. اجازه دهید تا VPN متصل شود
5. برای قطع، بر روی دکمه "قطع" کلیک کنید

## توجهات امنیتی ⚠️

**مهم - لطفاً بخش امنیتی را بخوانید:**

- ❌ **هرگز کانفیگ‌های حساس را در کد سخت‌کد نکنید**
- ✅ برای ذخیره‌سازی حساس، از `EncryptedSharedPreferences` استفاده کنید
- ❌ VPN بر روی دستگاه‌های Rooted استفاده نکنید
- ✅ همیشه از HTTPS برای ارسال کانفیگ استفاده کنید
- ✅ کلیدهای رمزنگاری را در متغیرهای محیطی ذخیره کنید
- ❌ هرگز کلیدها را در version control (git) commit نکنید

## مسائل و پیشنهادات

اگر مسئله‌ای پیدا کردید یا پیشنهادی دارید، لطفاً [یک Issue باز کنید](https://github.com/amiradc/Fandoghshekn/issues)

## مجوز

این پروژه تحت مجوز MIT منتشر شده است.

---

**فورک اصلی**: [arcaneechoes08-hub/Fandoghshekn](https://github.com/arcaneechoes08-hub/Fandoghshekn)
**آخرین بروزرسانی**: 2026
