package com.sns.kanta;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.sns.kanta.adapter.FullscreenRelatedAdapter;
import com.sns.kanta.adapter.RelatedSongsAdapter;
import com.sns.kanta.data.repository.RecentSongsManager;
import com.sns.kanta.databinding.ActivityPlayerBinding;
import com.sns.kanta.helper.TextFormatter;
import com.sns.kanta.model.ReservationModel;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.player.GlobalPlayerManager;
import com.sns.kanta.player.PlaybackManager;
import com.sns.kanta.queueing.QueueManager;
import com.sns.kanta.viewmodel.MainViewModel;

import java.util.List;
import java.util.Objects;

import jp.wasabeef.glide.transformations.BlurTransformation;

public class PlayerActivity extends AppCompatActivity implements RelatedSongsAdapter.OnAddClickListener {

    private static final long CONTROLS_HIDE_MS = 3500L;
    private final Handler controlsHandler = new Handler(Looper.getMainLooper());
    private ActivityPlayerBinding binding;
    private final Runnable hideControlsRunnable = this::hideControls;
    private PlaybackManager playbackManager;
    private MainViewModel viewModel;
    private RecentSongsManager recentSongsManager;
    private AudioManager audioManager;
    private GestureDetector gestureDetector;
    private RelatedSongsAdapter relatedAdapter;
    private FullscreenRelatedAdapter fsRelatedAdapter;
    private android.os.CountDownTimer autoplayTimer;
    private final PlaybackManager.PlaybackCallback playbackCallback = new PlaybackManager.PlaybackCallback() {
        @Override
        public void onPlayerReady() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loadInitialVideo();
            });
        }

        @Override
        public void onStateChanged(@NonNull PlayerConstants.PlayerState state) {
            GlobalPlayerManager.getInstance().updateState(state);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                updateOverlayPlayPauseIcon(state);
                switch (state) {
                    case BUFFERING:
                        showLoading();
                        break;
                    case PLAYING:
                        hideLoading();
                        cancelAutoplayCountdown();
                        break;
                    case PAUSED:
                        hideLoading();
                        break;
                    case ENDED:
                        startAutoplayCountdown();
                        break;
                    default:
                        break;
                }
            });
        }

        @Override
        public void onCurrentSecond(float second) {
            GlobalPlayerManager.getInstance().updateTime(second);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                checkEndingSoon(second);
                ((LinearProgressIndicator) binding.playerSeekBar).setProgressCompat((int) second, true);
            });
        }

        @Override
        public void onVideoDuration(float duration) {
            GlobalPlayerManager.getInstance().updateDuration(duration);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                ((LinearProgressIndicator) binding.playerSeekBar).setMax((int) Math.max(1f, duration));
            });
        }

        @Override
        public void onSongLoaded(@NonNull ReservationModel song) {
            GlobalPlayerManager.getInstance().updateSong(song);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                // Reset indicator for new song
                LinearProgressIndicator progress = (LinearProgressIndicator) binding.playerSeekBar;
                progress.setMax(100);
                progress.setProgress(0);

                updateUI(song);
                viewModel.loadRelatedSongs(song.getArtistSafe(), song.getVideoId());
                viewModel.recordPlay(song.getVideoId(), () -> {
                    viewModel.loadVideoDetails(song.getVideoId());
                });
                extractColorFromThumbnail(song.getThumbnail());
                loadAmbientGlow(song.getThumbnail());

                recentSongsManager.addSong(new VideoModel(song.getVideoId(), song.getTitle(), song.getChannel(), song.getThumbnail(), song.getArtist()));
            });
        }
    };
    private boolean isControlsLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        GlobalPlayerManager.getInstance().setMinimized(false);

        // Support notch/hole-punch area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        QueueManager queueManager = QueueManager.getInstance(this);
        recentSongsManager = RecentSongsManager.getInstance(this);
        playbackManager = new PlaybackManager(queueManager, playbackCallback);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        setupPlayer();
        setupUI();
        setupRelatedSongs();
        setupOverlay();
        setupBackPressedHandler();
        setupWindowInsets();
        observeViewModel();

        updatePlayerHeight(false);
        showControls();
    }

    private void setupWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets displayCutout = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout());

            // Determine safe areas
            int leftSafe = Math.max(systemBars.left, displayCutout.left);
            int rightSafe = Math.max(systemBars.right, displayCutout.right);
            int topSafe = Math.max(systemBars.top, displayCutout.top);
            int bottomSafe = systemBars.bottom;

            // Padding for the overlay controls
            if (binding.controlsOverlay != null) {
                binding.controlsOverlay.setPadding(leftSafe, 0, rightSafe, 0);
            }

            // Apply top padding to info bar for status bar / notch area
            if (binding.topInfoBar != null) {
                binding.topInfoBar.setPadding(
                    binding.topInfoBar.getPaddingLeft(), 
                    topSafe, 
                    binding.topInfoBar.getPaddingRight(), 
                    binding.topInfoBar.getPaddingBottom()
                );
            }

            // Apply side padding to root for landscape notches
            binding.getRoot().setPadding(leftSafe, 0, rightSafe, 0);

            // Apply bottom padding to nestedScrollView for navigation bar
            if (binding.nestedScrollView != null) {
                binding.nestedScrollView.setPadding(
                    binding.nestedScrollView.getPaddingLeft(),
                    binding.nestedScrollView.getPaddingTop(),
                    binding.nestedScrollView.getPaddingRight(),
                    bottomSafe
                );
            }

            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupPlayer() {
        getLifecycle().addObserver(binding.youtubePlayerView);
        IFramePlayerOptions options = new IFramePlayerOptions.Builder(this).controls(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).build();
        binding.youtubePlayerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer player) {
                playbackManager.attachPlayer(player);
            }
        }, options);
    }

    private void loadInitialVideo() {
        GlobalPlayerManager gpm = GlobalPlayerManager.getInstance();
        ReservationModel active = gpm.currentSong.getValue();

        Intent intent = getIntent();
        String videoId = intent.getStringExtra(MainActivity.EXTRA_VIDEO_ID);

        if (videoId != null) {
            // New video selection
            if (active != null && active.getVideoId().equals(videoId)) {
                // Already playing this song, just sync state
                Float time = gpm.currentTime.getValue();
                playbackManager.loadSong(active, time != null ? time : 0f);
            } else {
                // Loading a completely new song
                String title = intent.getStringExtra(MainActivity.EXTRA_TITLE);
                String channel = intent.getStringExtra(MainActivity.EXTRA_CHANNEL);
                String thumb = intent.getStringExtra("extra_thumbnail");
                String artist = intent.getStringExtra("extra_artist");
                playbackManager.loadSong(new ReservationModel(videoId, title, channel, thumb, artist));
            }
        } else if (active != null) {
            // Reopening from Mini Player
            Float time = gpm.currentTime.getValue();
            playbackManager.loadSong(active, time != null ? time : 0f);
        }
    }

    private void setupUI() {
        binding.btnMinimize.setOnClickListener(v -> finish());
        binding.btnFullscreen.setOnClickListener(v -> {
            Boolean isFs = viewModel.isFullscreen.getValue();
            viewModel.setFullscreen(isFs == null || !isFs);
        });
        binding.btnLockControls.setOnClickListener(v -> {
            isControlsLocked = !isControlsLocked;
            performHapticFeedback();
            updateLockState();
        });
    }

    private void setupRelatedSongs() {
        binding.rvRelatedSongs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRelatedSongs.setHasFixedSize(false);
        binding.rvRelatedSongs.setNestedScrollingEnabled(false);

        relatedAdapter = new RelatedSongsAdapter(this, this);
        binding.rvRelatedSongs.setAdapter(relatedAdapter);

        fsRelatedAdapter = new FullscreenRelatedAdapter(this, this::loadSongFromVideo);
        binding.rvFullscreenMoreSongs.setAdapter(fsRelatedAdapter);
    }

    private void setupOverlay() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                // More responsive than onSingleTapConfirmed
                if (isControlsLocked) {
                    performHapticFeedback();
                    binding.btnLockControls.setVisibility(View.VISIBLE);
                    binding.btnLockControls.setAlpha(1f);
                    controlsHandler.removeCallbacksAndMessages(null);
                    controlsHandler.postDelayed(() -> {
                        if (isControlsLocked)
                            binding.btnLockControls.animate().alpha(0f).setDuration(250).start();
                    }, 2000);
                } else {
                    if (binding.centerControls.getVisibility() == View.VISIBLE && binding.centerControls.getAlpha() > 0.5f) {
                        hideControls();
                    } else {
                        showControls();
                    }
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (isControlsLocked) return false;
                float x = e.getX();
                float cur = playbackManager.getCurrentSecond();
                if (x < binding.controlsOverlay.getWidth() / 2f) {
                    playbackManager.seekTo(Math.max(0, cur - 10));
                    showSeekFeedback(binding.layoutRewindFeedback);
                } else {
                    playbackManager.seekTo(cur + 10);
                    showSeekFeedback(binding.layoutForwardFeedback);
                }
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (isControlsLocked || e1 == null) return false;
                float deltaY = e1.getY() - e2.getY();
                if (Math.abs(distanceY) > Math.abs(distanceX)) {
                    if (e1.getX() < binding.controlsOverlay.getWidth() / 2f) {
                        adjustBrightness(deltaY / binding.controlsOverlay.getHeight());
                    } else {
                        adjustVolume(deltaY / binding.controlsOverlay.getHeight());
                    }
                    return true;
                }
                return false;
            }
        });

        binding.controlsOverlay.setOnTouchListener((v, ev) -> {
            boolean handled = gestureDetector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                hideGestureIndicator();
            }
            return handled;
        });

        binding.btnOverlayPlayPause.setOnClickListener(v -> {
            if (playbackManager.isReady()) {
                if (playbackManager.getPlayerState() == PlayerConstants.PlayerState.PLAYING) {
                    playbackManager.pause();
                } else {
                    playbackManager.play();
                }
            }
            // Reset hide timer
            controlsHandler.removeCallbacks(hideControlsRunnable);
            controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_MS);
        });

        binding.btnOverlayNext.setOnClickListener(v -> playNextRelated());
        binding.btnOverlayPrevious.setOnClickListener(v -> playbackManager.playPrevious());
    }

    private void observeViewModel() {
        viewModel.isFullscreen.observe(this, fs -> {
            if (fs) enterFullscreen();
            else exitFullscreen();
        });

        viewModel.relatedSongs.observe(this, videos -> {
            relatedAdapter.setSongs(videos);
            fsRelatedAdapter.setSongs(videos);
            binding.cardRelatedSongs.setVisibility(videos.isEmpty() ? View.GONE : View.VISIBLE);
        });

        viewModel.isLoadingRelated.observe(this, loading -> {
            if (loading) {
                binding.shimmerRelated.setVisibility(View.VISIBLE);
                binding.shimmerRelated.startShimmer();
                binding.rvRelatedSongs.setVisibility(View.GONE);
            } else {
                binding.shimmerRelated.stopShimmer();
                binding.shimmerRelated.setVisibility(View.GONE);
                binding.rvRelatedSongs.setVisibility(View.VISIBLE);
            }
        });

        viewModel.currentVideoDetails.observe(this, video -> {
            if (video != null) {
                binding.layoutMetaStats.setVisibility(View.VISIBLE);

                Long playCount = video.getPlayCount();
                if (playCount != null && playCount > 0) {
                    binding.txtSongPlayCount.setText(formatPlayCount(playCount) + " plays");
                } else {
                    binding.txtSongPlayCount.setText("0 plays");
                }

                String rawDate = video.getPublishedAt();
                if (rawDate == null || rawDate.isEmpty()) {
                    rawDate = video.getCreatedAt();
                }

                if (rawDate != null && !rawDate.isEmpty()) {
                    binding.txtSongPublishedDate.setText(com.sns.kanta.helper.TimeUtils.getRelativeTime(rawDate));
                    binding.txtSongPublishedDate.setVisibility(View.VISIBLE);
                } else {
                    binding.txtSongPublishedDate.setVisibility(View.GONE);
                }
            } else {
                binding.layoutMetaStats.setVisibility(View.GONE);
            }
        });
    }

    private void loadSongFromVideo(VideoModel video) {
        cancelAutoplayCountdown();
        playbackManager.loadSong(new ReservationModel(video.getVideoId(), video.getTitle(), video.getChannel(), video.getThumbnail(), video.getArtist()));
    }

    private void updateUI(ReservationModel song) {
        binding.layoutMetaStats.setVisibility(View.GONE);
        String title = TextFormatter.formatSongTitle(song.getTitle());
        binding.txtSongTitle.setText(title);
        binding.tvOverlayTitle.setText(title);

        String art = (song.getArtist() != null && !song.getArtist().isEmpty()) ? song.getArtist() : song.getChannel();
        binding.txtSongChannel.setText(art);
        binding.tvOverlayArtist.setText(art);

        if (song.getChannel() != null && !song.getChannel().isEmpty()) {
            binding.txtSongSource.setText(getString(R.string.label_source, song.getChannel()));
            binding.txtSongSource.setVisibility(View.VISIBLE);
        } else {
            binding.txtSongSource.setVisibility(View.GONE);
        }
    }

    private void showControls() {
        binding.controlsOverlay.setBackgroundColor(android.graphics.Color.parseColor("#80000000")); // Darken

        if (isControlsLocked) {
            binding.btnLockControls.setVisibility(View.VISIBLE);
            binding.btnLockControls.animate().alpha(1f).setDuration(250).start();
            return;
        }

        // Make interactive elements visible and clickable
        setControlsClickable(true);

        binding.topInfoBar.setVisibility(View.VISIBLE);
        binding.centerControls.setVisibility(View.VISIBLE);
        binding.playerSeekBar.setVisibility(View.VISIBLE);
        binding.btnFullscreen.setVisibility(View.VISIBLE);

        binding.topInfoBar.animate().alpha(1f).setDuration(250).start();
        binding.centerControls.animate().alpha(1f).setDuration(250).start();
        binding.playerSeekBar.animate().alpha(1f).setDuration(250).start();
        binding.btnFullscreen.animate().alpha(1f).setDuration(250).start();

        boolean isFs = Objects.requireNonNullElse(viewModel.isFullscreen.getValue(), false);
        if (isFs) {
            binding.rvFullscreenMoreSongs.setVisibility(View.VISIBLE);
            binding.rvFullscreenMoreSongs.animate().alpha(1f).setDuration(250).start();
            binding.btnLockControls.setVisibility(View.VISIBLE);
            binding.btnLockControls.animate().alpha(1f).setDuration(250).start();
        }

        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_MS);
    }

    private void hideControls() {
        binding.controlsOverlay.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // Immediately make them unclickable so they don't block taps during fade out
        setControlsClickable(false);

        binding.topInfoBar.animate().alpha(0f).setDuration(250).start();
        binding.centerControls.animate().alpha(0f).setDuration(250).start();
        binding.playerSeekBar.animate().alpha(0f).setDuration(250).start();

        binding.btnFullscreen.animate().alpha(0.5f).setDuration(250).start();

        if (binding.btnLockControls.getVisibility() == View.VISIBLE) {
            binding.btnLockControls.animate().alpha(0f).setDuration(250).start();
        }
        if (binding.rvFullscreenMoreSongs.getVisibility() == View.VISIBLE) {
            binding.rvFullscreenMoreSongs.animate().alpha(0f).setDuration(250).start();
        }

        controlsHandler.postDelayed(() -> {
            if (binding.topInfoBar.getAlpha() == 0) binding.topInfoBar.setVisibility(View.GONE);
            if (binding.centerControls.getAlpha() == 0)
                binding.centerControls.setVisibility(View.GONE);
            if (binding.playerSeekBar.getAlpha() == 0)
                binding.playerSeekBar.setVisibility(View.GONE);
        }, 250);
    }

    private void setControlsClickable(boolean clickable) {
        binding.btnOverlayPlayPause.setClickable(clickable);
        binding.btnOverlayNext.setClickable(clickable);
        binding.btnOverlayPrevious.setClickable(clickable);
        binding.btnMinimize.setClickable(clickable);
        // Fullscreen remains clickable (at 0.5 alpha when hidden)
        binding.btnFullscreen.setClickable(true);
    }

    private void enterFullscreen() {
        updatePlayerHeight(true);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideSystemUI();
        binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit);
    }

    private void exitFullscreen() {
        updatePlayerHeight(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        showSystemUI();
        binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen);
        controlsHandler.postDelayed(() -> {
            if (!isDestroyed() && !isFinishing()) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }, 1000);
    }

    private void updatePlayerHeight(boolean fullscreen) {
        ViewGroup.LayoutParams params = binding.playerWrapper.getLayoutParams();
        if (fullscreen) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            params.height = (Math.min(width, height) * 9) / 16;
        }
        binding.playerWrapper.setLayoutParams(params);
        binding.playerContainer.getLayoutParams().height = params.height;
        binding.youtubePlayerView.getLayoutParams().height = params.height;
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null)
                c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void startAutoplayCountdown() {
        cancelAutoplayCountdown();

        autoplayTimer = new android.os.CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millis) {
                if (isFinishing() || isDestroyed()) {
                    cancel();
                    return;
                }
                binding.tvAutoplaySeconds.setText(String.valueOf((int) (millis / 1000) + 1));
            }

            @Override
            public void onFinish() {
                if (isFinishing() || isDestroyed()) return;
                binding.layoutAutoplayCountdown.setVisibility(View.GONE);
                playNextRelated();
            }
        }.start();
        binding.layoutAutoplayCountdown.setVisibility(View.VISIBLE);
        binding.btnSkipCountdown.setOnClickListener(v -> {
            cancelAutoplayCountdown();
            playNextRelated();
        });
    }

    private void cancelAutoplayCountdown() {
        if (autoplayTimer != null) {
            autoplayTimer.cancel();
            autoplayTimer = null;
        }
        if (binding != null && binding.layoutAutoplayCountdown != null) {
            binding.layoutAutoplayCountdown.setVisibility(View.GONE);
        }
    }

    private void playNextRelated() {
        if (isFinishing() || isDestroyed()) return;

        List<VideoModel> related = viewModel.relatedSongs.getValue();
        if (related != null && !related.isEmpty()) {
            loadSongFromVideo(related.get(0));
        } else {
            Toast.makeText(this, "No more related songs to play.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void updateOverlayPlayPauseIcon(PlayerConstants.PlayerState state) {
        if (state == PlayerConstants.PlayerState.PLAYING) {
            binding.btnOverlayPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            binding.btnOverlayPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void checkEndingSoon(float second) {
        float duration = playbackManager.getVideoDuration();
        if (duration > 30f && (duration - second) <= 20f && !playbackManager.isEndingSoonAlertShown()) {
            playbackManager.setEndingSoonAlertShown(true);
            List<VideoModel> related = viewModel.relatedSongs.getValue();
            if (related != null && !related.isEmpty()) {
                Toast.makeText(this, "Up next: " + TextFormatter.formatSongTitle(related.get(0).getTitle()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showLoading() {
        binding.loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        binding.loadingIndicator.setVisibility(View.GONE);
    }

    private void adjustVolume(float percent) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        // Multiplier 1.2 for slightly faster adjustment on short swipes
        int delta = Math.round(percent * max * 1.2f);
        int next = Math.max(0, Math.min(max, current + delta));

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
        showGestureIndicator(R.drawable.ic_volume_up, (int) ((next / (float) max) * 100) + "%");
    }

    private void adjustBrightness(float percent) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float current = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
        lp.screenBrightness = Math.max(0.01f, Math.min(1f, current + (percent * 1.2f)));
        getWindow().setAttributes(lp);
        showGestureIndicator(R.drawable.ic_brightness, (int) (lp.screenBrightness * 100) + "%");
    }

    private void showGestureIndicator(int icon, String text) {
        binding.layoutGestureIndicator.setVisibility(View.VISIBLE);
        binding.layoutGestureIndicator.setAlpha(1f);
        binding.ivGestureIcon.setImageResource(icon);
        binding.tvGestureValue.setText(text);
    }

    private void hideGestureIndicator() {
        binding.layoutGestureIndicator.animate().alpha(0f).setDuration(300).withEndAction(() -> binding.layoutGestureIndicator.setVisibility(View.GONE)).start();
    }

    private void showSeekFeedback(View v) {
        v.setAlpha(1f);
        v.animate().alpha(0f).setDuration(600).setStartDelay(200).start();
    }

    private void updateLockState() {
        if (isControlsLocked) {
            hideControls();
            Toast.makeText(this, "Controls Locked", Toast.LENGTH_SHORT).show();
        } else {
            showControls();
            Toast.makeText(this, "Controls Unlocked", Toast.LENGTH_SHORT).show();
        }
    }

    private void performHapticFeedback() {
        getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void extractColorFromThumbnail(String url) {
        if (url == null || url.isEmpty()) return;
        Glide.with(getApplicationContext()).asBitmap().load(url).into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
            @Override
            public void onResourceReady(@NonNull android.graphics.Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                if (isDestroyed() || isFinishing()) return;
                Palette.from(resource).generate(palette -> {
                    if (isDestroyed() || isFinishing()) return;
                    if (palette != null) {
                        Palette.Swatch swatch = palette.getVibrantSwatch();
                        if (swatch == null) swatch = palette.getMutedSwatch();
                        if (swatch != null) applyDynamicColors(swatch.getRgb());
                    }
                });
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
            }
        });
    }

    private void applyDynamicColors(int color) {
        ((LinearProgressIndicator) binding.playerSeekBar).setIndicatorColor(color);
        binding.tvOverlayArtist.setTextColor(color);
    }

    private void loadAmbientGlow(String url) {
        if (url == null || url.isEmpty()) {
            binding.ivAmbientGlow.animate().alpha(0f).start();
            return;
        }
        Glide.with(getApplicationContext()).load(url).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3))).into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
            @Override
            public void onResourceReady(@NonNull android.graphics.drawable.Drawable resource, @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                if (isDestroyed() || isFinishing()) return;
                binding.ivAmbientGlow.setImageDrawable(resource);
                binding.ivAmbientGlow.animate().alpha(0.3f).setDuration(800).start();
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
            }
        });
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewModel.isFullscreen.getValue() != null && viewModel.isFullscreen.getValue()) {
                    viewModel.setFullscreen(false);
                } else {
                    // Minimize instead of full finish if a song is playing
                    if (GlobalPlayerManager.getInstance().isPlaying()) {
                        GlobalPlayerManager.getInstance().setMinimized(true);
                    }
                    finish();
                }
            }
        });
    }

    @Override
    public void onAddClick(@NonNull VideoModel video) {
        loadSongFromVideo(video);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        viewModel.setFullscreen(isLandscape);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && Objects.requireNonNullElse(viewModel.isFullscreen.getValue(), false)) {
            hideSystemUI();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        controlsHandler.removeCallbacksAndMessages(null);
        if (autoplayTimer != null) autoplayTimer.cancel();
    }

    private String formatPlayCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format(java.util.Locale.US, "%.1fK", count / 1000.0);
        return String.format(java.util.Locale.US, "%.1fM", count / 1000000.0);
    }
}