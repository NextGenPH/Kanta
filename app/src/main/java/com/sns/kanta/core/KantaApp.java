package com.sns.kanta.core;

import android.app.Application;
import android.util.Log;

/**
 * Custom Application class for global initialization and crash handling.
 */
public class KantaApp extends Application {
    private static final String TAG = "KantaApp";

    @Override
    public void onCreate() {
        super.onCreate();

        if (com.sns.kanta.BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        setupGlobalCrashHandler();
    }

    private void setupGlobalCrashHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // Guard against third-party NextGenUpdater background thread Toast crash on network failure
            if (throwable instanceof NullPointerException && throwable.getMessage() != null 
                    && throwable.getMessage().contains("Looper.prepare()")) {
                for (StackTraceElement element : throwable.getStackTrace()) {
                    if (element.getClassName().contains("com.nextgen.updater")) {
                        Log.w(TAG, "Silenced NextGenUpdater background thread toast crash", throwable);
                        return; // Ignore crash!
                    }
                }
            }

            Log.e(TAG, "FATAL CRASH DETECTED", throwable);

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
