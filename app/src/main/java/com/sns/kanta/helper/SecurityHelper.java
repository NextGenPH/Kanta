package com.sns.kanta.helper;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class SecurityHelper {

    private static final String TAG = "SecurityHelper";

    // Common emulator signatures
    private static final String[] EMULATORS = {
            "generic", "goldfish", "ranchu", "vbox", "qemu",
            "android-x86", "nox", "mumu", "ldplayer",
            "blueStacks", "genymotion"
    };

    // Check if this is a debug build
    public static boolean isDebugBuild(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    // Check if running on emulator
    public static boolean isEmulator(Context context) {
        // Skip check in debug build
        if (isDebugBuild(context)) {
            return false;
        }

        // Check build properties
        if (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                "google_sdk".equals(Build.PRODUCT)) {
            return true;
        }

        // Check for emulator files
        String[] files = {
                "/system/bin/qemu-props",
                "/dev/socket/qemud",
                "/system/bin/microvirt"
        };

        for (String file : files) {
            if (new File(file).exists()) {
                return true;
            }
        }

        // Check kernel qemu property
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class);
            String value = (String) get.invoke(null, "ro.kernel.qemu");
            if (value != null && value.equals("1")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    // Check if app is debuggable
    public static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    // Check if device is rooted
    public static boolean isRooted(Context context) {
        // Skip check in debug build
        if (isDebugBuild(context)) {
            return false;
        }

        String[] rootPaths = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
        };

        for (String path : rootPaths) {
            if (new File(path).exists()) {
                return true;
            }
        }

        // Check for root command
        try {
            Process process = Runtime.getRuntime().exec("which su");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    // Check for hooking frameworks
    public static boolean isHooked(Context context) {
        // Skip check in debug build
        if (isDebugBuild(context)) {
            return false;
        }

        try {
            // Check for Xposed
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (ClassNotFoundException e) {
            // Not found
        }

        try {
            // Check for Frida
            BufferedReader reader = new BufferedReader(
                    new FileReader("/proc/self/maps")
            );
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("frida")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (IOException e) {
            // Ignore
        }

        return false;
    }

    // Comprehensive security check
    public static SecurityResult performSecurityCheck(Context context) {
        SecurityResult result = new SecurityResult();

        // Skip all security checks in debug build
        if (isDebugBuild(context)) {
            result.isDebugBuild = true;
            result.securityScore = 100;
            result.isCompromised = false;
            return result;
        }

        result.isDebugBuild = false;
        result.isEmulator = isEmulator(context);
        result.isDebuggable = isDebuggable(context);
        result.isRooted = isRooted(context);
        result.isHooked = isHooked(context);

        // Calculate security score
        int score = 100;
        if (result.isEmulator) score -= 35;
        if (result.isDebuggable) score -= 25;
        if (result.isRooted) score -= 20;
        if (result.isHooked) score -= 20;

        result.securityScore = Math.max(0, score);
        result.isCompromised = result.isEmulator || result.isDebuggable ||
                result.isHooked || result.isRooted;

        return result;
    }

    public static class SecurityResult {
        public boolean isDebugBuild;
        public boolean isEmulator;
        public boolean isDebuggable;
        public boolean isRooted;
        public boolean isHooked;
        public boolean isCompromised;
        public int securityScore;

        public String getReport() {
            if (isDebugBuild) {
                return "=== Debug Build - Security Checks Skipped ===";
            }
            return "=== Security Report ===\n" +
                    "Emulator: " + isEmulator + "\n" +
                    "Debuggable: " + isDebuggable + "\n" +
                    "Rooted: " + isRooted + "\n" +
                    "Hooked: " + isHooked + "\n" +
                    "Security Score: " + securityScore + "/100\n" +
                    "Compromised: " + isCompromised;
        }
    }
}