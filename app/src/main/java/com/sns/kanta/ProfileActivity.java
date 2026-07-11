package com.sns.kanta;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sns.kanta.adapter.SongAdapter;
import com.sns.kanta.data.repository.PlayLaterManager;
import com.sns.kanta.data.repository.RecentSongsManager;
import com.sns.kanta.databinding.ActivityProfileBinding;
import com.sns.kanta.helper.AnalyticsManager;
import com.sns.kanta.helper.SearchHistoryManager;
import com.sns.kanta.model.VideoModel;

public class ProfileActivity extends AppCompatActivity {

    private static final String PREF_NAME = "player_prefs";
    private static final String KEY_THEME = "app_theme";
    private static final String KEY_USER_NAME = "user_name";

    private ActivityProfileBinding binding;
    private SharedPreferences prefs;
    private SongAdapter playLaterAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        setupWindowInsets();
        setupUserInfo();
        setupPlayLaterSection();
        setupThemeSelection();
        setupDataManagement();
        setupButtons();
        setupDisplayMetadata();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int topSafe = Math.max(systemBars.top, displayCutout.top);
            binding.appBarLayout.setPadding(0, topSafe, 0, 0);

            int baseBottomPadding = (int) (32 * getResources().getDisplayMetrics().density);
            binding.nestedScrollView.setPadding(
                    systemBars.left,
                    0,
                    systemBars.right,
                    baseBottomPadding + systemBars.bottom
            );

            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playLaterAdapter != null) {
            loadPlayLaterSongs();
        }
    }

    private void setupUserInfo() {
        String name = prefs.getString(KEY_USER_NAME, getString(R.string.profile_default_name));
        binding.tvProfileName.setText(name);
        binding.layoutEditName.setOnClickListener(v -> showEditNameDialog());
    }

    private void showEditNameDialog() {
        EditText input = new EditText(this);
        String currentName = prefs.getString(KEY_USER_NAME, getString(R.string.profile_default_name));
        input.setText(currentName);
        input.setSelection(currentName.length());
        input.setHint(R.string.profile_edit_name_hint);

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.KantaAlertDialog)
                .setTitle(R.string.profile_edit_name)
                .setView(container)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        prefs.edit().putString(KEY_USER_NAME, newName).apply();
                        binding.tvProfileName.setText(newName);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    private void setupThemeSelection() {
        int currentTheme = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) {
            binding.themeToggleGroup.check(R.id.btnThemeLight);
        } else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            binding.themeToggleGroup.check(R.id.btnThemeNight);
        } else {
            binding.themeToggleGroup.check(R.id.btnThemeSystem);
        }

        binding.themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            int newTheme;
            if (checkedId == R.id.btnThemeLight) {
                newTheme = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.btnThemeNight) {
                newTheme = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                newTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }

            if (newTheme != currentTheme) {
                prefs.edit().putInt(KEY_THEME, newTheme).apply();
                AppCompatDelegate.setDefaultNightMode(newTheme);
            }
        });
    }

    private void setupDataManagement() {
        binding.btnClearHistory.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this, R.style.KantaAlertDialog)
                        .setTitle(R.string.profile_clear_confirm)
                        .setMessage(R.string.profile_clear_history_msg)
                        .setPositiveButton(R.string.btn_clear, (d, w) -> {
                            SearchHistoryManager.getInstance(getApplicationContext()).clearHistory();
                            RecentSongsManager.getInstance(getApplicationContext()).clearHistory();
                            getSharedPreferences("search_history_prefs", MODE_PRIVATE).edit().clear().apply();
                            Toast.makeText(this, R.string.profile_history_cleared, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
        );

        binding.btnClearCache.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this, R.style.KantaAlertDialog)
                        .setTitle(R.string.profile_clear_confirm)
                        .setMessage(R.string.profile_clear_cache_msg)
                        .setPositiveButton(R.string.btn_clear, (d, w) -> {
                            new Thread(() -> {
                                try {
                                    Glide.get(getApplicationContext()).clearDiskCache();
                                    runOnUiThread(() -> {
                                        Glide.get(getApplicationContext()).clearMemory();
                                        Toast.makeText(this, R.string.profile_cache_cleared, Toast.LENGTH_SHORT).show();
                                    });
                                } catch (Exception e) {
                                    Log.e("Profile", "Cache clearing failed", e);
                                }
                            }).start();
                        })
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
        );
    }

    private void setupButtons() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnShareApp.setOnClickListener(v -> shareApp());
        binding.btnShowHelp.setOnClickListener(v -> openWebPage(getString(R.string.menu_quick_guide), "https://www.nextgenph.site/Landingpage/quick-guide.html"));
        binding.btnShowPolicy.setOnClickListener(v -> openWebPage(getString(R.string.profile_policy), "https://www.nextgenph.site/Landingpage/privacy-policy.html"));
        binding.btnShowDisclaimer.setOnClickListener(v -> openWebPage(getString(R.string.profile_disclaimer), "https://www.nextgenph.site/Landingpage/disclaimer.html"));
        binding.cardDonate.setOnClickListener(v -> openWebPage(getString(R.string.menu_donate), "https://www.nextgenph.site/Landingpage/donate.html"));
    }

    private void setupDisplayMetadata() {
        binding.tvVersionInfo.setText(getString(R.string.profile_version_info, getString(R.string.app_name), BuildConfig.VERSION_NAME));
        String installationId = AnalyticsManager.getInstance(this).getInstallationId();
        binding.tvInstallationId.setText(getString(R.string.profile_user_id, installationId));
    }

    private void openWebPage(String title, String url) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WebViewActivity.EXTRA_TITLE, title);
        intent.putExtra(WebViewActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    private void shareApp() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String body = getString(R.string.profile_share_message, getPackageName());
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        intent.putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(intent, getString(R.string.profile_share_title)));
    }

    private void setupPlayLaterSection() {
        binding.rvPlayLater.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        playLaterAdapter = new SongAdapter(this, SongAdapter.Style.PLAY_LATER_CARD, this::playVideo, this::showPlayLaterMenu);
        binding.rvPlayLater.setAdapter(playLaterAdapter);
        loadPlayLaterSongs();
    }

    private void loadPlayLaterSongs() {
        PlayLaterManager.getInstance(this).getPlayLaterSongs(songs ->
                runOnUiThread(() -> {
                    if (songs.isEmpty()) {
                        binding.layoutPlayLaterEmpty.setVisibility(android.view.View.VISIBLE);
                        binding.rvPlayLater.setVisibility(android.view.View.GONE);
                        binding.tvPlayLaterCount.setText("0 songs");
                    } else {
                        binding.layoutPlayLaterEmpty.setVisibility(android.view.View.GONE);
                        binding.rvPlayLater.setVisibility(android.view.View.VISIBLE);
                        String countText = songs.size() == 1 ? "1 song" : songs.size() + " songs";
                        binding.tvPlayLaterCount.setText(countText);
                    }
                    playLaterAdapter.setSongs(songs);
                })
        );
    }

    private void playVideo(VideoModel video) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(MainActivity.EXTRA_VIDEO_ID, video.getVideoId());
        intent.putExtra(MainActivity.EXTRA_TITLE, video.getTitle());
        intent.putExtra(MainActivity.EXTRA_CHANNEL, video.getChannel());
        intent.putExtra("extra_thumbnail", video.getThumbnail());
        intent.putExtra("extra_artist", video.getArtist());

        android.app.ActivityOptions options = android.app.ActivityOptions.makeCustomAnimation(this, R.anim.slide_up, R.anim.no_animation);
        startActivity(intent, options.toBundle());
    }

    private void showPlayLaterMenu(android.view.View anchorView, VideoModel video) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.menu_media_item, popup.getMenu());

        android.view.MenuItem playLaterItem = popup.getMenu().findItem(R.id.menu_play_later);
        if (playLaterItem != null) {
            playLaterItem.setTitle("Remove from Play Later");
        }

        try {
            java.lang.reflect.Field[] fields = popup.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    java.lang.reflect.Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceShowIcon.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            Log.w("Profile", "Could not force show icons on PopupMenu", e);
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_play_later) {
                PlayLaterManager.getInstance(this).togglePlayLater(video, added ->
                        runOnUiThread(() -> {
                            loadPlayLaterSongs();
                            String msg = added ? "Added to Play Later" : "Removed from Play Later";
                            com.google.android.material.snackbar.Snackbar.make(anchorView, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                        })
                );
                return true;
            } else if (id == R.id.menu_share) {
                com.sns.kanta.helper.MenuUtils.shareVideo(this, video);
                return true;
            } else if (id == R.id.menu_report) {
                com.sns.kanta.helper.MenuUtils.showReportBottomSheet(this, video, anchorView);
                return true;
            }
            return false;
        });

        popup.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}