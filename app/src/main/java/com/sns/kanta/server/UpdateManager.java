package com.sns.kanta.server;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sns.kanta.R;
import com.sns.kanta.databinding.LayoutDialogUpdateBinding;
import com.sns.kanta.model.UpdateModel;

import java.io.File;
import java.lang.ref.WeakReference;

public class UpdateManager {

    private final WeakReference<AppCompatActivity> activityRef;
    private AlertDialog currentDialog = null;
    private long downloadId = -1;
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadId == id) {
                installApk(context);
            }
        }
    };

    public UpdateManager(AppCompatActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    public void dismissDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
        unregisterReceiver();
    }

    private void unregisterReceiver() {
        AppCompatActivity activity = activityRef.get();
        if (activity != null && downloadId != -1) {
            try {
                activity.unregisterReceiver(onDownloadComplete);
            } catch (Exception ignored) {
            }
        }
    }

    public void showUpdateDialog(UpdateModel update, boolean isForceUpdate) {
        AppCompatActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        dismissDialog();

        LayoutDialogUpdateBinding binding = LayoutDialogUpdateBinding.inflate(LayoutInflater.from(activity));

        binding.tvVersion.setText(activity.getString(R.string.update_version_label, update.getVersionName()));
        binding.tvReleaseNotes.setText(update.getReleaseNotes());

        if (isForceUpdate) {
            binding.tvUpdateTitle.setText(R.string.update_required);
            binding.tvForceUpdateHint.setVisibility(View.VISIBLE);
            binding.btnLater.setVisibility(View.GONE);
        } else {
            binding.tvUpdateTitle.setText(R.string.update_available);
            binding.tvForceUpdateHint.setVisibility(View.GONE);
            binding.btnLater.setVisibility(View.VISIBLE);
        }

        binding.btnUpdateNow.setOnClickListener(v -> {
            startDownload(update.getDownloadUrl(), update.getVersionName());
            binding.btnUpdateNow.setEnabled(false);
            binding.btnUpdateNow.setText(R.string.update_downloading);
        });

        binding.btnLater.setOnClickListener(v -> currentDialog.dismiss());

        currentDialog = new MaterialAlertDialogBuilder(activity, R.style.KantaAlertDialog)
                .setView(binding.getRoot())
                .setCancelable(!isForceUpdate)
                .create();

        currentDialog.setCanceledOnTouchOutside(!isForceUpdate);
        currentDialog.show();
    }

    private void startDownload(String url, String versionName) {
        AppCompatActivity activity = activityRef.get();
        if (activity == null) return;

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(activity.getString(R.string.update_notification_title));
        request.setDescription(activity.getString(R.string.update_notification_desc, versionName));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        String fileName = "kanta_update_" + versionName + ".apk";
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            downloadId = manager.enqueue(request);
            ContextCompat.registerReceiver(activity, onDownloadComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_EXPORTED);
            Toast.makeText(activity, R.string.update_download_started, Toast.LENGTH_SHORT).show();
        }
    }

    private void installApk(Context context) {
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor cursor = manager.query(query);

        if (cursor.moveToFirst()) {
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusIndex != -1 && DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                if (uriIndex != -1) {
                    String uriString = cursor.getString(uriIndex);
                    if (uriString != null) {
                        File apkFile = new File(Uri.parse(uriString).getPath());

                        // Fallback for some devices where Path might be hidden
                        if (!apkFile.exists()) {
                            String lastSegment = Uri.parse(uriString).getLastPathSegment();
                            if (lastSegment != null) {
                                apkFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        lastSegment);
                            }
                        }

                        if (apkFile.exists()) {
                            Intent installIntent = new Intent(Intent.ACTION_VIEW);
                            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);

                            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            try {
                                context.startActivity(installIntent);
                            } catch (Exception e) {
                                Toast.makeText(context, "Error opening APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
            }
        }
        cursor.close();
    }
}
