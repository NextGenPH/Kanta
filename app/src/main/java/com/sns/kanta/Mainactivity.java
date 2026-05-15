package com.sns.kanta;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.pierfrancescosoffritti.androidyoutubeplayer.chromecast.chromecastsender.ChromecastYouTubePlayerContext;
import com.pierfrancescosoffritti.androidyoutubeplayer.chromecast.chromecastsender.io.infrastructure.ChromecastConnectionListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker;
import com.sns.kanta.adapter.FavoritesAdapter;
import com.sns.kanta.adapter.RelatedSongsAdapter;
import com.sns.kanta.adapter.SearchHistoryAdapter;
import com.sns.kanta.adapter.SearchResultAdapter;
import com.sns.kanta.databinding.ActivityMainBinding;
import com.sns.kanta.databinding.DialogSearchSongBinding;
import com.sns.kanta.helper.FavoritesManager;
import com.sns.kanta.helper.SearchHistoryManager;
import com.sns.kanta.helper.TextFormatter;
import com.sns.kanta.model.ReservationModel;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.queueing.QueueManager;
import com.sns.kanta.server.UpdateManager;
import com.sns.kanta.server.VideoRepository;
import com.sns.kanta.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity
        implements RelatedSongsAdapter.OnAddClickListener,
        RelatedSongsAdapter.OnFavoriteLongClickListener,
        SearchResultAdapter.OnFavoriteLongClickListener {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CHANNEL = "extra_channel";

    private static final String FALLBACK_VIDEO_ID = "fW10s9xwI5k";
    private static final String PREF_NAME = "player_prefs";
    private static final String PREF_LAST_VIDEO = "last_video_id";
    private static final String PREF_LAST_TITLE = "last_title";
    private static final String PREF_LAST_CHANNEL = "last_channel";
    private static final String PREF_SAVED_VIDEO = "saved_video_id";
    private static final String PREF_SAVED_TITLE = "saved_video_title";
    private static final String PREF_SAVED_CHANNEL = "saved_video_channel";
    private static final String PREF_SAVED_TIME = "saved_video_time";

    private static final long SEARCH_DELAY_MS = 400L;
    private static final long SEEK_DELAY_MS = 600L;
    private static final long RESUME_AUTO_HIDE_MS = 8000L;
    private static final long CONTROLS_HIDE_MS = 3500L;

    private static final int REQUEST_MIC_PERM = 101;
    private final TextFormatter formatter = TextFormatter.getInstance();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Handler resumeHandler = new Handler(Looper.getMainLooper());
    private final Handler seekHandler = new Handler(Looper.getMainLooper());
    private final Handler controlsHandler = new Handler(Looper.getMainLooper());

    private ActivityMainBinding binding;
    private final Runnable hideControlsRunnable = this::hideControls;
    private RelatedSongsAdapter relatedAdapter;
    private FavoritesAdapter favoritesAdapter;

    private ActivityResultLauncher<Intent> voiceSearchLauncher;
    private SearchResultAdapter searchAdapter;
    private SearchHistoryAdapter historyAdapter;
    private BottomSheetBehavior<View> searchSheetBehavior;
    private DialogSearchSongBinding searchBinding;
    private EditText searchInput;
    private Runnable searchRunnable;

    private MainViewModel viewModel;
    private QueueManager queueManager;
    private FavoritesManager favoritesManager;
    private SearchHistoryManager historyManager;
    private UpdateManager updateManager;
    private VideoRepository repository;
    private SharedPreferences prefs;

    private YouTubePlayer activePlayer;
    private YouTubePlayerTracker playerTracker;
    private ReservationModel pendingDirectPlay;
    private boolean isPlayingFallback = false;
    private float pendingSeekTime = 0f;
    private boolean isEndingSoonAlertShown = false;
    private GestureDetector gestureDetector;
    private ChromecastYouTubePlayerContext chromecastYouTubePlayerContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        queueManager = QueueManager.getInstance(this);
        favoritesManager = FavoritesManager.getInstance(this);
        historyManager = SearchHistoryManager.getInstance(this);
        updateManager = new UpdateManager(this);
        repository = VideoRepository.getInstance();

        handleIntent();
        restoreLastPlayedIfNeeded();
        setupPlayer();
        setupOverlay();
        setupFullscreenButton();
        setupRelatedSongs();
        setupFavoritesDrawer();
        setupSearchBottomSheet();
        setupBottomNav();
        checkForSavedSession();
        refreshDisplay();

        setupCast();
        setupActivityResultLaunchers();
        setupBackPressedHandler();
        observeViewModel();

        viewModel.checkForUpdates();
        showControls();
    }

    private void setupSearchBottomSheet() {
        searchSheetBehavior = BottomSheetBehavior.from(binding.searchBottomSheet);
        searchSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        searchBinding = binding.searchContent;
        searchInput = searchBinding.searchInput;

        for (int i = 0; i < searchBinding.chipGroupCategories.getChildCount(); i++) {
            View child = searchBinding.chipGroupCategories.getChildAt(i);
            if (child instanceof com.google.android.material.chip.Chip) {
                com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) child;
                chip.setOnClickListener(v -> {
                    String query = chip.getText().toString().trim();
                    searchBinding.searchInput.setText(query);
                    searchBinding.searchInput.setSelection(query.length());
                });
            }
        }

        searchBinding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SearchResultAdapter(this, this::addToQueueFromSearch, this);
        searchBinding.recyclerResults.setAdapter(searchAdapter);

        historyAdapter = new SearchHistoryAdapter(query -> {
            searchBinding.searchInput.setText(query);
            searchBinding.searchInput.setSelection(query.length());
        });
        searchBinding.recyclerHistory.setAdapter(historyAdapter);
        searchBinding.btnClearHistory.setOnClickListener(v -> {
            historyManager.clearHistory();
            searchBinding.historySection.setVisibility(View.GONE);
        });

        List<String> historyItems = historyManager.getHistory();
        if (!historyItems.isEmpty()) {
            searchBinding.historySection.setVisibility(View.VISIBLE);
            historyAdapter.setHistoryItems(historyItems);
        }

        searchBinding.btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC_PERM);
            } else startVoiceSearch();
        });

        searchBinding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int i, int c, int a) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                String query = s.toString();
                if (query.trim().isEmpty()) {
                    searchBinding.txtEmpty.setVisibility(View.VISIBLE);
                    searchBinding.txtEmpty.setText(R.string.search_prompt);
                    searchAdapter.updateResults(new ArrayList<>());
                    searchBinding.txtResultCount.setText(R.string.search_found_zero);
                    searchBinding.progressBar.setVisibility(View.GONE);
                    List<String> h = historyManager.getHistory();
                    if (!h.isEmpty()) {
                        searchBinding.historySection.setVisibility(View.VISIBLE);
                        historyAdapter.setHistoryItems(h);
                    }
                    return;
                }
                searchBinding.historySection.setVisibility(View.GONE);
                searchBinding.txtEmpty.setVisibility(View.GONE);
                searchBinding.progressBar.setVisibility(View.VISIBLE);
                searchBinding.txtResultCount.setText(R.string.searching);
                searchRunnable = () -> {
                    viewModel.runSearch(query);
                    historyManager.addSearchQuery(query);
                };
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }
        });

        searchSheetBehavior.setFitToContents(false);
        searchSheetBehavior.setExpandedOffset(0);
    }

    private void setupCast() {
        try {
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), binding.mediaRouteButton);
            chromecastYouTubePlayerContext = new ChromecastYouTubePlayerContext(
                    CastContext.getSharedInstance(this).getSessionManager(),
                    new ChromecastConnectionListener() {
                        @Override
                        public void onChromecastConnecting() {
                        }

                        @Override
                        public void onChromecastConnected(@NonNull ChromecastYouTubePlayerContext context) {
                            if (activePlayer != null) activePlayer.pause();
                        }

                        @Override
                        public void onChromecastDisconnected() {
                            if (activePlayer != null) activePlayer.play();
                        }
                    }
            );
        } catch (Exception e) {
            Log.e("Cast", "Error setting up Cast: " + e.getMessage());
        }
    }

    private void observeViewModel() {
        viewModel.isFullscreen.observe(this, fs -> {
            if (fs) enterFullscreen();
            else exitFullscreen();
            binding.btnFullscreen.bringToFront();
        });
        viewModel.searchResults.observe(this, results -> {
            if (searchAdapter != null) searchAdapter.updateResults(results);
            if (searchBinding != null) {
                searchBinding.progressBar.setVisibility(View.GONE);
                searchBinding.txtResultCount.setText(getString(R.string.search_found_count, results.size()));
            }
        });
        viewModel.relatedSongs.observe(this, videos -> {
            ReservationModel current = queueManager.getNowPlaying();
            String artist = (current != null && current.getArtist() != null) ? current.getArtist() : "";
            if (videos.isEmpty()) {
                binding.txtRelatedSectionTitle.setText(R.string.related_title);
                binding.txtNoRelated.setVisibility(View.VISIBLE);
                binding.txtNoRelated.setText(getString(R.string.related_none_found, artist));
                binding.rvRelatedSongs.setVisibility(View.GONE);
            } else {
                binding.txtRelatedSectionTitle.setText(getString(R.string.related_more_from, artist.toUpperCase(Locale.US)));
                binding.txtNoRelated.setVisibility(View.GONE);
                binding.rvRelatedSongs.setVisibility(View.VISIBLE);
                relatedAdapter.setSongs(videos);
                binding.cardRelatedSongs.setAlpha(0f);
                binding.cardRelatedSongs.setVisibility(View.VISIBLE);
                binding.cardRelatedSongs.animate().alpha(1f).setDuration(400).start();
            }
        });
        viewModel.searchError.observe(this, msg -> {
            Toast.makeText(this, getString(R.string.search_error_toast, msg), Toast.LENGTH_SHORT).show();
            if (searchBinding != null) {
                searchBinding.progressBar.setVisibility(View.GONE);
                searchBinding.txtResultCount.setText(R.string.search_failed);
            }
        });
        viewModel.updateInfo.observe(this, info -> {
            if (info != null) {
                Boolean force = viewModel.isForceUpdate.getValue();
                updateManager.showUpdateDialog(info, force != null && force);
            }
        });
    }

    private void setupActivityResultLaunchers() {
        voiceSearchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && !results.isEmpty()) {
                            String spoken = results.get(0);
                            if (searchSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                                searchInput.setText(spoken);
                            } else {
                                showSearchDialog(spoken);
                            }
                        }
                    }
                }
        );
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                    searchSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.END);
                } else {
                    Boolean isFs = viewModel.isFullscreen.getValue();
                    if (isFs != null && isFs) {
                        viewModel.setFullscreen(false);
                    } else {
                        showExitDialog();
                    }
                }
            }
        });
    }

    private void showExitDialog() {
        new MaterialAlertDialogBuilder(this, R.style.KantaAlertDialog)
                .setTitle(R.string.exit_title)
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton(R.string.btn_exit, (d, w) -> {
                    clearSavedSession();
                    finishAffinity();
                })
                .setNegativeButton(R.string.btn_stay, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activePlayer != null) activePlayer.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePlaybackState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        savePlaybackState();
        searchHandler.removeCallbacksAndMessages(null);
        resumeHandler.removeCallbacksAndMessages(null);
        seekHandler.removeCallbacksAndMessages(null);
        controlsHandler.removeCallbacksAndMessages(null);
        if (repository != null) {
            repository.cancelActive();
            repository.cancelArtistSearch();
        }
        if (updateManager != null) updateManager.dismissDialog();
        binding.youtubePlayerView.release();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void handleIntent() {
        String videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String channel = getIntent().getStringExtra(EXTRA_CHANNEL);
        if (videoId != null && !videoId.isEmpty()) {
            String t = Objects.requireNonNullElse(title, "Song");
            String c = Objects.requireNonNullElse(channel, "Artist");
            pendingDirectPlay = new ReservationModel(videoId, t, c, "Direct Play");
            persistLastPlayed(videoId, t, c);
        }
    }

    private void restoreLastPlayedIfNeeded() {
        if (pendingDirectPlay != null) return;
        String id = prefs.getString(PREF_LAST_VIDEO, null);
        if (id != null) {
            String t = prefs.getString(PREF_LAST_TITLE, "Song");
            String c = prefs.getString(PREF_LAST_CHANNEL, "Artist");
            pendingDirectPlay = new ReservationModel(id, t, c, "Last Played");
        }
    }

    private void setupBottomNav() {
        NavigationBarView navView = binding.bottomNavigationView;
        if (findViewById(R.id.navigationRail) != null) navView = findViewById(R.id.navigationRail);
        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_search) {
                showSearchDialog(null);
                return true;
            } else if (id == R.id.nav_favorites) {
                showFavorites();
                return true;
            } else if (id == R.id.nav_settings) {
                showHelp();
                return true;
            }
            return false;
        });
    }

    private void checkForSavedSession() {
        String sid = prefs.getString(PREF_SAVED_VIDEO, null);
        float stime = prefs.getFloat(PREF_SAVED_TIME, 0f);
        if (sid != null && stime > 5f && pendingDirectPlay == null) {
            String stitle = prefs.getString(PREF_SAVED_TITLE, sid);
            binding.txtResumeTitle.setText(formatter.formatSongTitle(stitle));
            binding.btnResume.setOnClickListener(v -> {
                dismissResumeBanner();
                applyResume(sid, stitle, prefs.getString(PREF_SAVED_CHANNEL, ""), stime);
            });
            binding.btnDismissResume.setOnClickListener(v -> {
                dismissResumeBanner();
                clearSavedSession();
            });
            binding.resumeBanner.setVisibility(View.VISIBLE);
            ObjectAnimator.ofFloat(binding.resumeBanner, "translationY", 80f, 0f).setDuration(280).start();
            resumeHandler.postDelayed(dismissResumeBannerRunnable, RESUME_AUTO_HIDE_MS);
        }
    }

    private void applyResume(String id, String title, String ch, float time) {
        pendingSeekTime = time;
        pendingDirectPlay = new ReservationModel(id, title, ch, "Resumed");
        persistLastPlayed(id, title, ch);
        if (activePlayer != null) {
            isPlayingFallback = false;
            activePlayer.loadVideo(id, 0f);
            seekHandler.postDelayed(() -> {
                if (activePlayer != null && pendingSeekTime > 0f) {
                    activePlayer.seekTo(pendingSeekTime);
                    activePlayer.play();
                    pendingSeekTime = 0f;
                }
            }, SEEK_DELAY_MS);
            pendingDirectPlay = null;
            refreshDisplay();
        }
    }

    private void dismissResumeBanner() {
        resumeHandler.removeCallbacks(dismissResumeBannerRunnable);
        if (binding.resumeBanner.getVisibility() != View.VISIBLE) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(binding.resumeBanner, "translationY", 0f, 80f);
        anim.setDuration(220);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                binding.resumeBanner.setVisibility(View.GONE);
            }
        });
        anim.start();
    }

    private void savePlaybackState() {
        ReservationModel cur = queueManager.getNowPlaying();
        if (isPlayingFallback || cur == null || playerTracker == null) return;
        float time = playerTracker.getCurrentSecond();
        if (time > 5f) {
            prefs.edit()
                    .putString(PREF_SAVED_VIDEO, cur.getVideoId())
                    .putString(PREF_SAVED_TITLE, cur.getTitle())
                    .putString(PREF_SAVED_CHANNEL, cur.getChannel())
                    .putFloat(PREF_SAVED_TIME, time)
                    .apply();
        }
    }

    private void clearSavedSession() {
        prefs.edit().remove(PREF_SAVED_VIDEO).remove(PREF_SAVED_TITLE).remove(PREF_SAVED_CHANNEL).remove(PREF_SAVED_TIME).apply();
    }

    private void persistLastPlayed(String id, String t, String c) {
        if (id != null) {
            prefs.edit().putString(PREF_LAST_VIDEO, id).putString(PREF_LAST_TITLE, t).putString(PREF_LAST_CHANNEL, c).apply();
        }
    }

    private void setupPlayer() {
        getLifecycle().addObserver(binding.youtubePlayerView);
        playerTracker = new YouTubePlayerTracker();
        IFramePlayerOptions options = new IFramePlayerOptions.Builder(this).controls(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).build();
        binding.youtubePlayerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer player) {
                activePlayer = player;
                player.addListener(playerTracker);
                playCurrentSong();
            }

            @Override
            public void onCurrentSecond(@NonNull YouTubePlayer player, float second) {
                super.onCurrentSecond(player, second);
                checkEndingSoon(second);
            }

            @Override
            public void onStateChange(@NonNull YouTubePlayer player, @NonNull PlayerConstants.PlayerState state) {
                updateOverlayPlayPauseIcon(state);
                switch (state) {
                    case BUFFERING:
                        showLoading();
                        break;
                    case PLAYING:
                        hideLoading();
                        startVisualizer();
                        break;
                    case PAUSED:
                        hideLoading();
                        stopVisualizer();
                        break;
                    case ENDED:
                        runOnUiThread(() -> {
                            stopVisualizer();
                            playNextRelated();
                        });
                        break;
                    default:
                        break;
                }
            }
        }, options);
    }

    private void checkEndingSoon(float second) {
        if (isPlayingFallback || isEndingSoonAlertShown) return;
        float duration = playerTracker.getVideoDuration();
        if (duration > 30f && (duration - second) <= 20f) {
            isEndingSoonAlertShown = true;
            List<VideoModel> related = viewModel.relatedSongs.getValue();
            if (related != null && !related.isEmpty()) {
                Toast.makeText(this, "Up next: " + formatter.formatSongTitle(related.get(0).getTitle()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startVisualizer() {
        binding.visualizer.getRoot().setVisibility(View.VISIBLE);
        animateBar(binding.visualizer.bar1, 400);
        animateBar(binding.visualizer.bar2, 300);
        animateBar(binding.visualizer.bar3, 500);
        animateBar(binding.visualizer.bar4, 350);
    }

    private void stopVisualizer() {
        binding.visualizer.getRoot().setVisibility(View.GONE);
        binding.visualizer.bar1.clearAnimation();
        binding.visualizer.bar2.clearAnimation();
        binding.visualizer.bar3.clearAnimation();
        binding.visualizer.bar4.clearAnimation();
    }

    private void animateBar(View bar, long d) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(bar, "scaleY", 0.3f, 1.2f);
        anim.setDuration(d);
        anim.setRepeatCount(ObjectAnimator.INFINITE);
        anim.setRepeatMode(ObjectAnimator.REVERSE);
        anim.start();
    }

    private void playCurrentSong() {
        if (activePlayer == null) return;
        if (pendingDirectPlay != null) {
            loadSong(pendingDirectPlay);
            pendingDirectPlay = null;
        } else playFallback();
    }

    private void playNextRelated() {
        List<VideoModel> related = viewModel.relatedSongs.getValue();
        if (related != null && !related.isEmpty()) loadSongFromVideo(related.get(0));
        else playFallback();
    }

    private void playNext() {
        playNextRelated();
    }

    private void playPrevious() {
        if (queueManager.moveToPrevious()) {
            ReservationModel prev = queueManager.getNowPlaying();
            if (prev != null) loadSong(prev);
        } else Toast.makeText(this, "No previous songs", Toast.LENGTH_SHORT).show();
    }

    private void loadSongFromVideo(@NonNull VideoModel video) {
        loadSong(new ReservationModel(video.getVideoId(), video.getTitle(), video.getChannel(), video.getThumbnail(), video.getArtist(), "Autoplay"));
    }

    private void loadSong(@NonNull ReservationModel next) {
        if (activePlayer != null) {
            isPlayingFallback = false;
            isEndingSoonAlertShown = false;
            queueManager.add(next);
            activePlayer.loadVideo(next.getVideoId(), 0f);
            persistLastPlayed(next.getVideoId(), next.getTitle(), next.getChannel());
            refreshDisplay();
            viewModel.loadRelatedSongs(next.getArtistSafe(), next.getVideoId());
        }
    }

    private void playFallback() {
        if (activePlayer == null) return;
        isPlayingFallback = true;
        activePlayer.loadVideo(FALLBACK_VIDEO_ID, 0f);
        binding.txtSongTitle.setText(R.string.fallback_title);
        binding.txtSongChannel.setText("");
        binding.txtSongSource.setText("");
        binding.tvOverlayTitle.setText(R.string.fallback_title);
        binding.tvOverlayArtist.setText("");
    }

    private void setupOverlay() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (binding.centerControls.getVisibility() == View.VISIBLE) hideControls();
                else showControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (activePlayer == null || isPlayingFallback) return false;
                float x = e.getX();
                float cur = playerTracker.getCurrentSecond();
                if (x < binding.controlsOverlay.getWidth() / 2f) {
                    activePlayer.seekTo(Math.max(0, cur - 10));
                    showSeekFeedback(binding.layoutRewindFeedback);
                } else {
                    activePlayer.seekTo(cur + 10);
                    showSeekFeedback(binding.layoutForwardFeedback);
                }
                return true;
            }
        });
        binding.controlsOverlay.setOnTouchListener((v, ev) -> {
            gestureDetector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return true;
        });
        binding.btnOverlayPlayPause.setOnClickListener(v -> {
            if (activePlayer != null) showControls();
        });
        binding.btnOverlayNext.setOnClickListener(v -> playNext());
        binding.btnOverlayPrevious.setOnClickListener(v -> playPrevious());
        binding.btnVoiceSearchOverlay.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC_PERM);
            else startVoiceSearch();
        });
    }

    private void showSeekFeedback(View l) {
        if (l != null) {
            l.setAlpha(1f);
            l.animate().alpha(0f).setDuration(600).setStartDelay(200).start();
        }
    }

    private void setupRelatedSongs() {
        binding.rvRelatedSongs.setLayoutManager(new LinearLayoutManager(this));
        relatedAdapter = new RelatedSongsAdapter(this, this, this);
        binding.rvRelatedSongs.setAdapter(relatedAdapter);
        binding.cardRelatedSongs.setVisibility(View.GONE);
    }

    @Override
    public void onAddClick(@NonNull VideoModel v) {
        loadSongFromVideo(v);
    }

    private void onFavoriteClick(@NonNull VideoModel v) {
        loadSongFromVideo(v);
    }

    @Override
    public void onFavoriteLongClick(@NonNull VideoModel v) {
        if (favoritesManager.isFavorite(v.getVideoId())) {
            favoritesManager.remove(v.getVideoId());
            Toast.makeText(this, R.string.toast_removed_favorite, Toast.LENGTH_SHORT).show();
        } else {
            favoritesManager.add(v);
            Toast.makeText(this, R.string.toast_added_favorite, Toast.LENGTH_SHORT).show();
        }
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) refreshFavoritesList();
    }

    private void refreshDisplay() {
        updateNowPlaying();
    }

    private void updateOverlayPlayPauseIcon(PlayerConstants.PlayerState state) {
        if (state == PlayerConstants.PlayerState.PLAYING) {
            binding.btnOverlayPlayPause.setImageResource(R.drawable.ic_pause);
            binding.btnOverlayPlayPause.setOnClickListener(v -> {
                if (activePlayer != null) activePlayer.pause();
                showControls();
            });
        } else {
            binding.btnOverlayPlayPause.setImageResource(R.drawable.ic_play);
            binding.btnOverlayPlayPause.setOnClickListener(v -> {
                if (activePlayer != null) activePlayer.play();
                showControls();
            });
        }
    }

    private void updateNowPlaying() {
        if (isPlayingFallback) {
            playFallback();
            return;
        }
        ReservationModel current = queueManager.getNowPlaying();
        if (current == null) {
            binding.txtSongTitle.setText(R.string.no_song_playing);
            binding.txtSongChannel.setText("");
            binding.txtSongSource.setText("");
            binding.tvOverlayTitle.setText(R.string.no_song_playing);
            binding.tvOverlayArtist.setText("");
        } else {
            String title = formatter.formatSongTitle(current.getTitle());
            binding.txtSongTitle.setText(title);
            binding.tvOverlayTitle.setText(title);
            String art = (current.getArtist() != null && !current.getArtist().isEmpty()) ? current.getArtist() : current.getChannel();
            binding.txtSongChannel.setText(art);
            binding.tvOverlayArtist.setText(art);
            String ch = current.getChannel();
            if (ch != null && !ch.isEmpty()) {
                binding.txtSongSource.setText("Source: " + ch);
                binding.txtSongSource.setVisibility(View.VISIBLE);
            } else binding.txtSongSource.setVisibility(View.GONE);
            viewModel.loadRelatedSongs(current.getArtistSafe(), current.getVideoId());
        }
    }

    private void showLoading() {
        if (binding.loadingIndicator != null) binding.loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (binding.loadingIndicator != null) binding.loadingIndicator.setVisibility(View.GONE);
    }    private final Runnable dismissResumeBannerRunnable = this::dismissResumeBanner;

    private void setupFullscreenButton() {
        binding.btnFullscreen.setOnClickListener(v -> {
            Boolean isFs = viewModel.isFullscreen.getValue();
            viewModel.setFullscreen(isFs == null || !isFs);
        });
    }

    private void enterFullscreen() {
        binding.playerWrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        binding.nestedScrollView.setVisibility(View.GONE);
        binding.bottomNavigationView.setVisibility(View.GONE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideSystemUI();
        binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit);
    }

    private void exitFullscreen() {
        binding.playerWrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        binding.nestedScrollView.setVisibility(View.VISIBLE);
        binding.bottomNavigationView.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        showSystemUI();
        binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen);
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null)
                c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void showControls() {
        binding.btnFullscreen.setVisibility(View.VISIBLE);
        binding.btnVoiceSearchOverlay.setVisibility(View.VISIBLE);
        binding.btnOverlayPrevious.setVisibility(View.VISIBLE);
        binding.topInfoBar.setVisibility(View.VISIBLE);
        binding.centerControls.setVisibility(View.VISIBLE);
        binding.btnFullscreen.animate().alpha(1f).setDuration(250).start();
        binding.btnVoiceSearchOverlay.animate().alpha(1f).setDuration(250).start();
        binding.btnOverlayPrevious.animate().alpha(1f).setDuration(250).start();
        binding.topInfoBar.animate().alpha(1f).setDuration(250).start();
        binding.centerControls.animate().alpha(1f).setDuration(250).start();
        binding.controlsOverlay.animate().alpha(1f).setDuration(250).start();
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_MS);
    }

    private void hideControls() {
        binding.btnFullscreen.animate().alpha(0f).setDuration(250).start();
        binding.btnVoiceSearchOverlay.animate().alpha(0f).setDuration(250).start();
        binding.btnOverlayPrevious.animate().alpha(0f).setDuration(250).start();
        binding.topInfoBar.animate().alpha(0f).setDuration(250).start();
        binding.centerControls.animate().alpha(0f).setDuration(250).start();
        binding.controlsOverlay.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            binding.btnFullscreen.setVisibility(View.GONE);
            binding.btnVoiceSearchOverlay.setVisibility(View.GONE);
            binding.btnOverlayPrevious.setVisibility(View.GONE);
            binding.topInfoBar.setVisibility(View.GONE);
            binding.centerControls.setVisibility(View.GONE);
        }).start();
    }

    private void showSearchDialog(@Nullable String q) {
        searchSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        if (q != null) searchInput.setText(q);
    }

    private void addToQueueFromSearch(@NonNull VideoModel v) {
        searchSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        loadSongFromVideo(v);
    }

    private void startVoiceSearch() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_prompt));
        try {
            voiceSearchLauncher.launch(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_speech_unsupported, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupFavoritesDrawer() {
        favoritesAdapter = new FavoritesAdapter(this, this::onFavoriteClick, this::onFavoriteLongClick);
        binding.favoritesDrawer.rvFavorites.setAdapter(favoritesAdapter);
        binding.favoritesDrawer.btnCloseFavorites.setOnClickListener(v -> binding.drawerLayout.closeDrawer(GravityCompat.END));
        binding.drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View d) {
                refreshFavoritesList();
            }
        });
    }

    private void refreshFavoritesList() {
        List<VideoModel> favs = favoritesManager.getAll();
        favoritesAdapter.setFavorites(favs);
        binding.favoritesDrawer.txtEmptyFavorites.setVisibility(favs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showFavorites() {
        binding.drawerLayout.openDrawer(GravityCompat.END);
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this, R.style.KantaAlertDialog)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.btn_got_it, null).show();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration c) {
        super.onConfigurationChanged(c);
        boolean ls = c.orientation == Configuration.ORIENTATION_LANDSCAPE;
        Boolean fs = viewModel.isFullscreen.getValue();
        if (ls && (fs == null || !fs)) viewModel.setFullscreen(true);
        else if (!ls && (fs != null && fs)) viewModel.setFullscreen(false);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f && Objects.requireNonNullElse(viewModel.isFullscreen.getValue(), false))
            hideSystemUI();
    }

    @Override
    public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == REQUEST_MIC_PERM && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED)
            startVoiceSearch();
        else if (r == REQUEST_MIC_PERM)
            Toast.makeText(this, R.string.toast_mic_denied, Toast.LENGTH_SHORT).show();
    }




}
