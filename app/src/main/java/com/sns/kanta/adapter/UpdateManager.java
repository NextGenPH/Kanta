package com.sns.kanta.adapter;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.server.ApiService;

import java.lang.ref.WeakReference;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UpdateManager {

    private final WeakReference<AppCompatActivity> activityRef;
    private final ApiService apiService;
    private Call<List<UpdateModel>> activeCall;
    private boolean isForceUpdateBlocking = false;
    private AlertDialog currentDialog = null;

    public UpdateManager(AppCompatActivity activity, ApiService apiService) {
        this.activityRef = new WeakReference<>(activity);
        this.apiService = apiService;
    }

    public void cancelUpdateCheck() {
        if (activeCall != null && !activeCall.isCanceled()) {
            activeCall.cancel();
            activeCall = null;
        }
        dismissDialog();
    }

    private void dismissDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }

    public int getCurrentVersionCode() {
        AppCompatActivity activity = activityRef.get();
        if (activity == null) return 1;
        try {
            PackageInfo packageInfo = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }

    public void checkForUpdates(boolean showNoUpdateMessage) {
        AppCompatActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        String authHeader = "Bearer " + BuildConfig.SUPABASE_ANON_KEY;

        activeCall = apiService.getLatestVersion(
                BuildConfig.SUPABASE_ANON_KEY,
                authHeader,
                "*",
                "version_code.desc",
                1
        );

        activeCall.enqueue(new Callback<List<UpdateModel>>() {
            @Override
            public void onResponse(Call<List<UpdateModel>> call, Response<List<UpdateModel>> response) {
                AppCompatActivity act = activityRef.get();
                if (act == null || act.isFinishing() || act.isDestroyed() || call.isCanceled()) {
                    return;
                }

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    UpdateModel latestUpdate = response.body().get(0);
                    int currentVersion = getCurrentVersionCode();
                    int latestVersion = latestUpdate.getVersionCode();
                    boolean isForceUpdate = latestUpdate.isForceUpdate();

                    if (latestVersion > currentVersion) {
                        showUpdateDialog(latestUpdate, isForceUpdate);
                    } else if (showNoUpdateMessage) {
                        Toast.makeText(act, "You're using the latest version", Toast.LENGTH_SHORT).show();
                    }
                } else if (showNoUpdateMessage) {
                    Toast.makeText(act, "Unable to check for updates", Toast.LENGTH_SHORT).show();
                }
                activeCall = null;
            }

            @Override
            public void onFailure(Call<List<UpdateModel>> call, Throwable t) {
                AppCompatActivity act = activityRef.get();
                if (act == null || act.isFinishing() || act.isDestroyed() || call.isCanceled()) {
                    return;
                }
                if (showNoUpdateMessage) {
                    Toast.makeText(act, "Update check failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
                activeCall = null;
            }
        });
    }

    private void showUpdateDialog(UpdateModel update, boolean isForceUpdate) {
        AppCompatActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // Dismiss any existing dialog
        dismissDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        if (isForceUpdate) {
            builder.setTitle("⚠️ Update Required");
            builder.setMessage("Version " + update.getVersionName() + " is available!\n\n" +
                    "What's new:\n" + update.getReleaseNotes() + "\n\n" +
                    "⚠️ This update is REQUIRED to continue using the app.\n\n" +
                    "Please update now to access the app.");
        } else {
            builder.setTitle("📱 Update Available");
            builder.setMessage("Version " + update.getVersionName() + " is available!\n\n" +
                    "What's new:\n" + update.getReleaseNotes() + "\n\n" +
                    "Would you like to update?");
        }

        builder.setPositiveButton("Update Now", (dialog, which) -> {
            openDownloadUrl(update.getDownloadUrl());
        });

        if (!isForceUpdate) {
            builder.setNegativeButton("Later", (dialog, which) -> {
                dialog.dismiss();
            });
        }

        // Force update - can't be dismissed
        builder.setCancelable(!isForceUpdate);

        currentDialog = builder.create();
        currentDialog.setCanceledOnTouchOutside(!isForceUpdate);

        // Prevent back button from closing force update dialog
        if (isForceUpdate) {
            currentDialog.setOnCancelListener(null);
        }

        currentDialog.show();
        isForceUpdateBlocking = isForceUpdate;
    }

    private void openDownloadUrl(String url) {
        AppCompatActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to open download link", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isForceUpdateBlocking() {
        return isForceUpdateBlocking;
    }

    public void recheckAfterReturn() {
        if (isForceUpdateBlocking) {
            // User came back from browser without updating, show dialog again
            checkForUpdates(false);
        }
    }
}