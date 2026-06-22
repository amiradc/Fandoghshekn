#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/fcntl.h>

JNIEXPORT jint JNICALL
Java_com_fandogh_shekan_FandoghVpnService_execWithFd(JNIEnv *env, jobject thiz, jobjectArray cmd_array, jint tun_fd) {
    int len = (*env)->GetArrayLength(env, cmd_array);
    char **args = malloc((len + 1) * sizeof(char *));
    for (int i = 0; i < len; i++) {
        jstring str = (*env)->GetObjectArrayElement(env, cmd_array, i);
        args[i] = (char *)(*env)->GetStringUTFChars(env, str, 0);
    }
    args[len] = NULL;

    // باز کردن قفل ارث‌بری برای این دسکریپتور خاص
    fcntl(tun_fd, F_SETFD, 0);

    pid_t pid = fork();
    if (pid == 0) {
        // اینجا داخل فرآیند فرزند است؛ اندروید دیگر نمی‌تواند جلوی ما را بگیرد!
        execv(args[0], args);
        exit(1);
    }

    free(args);
    return pid; // بازگرداندن PID فرآیند به جاوا برای مدیریت و Kill کردن در آینده
}
