package com.sns.kanta.helper;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;

import com.sns.kanta.R;
import com.sns.kanta.data.repository.PlayLaterManager;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.server.ReportRepository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MenuUtils {

    private MenuUtils() {}

    /**
     * Shows a Material Design 3 style popup menu for a media item.
     *
     * @param context    The active interface context.
     * @param anchorView The view anchor (usually btnMenu three-dot icon).
     * @param video      The VideoModel associated with the selected list item.
     */
    public static void showMediaItemMenu(Context context, View anchorView, VideoModel video) {
        PopupMenu popup = new PopupMenu(context, anchorView);
        popup.getMenuInflater().inflate(R.menu.menu_media_item, popup.getMenu());

        // Update menu item title dynamically based on cache
        PlayLaterManager playLaterManager = PlayLaterManager.getInstance(context);
        boolean isSaved = playLaterManager.isSaved(video.getVideoId());
        android.view.MenuItem playLaterItem = popup.getMenu().findItem(R.id.menu_play_later);
        if (playLaterItem != null) {
            if (isSaved) {
                playLaterItem.setTitle("Remove from Play Later");
            } else {
                playLaterItem.setTitle("Save to Play Later");
            }
        }

        // Force show icons in PopupMenu (Material 3 standard)
        try {
            Field[] fields = popup.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceShowIcon.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            android.util.Log.w("MenuUtils", "Could not force show icons on PopupMenu", e);
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_play_later) {
                playLaterManager.togglePlayLater(video, added -> {
                    anchorView.post(() -> {
                        String msg = added ? "Added to Play Later" : "Removed from Play Later";
                        com.google.android.material.snackbar.Snackbar.make(anchorView, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                    });
                });
                return true;
            } else if (id == R.id.menu_share) {
                shareVideo(context, video);
                return true;
            } else if (id == R.id.menu_report) {
                showReportBottomSheet(context, video, anchorView);
                return true;
            }
            return false;
        });

        popup.show();
    }

    /**
     * Shares the video details and a YouTube link using Android's default share sheet.
     *
     * @param context The active context.
     * @param video   The video to share.
     */
    public static void shareVideo(Context context, VideoModel video) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String shareMessage = "Check out this song on Kanta: " + video.getTitle() + "\nhttps://youtu.be/" + video.getVideoId();
        intent.putExtra(Intent.EXTRA_SUBJECT, video.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, shareMessage);
        context.startActivity(Intent.createChooser(intent, "Share song"));
    }

    /**
     * Shows a professional MD3 Bottom Sheet Dialog to report a media item.
     *
     * @param context    The active context.
     * @param video      The VideoModel to report.
     * @param anchorView The parent anchor view to attach the Snackbar to.
     */
    public static void showReportBottomSheet(Context context, VideoModel video, View anchorView) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(context);
        View view = android.view.LayoutInflater.from(context).inflate(R.layout.layout_report_bottom_sheet, null);
        dialog.setContentView(view);

        View rowBroken = view.findViewById(R.id.rowReportBroken);
        View rowQuality = view.findViewById(R.id.rowReportQuality);
        View rowWrong = view.findViewById(R.id.rowReportWrong);
        View rowOther = view.findViewById(R.id.rowReportOther);

        android.widget.RadioButton rbBroken = view.findViewById(R.id.rbReportBroken);
        android.widget.RadioButton rbQuality = view.findViewById(R.id.rbReportQuality);
        android.widget.RadioButton rbWrong = view.findViewById(R.id.rbReportWrong);
        android.widget.RadioButton rbOther = view.findViewById(R.id.rbReportOther);

        android.widget.Button btnCancel = view.findViewById(R.id.btnReportCancel);
        android.widget.Button btnNext = view.findViewById(R.id.btnReportNext);
        View pbLoading = view.findViewById(R.id.pbReportLoading);

        final String[] selectedReason = {null};

        Runnable updateSelection = () -> {
            rbBroken.setChecked("Broken or Not Playing".equals(selectedReason[0]));
            rbQuality.setChecked("Poor Audio/Video Quality".equals(selectedReason[0]));
            rbWrong.setChecked("Wrong Song or Version".equals(selectedReason[0]));
            rbOther.setChecked("Other".equals(selectedReason[0]));
            btnNext.setEnabled(selectedReason[0] != null);
        };

        rowBroken.setOnClickListener(v -> {
            selectedReason[0] = "Broken or Not Playing";
            updateSelection.run();
        });

        rowQuality.setOnClickListener(v -> {
            selectedReason[0] = "Poor Audio/Video Quality";
            updateSelection.run();
        });

        rowWrong.setOnClickListener(v -> {
            selectedReason[0] = "Wrong Song or Version";
            updateSelection.run();
        });

        rowOther.setOnClickListener(v -> {
            selectedReason[0] = "Other";
            updateSelection.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnNext.setOnClickListener(v -> {
            if (selectedReason[0] == null) return;

            // Show loader and disable all controls
            pbLoading.setVisibility(View.VISIBLE);
            btnNext.setEnabled(false);
            btnCancel.setEnabled(false);
            rowBroken.setEnabled(false);
            rowQuality.setEnabled(false);
            rowWrong.setEnabled(false);
            rowOther.setEnabled(false);

            ReportRepository.getInstance().submitReport(
                    video.getVideoId(),
                    selectedReason[0],
                    new ReportRepository.SubmitCallback() {
                        @Override
                        public void onSuccess() {
                            dialog.dismiss();
                            com.google.android.material.snackbar.Snackbar.make(
                                    anchorView,
                                    "Thank you. Your report has been submitted.",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show();
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            pbLoading.setVisibility(View.GONE);
                            btnNext.setEnabled(true);
                            btnCancel.setEnabled(true);
                            rowBroken.setEnabled(true);
                            rowQuality.setEnabled(true);
                            rowWrong.setEnabled(true);
                            rowOther.setEnabled(true);

                            com.google.android.material.snackbar.Snackbar.make(
                                    view,
                                    "Submission failed. Please check your network connection.",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show();
                        }
                    }
            );
        });

        dialog.show();
    }
}
