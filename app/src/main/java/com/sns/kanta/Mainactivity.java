package com.sns.kanta;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;
import com.sns.kanta.adapter.RelatedSongsAdapter;
import com.sns.kanta.adapter.SearchResultAdapter;
import com.sns.kanta.adapter.VideoModel;
import com.sns.kanta.helper.SongParser;
import com.sns.kanta.helper.TextFormatter;
import com.sns.kanta.queueing.QueueManager;
import com.sns.kanta.queueing.ReservationModel;
import com.sns.kanta.server.VideoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Mainactivity extends AppCompatActivity implements RelatedSongsAdapter.OnAddClickListener {

    // ── Intent keys ───────────────────────────────────────────────────────────
    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CHANNEL = "extra_channel";

    // ── Fallback ──────────────────────────────────────────────────────────────
    private static final String FALLBACK_VIDEO_ID = "fW10s9xwI5k";

    // ── Prefs — last played (always updated) ──────────────────────────────────
    private static final String PREF_NAME = "player_prefs";
    private static final String PREF_LAST_VIDEO = "last_video_id";
    private static final String PREF_LAST_TITLE = "last_title";
    private static final String PREF_LAST_CHANNEL = "last_channel";

    // ── Prefs — saved session (only written on pause/destroy) ─────────────────
    private static final String PREF_SAVED_VIDEO = "saved_video_id";
    private static final String PREF_SAVED_TITLE = "saved_video_title";
    private static final String PREF_SAVED_CHANNEL = "saved_video_channel";
    private static final String PREF_SAVED_TIME = "saved_video_time";

    // ── Timing ────────────────────────────────────────────────────────────────
    private static final long AUTO_HIDE_MS = 3000L;
    private static final long SEARCH_DELAY_MS = 400L;
    private static final long SEEK_DELAY_MS = 600L;  // wait for player ready

    // ── Request codes ─────────────────────────────────────────────────────────
    private static final int REQUEST_SPEECH = 100;
    private static final int REQUEST_MIC_PERM = 101;
    private final TextFormatter formatter = TextFormatter.getInstance();
    // ── Handlers ──────────────────────────────────────────────────────────────
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Handler resumeHandler = new Handler(Looper.getMainLooper());
    // ── Views ─────────────────────────────────────────────────────────────────
    private YouTubePlayerView youtubePlayerView;
    private FrameLayout playerWrapper;
    private FrameLayout playerContainer;
    private FrameLayout controlsOverlay;
    private MaterialButton btnStop;
    private MaterialButton btnNextSong;
    private FloatingActionButton btnFullscreen;
    private AppBarLayout appBarLayout;
    private NestedScrollView contentScrollView;
    private ProgressBar loadingIndicator;
    private TextView txtSongTitle;
    private TextView txtSongChannel;
    // ── Next song card ────────────────────────────────────────────────────────
    private MaterialCardView nextSongCard;
    private ImageView imgNextThumb;
    private TextView txtNextTitle;
    private TextView txtQueueCount;
    // ── Related songs ─────────────────────────────────────────────────────────
    private MaterialCardView cardRelatedSongs;
    private RecyclerView rvRelatedSongs;
    private TextView txtRelatedTitle;
    private TextView txtNoRelated;
    private RelatedSongsAdapter relatedAdapter;
    // ── Search ────────────────────────────────────────────────────────────────
    private SearchResultAdapter searchAdapter;
    private AlertDialog searchDialog;
    private EditText searchInput;
    private String currentSearchQuery = "";
    private Runnable searchRunnable;
    // ── Dependencies ──────────────────────────────────────────────────────────
    private QueueManager queueManager;
    private VideoRepository repository;
    private SharedPreferences prefs;
    // ── Player state ──────────────────────────────────────────────────────────
    private YouTubePlayer activePlayer;
    private YouTubePlayerTracker playerTracker;
    private ReservationModel pendingDirectPlay;
    private boolean isFullscreen = false;
    private boolean isBtnStripShown = false;
    private boolean isPlayingFallback = false;
    private float pendingSeekTime = 0f;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        queueManager = QueueManager.getInstance(this);
        repository = VideoRepository.getInstance();

        handleIntent();
        bindViews();
        setupToolbar();
        setupPlayer();
        setupOverlay();
        setupFullscreenButton();
        setupRelatedSongs();
        refreshDisplay();
    }    private final Runnable hideRunnable = this::hideBtnStrip;

    @Override
    protected void onPause() {
        super.onPause();
        savePlaybackState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activePlayer != null && !isPlayingFallback) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (activePlayer != null && playerTracker != null) {
                    PlayerConstants.PlayerState state = playerTracker.getState();

                    if (state == PlayerConstants.PlayerState.PAUSED || state == PlayerConstants.PlayerState.BUFFERING) {
                        activePlayer.play();
                    }
                }
            }, 200);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        savePlaybackState();
        hideHandler.removeCallbacksAndMessages(null);
        searchHandler.removeCallbacksAndMessages(null);
        resumeHandler.removeCallbacksAndMessages(null);
        if (repository != null) {
            repository.cancelActive();
            repository.cancelArtistSearch();
        }
        if (youtubePlayerView != null) youtubePlayerView.release();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        int queueSize = queueManager.getQueueSize();

        if (queueSize > 0) {
            showExitConfirmationDialog(queueSize);
        } else {
            super.onBackPressed();
            finishAffinity();
        }
    }

    private void showExitConfirmationDialog(int queueSize) {
        String message = "You have " + queueSize + " song" + (queueSize > 1 ? "s" : "") + " in your queue.\n\n" + "Closing the app will remove all reserved songs.\n\n" + "Are you sure you want to exit?";

        new AlertDialog.Builder(this, R.style.KantaAlertDialog).setTitle("Exit Karaoke?").setMessage(message).setPositiveButton("Exit", (dialog, which) -> {
            queueManager.clearQueue();
            clearSavedSession();
            finishAffinity();
        }).setNegativeButton("Stay", (dialog, which) -> {
            dialog.dismiss();
        }).setNeutralButton("Save Queue and Exit", (dialog, which) -> {
            savePlaybackState();
            Toast.makeText(this, "Queue saved for next session", Toast.LENGTH_SHORT).show();
            finishAffinity();
        }).setCancelable(false).show();
    }

    private void handleIntent() {
        String videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String channel = getIntent().getStringExtra(EXTRA_CHANNEL);
        if (videoId != null && !videoId.isEmpty()) {
            String t = title != null ? title : "Unknown Song";
            String c = channel != null ? channel : "Unknown Artist";
            pendingDirectPlay = new ReservationModel(videoId, t, c, "Direct Play");
            persistLastPlayed(videoId, t, c);
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        youtubePlayerView = findViewById(R.id.youtubePlayerView);
        playerWrapper = findViewById(R.id.playerWrapper);
        playerContainer = findViewById(R.id.playerContainer);
        controlsOverlay = findViewById(R.id.controlsOverlay);
        btnStop = findViewById(R.id.btnStop);
        btnNextSong = findViewById(R.id.btnNextSong);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        appBarLayout = findViewById(R.id.appBarLayout);
        contentScrollView = findViewById(R.id.contentScrollView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        txtSongTitle = findViewById(R.id.txtSongTitle);
        txtSongChannel = findViewById(R.id.txtSongChannel);
        nextSongCard = findViewById(R.id.nextSongCard);
        imgNextThumb = findViewById(R.id.imgNextThumb);
        txtNextTitle = findViewById(R.id.txtNextTitle);
        txtQueueCount = findViewById(R.id.txtQueueCount);
        cardRelatedSongs = findViewById(R.id.cardRelatedSongs);
        rvRelatedSongs = findViewById(R.id.rvRelatedSongs);
        txtRelatedTitle = findViewById(R.id.txtRelatedSectionTitle);
        txtNoRelated = findViewById(R.id.txtNoRelated);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Handle home
            } else if (id == R.id.nav_queue) {
                // Show queue info
                showQueueInfo();
            } else if (id == R.id.nav_help) {
                showHelp();
            } else if (id == R.id.nav_about) {
                // Show about dialog
            }
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private void clearSavedSession() {
        prefs.edit().remove(PREF_SAVED_VIDEO).remove(PREF_SAVED_TITLE).remove(PREF_SAVED_CHANNEL).remove(PREF_SAVED_TIME).apply();
    }

    private void savePlaybackState() {

        if (isPlayingFallback || playerTracker == null) {
            return;
        }

        ReservationModel current = queueManager.getNowPlaying();

        if (current == null && pendingDirectPlay == null) {
            return;
        }

        String id = current != null ? current.getVideoId() : null;
        String title = current != null ? current.getTitle() : null;
        String channel = current != null ? current.getChannel() : null;
        float time = playerTracker.getCurrentSecond();

        if (id == null || id.isEmpty() || time < 5f) {
            return;
        }

        prefs.edit().putString(PREF_SAVED_VIDEO, id).putString(PREF_SAVED_TITLE, title).putString(PREF_SAVED_CHANNEL, channel).putFloat(PREF_SAVED_TIME, time).apply();

    }

    // ── Playback state persistence ────────────────────────────────────────────

    private void persistLastPlayed(String id, String title, String channel) {
        if (id == null || id.isEmpty()) return;
        prefs.edit().putString(PREF_LAST_VIDEO, id).putString(PREF_LAST_TITLE, title).putString(PREF_LAST_CHANNEL, channel).apply();
    }

    private void setupPlayer() {
        getLifecycle().addObserver(youtubePlayerView);
        playerTracker = new YouTubePlayerTracker();

        youtubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {

            @Override
            public void onReady(@NonNull YouTubePlayer player) {
                activePlayer = player;
                player.addListener(playerTracker);
                playCurrentSong();
            }

            @Override
            public void onStateChange(@NonNull YouTubePlayer player, @NonNull PlayerConstants.PlayerState state) {
                switch (state) {
                    case BUFFERING:
                        showLoading();
                        break;
                    case PLAYING:
                    case PAUSED:
                        hideLoading();
                        break;
                    case ENDED:
                        runOnUiThread(() -> {
                            if (isPlayingFallback) checkQueueOrFallback();
                            else playNext();
                        });
                        break;
                    default:
                        break;
                }
            }
        });
    }

    // ── Player setup ──────────────────────────────────────────────────────────

    private void playCurrentSong() {
        if (activePlayer == null) return;
        showLoading();

        if (pendingDirectPlay != null) {
            isPlayingFallback = false;
            activePlayer.loadVideo(pendingDirectPlay.getVideoId(), 0f);
            if (pendingSeekTime > 0f) {
                final float seekTo = pendingSeekTime;
                pendingSeekTime = 0f;
                hideHandler.postDelayed(() -> {
                    if (activePlayer != null) {
                        activePlayer.seekTo(seekTo);
                        activePlayer.play();
                    }
                }, SEEK_DELAY_MS);
            }
            pendingDirectPlay = null;
            return;
        }
        checkQueueOrFallback();
    }

    // ── Playback logic ────────────────────────────────────────────────────────

    private void playNext() {
        clearSavedSession();
        queueManager.removePlayedSong();
        refreshDisplay();
        checkQueueOrFallback();
    }

    private void checkQueueOrFallback() {
        ReservationModel next = queueManager.getNowPlaying();
        if (next != null && activePlayer != null) {
            isPlayingFallback = false;
            activePlayer.loadVideo(next.getVideoId(), 0f);
            persistLastPlayed(next.getVideoId(), next.getTitle(), next.getChannel());
            refreshDisplay();
            loadRelatedSongs();
            Toast.makeText(this, getString(R.string.next_song_playing, formatter.formatSongTitle(next.getTitle())), Toast.LENGTH_SHORT).show();
        } else {
            playFallback();
        }
    }

    private void playFallback() {
        if (activePlayer == null) return;
        isPlayingFallback = true;
        activePlayer.loadVideo(FALLBACK_VIDEO_ID, 0f);
        txtSongTitle.setText(R.string.fallback_title);
        txtSongChannel.setText("");
        nextSongCard.setVisibility(View.GONE);
        cardRelatedSongs.setVisibility(View.GONE);
    }

    private void setupOverlay() {
        controlsOverlay.setOnClickListener(v -> {
            if (isBtnStripShown) hideBtnStrip();
            else showBtnStrip();
        });

        btnStop.setOnClickListener(v -> {
            if (activePlayer != null) {
                activePlayer.pause();
                activePlayer.seekTo(0f);
            }
            hideBtnStrip();
            Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show();
        });

        btnNextSong.setOnClickListener(v -> {
            hideBtnStrip();
            if (isPlayingFallback) checkQueueOrFallback();
            else playNext();
        });
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private void showBtnStrip() {
        isBtnStripShown = true;
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, AUTO_HIDE_MS);
    }

    private void hideBtnStrip() {
        isBtnStripShown = false;
        hideHandler.removeCallbacks(hideRunnable);
    }

    private void updateNextSongCard() {
        List<ReservationModel> upNext = queueManager.getUpNext();
        int queueCount = upNext.size();

        if (queueCount == 0) {
            nextSongCard.setVisibility(View.GONE);
            return;
        }

        nextSongCard.setVisibility(View.VISIBLE);

        ReservationModel next = upNext.get(0);
        if (next != null) {
            txtNextTitle.setText(formatter.formatSongTitle(next.getTitle()));

            String thumb = next.getThumbnail();
            if (thumb != null && !thumb.isEmpty()) {
                Glide.with(this)
                        .load(thumb)
                        .placeholder(R.drawable.ic_thumbnail_placeholder)
                        .error(R.drawable.ic_thumbnail_placeholder)
                        .into(imgNextThumb);
            } else {
                imgNextThumb.setImageResource(R.drawable.ic_thumbnail_placeholder);
            }
        }

        // Show queue count
        String countText;
        if (queueCount == 1) {
            countText = "1 more";
        } else {
            countText = queueCount + " more";
        }
        txtQueueCount.setText(countText);
    }

    private void setupRelatedSongs() {
        rvRelatedSongs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        relatedAdapter = new RelatedSongsAdapter(this, this);
        rvRelatedSongs.setAdapter(relatedAdapter);
        rvRelatedSongs.setNestedScrollingEnabled(true);
        cardRelatedSongs.setVisibility(View.GONE);
    }

    // ── Related songs ─────────────────────────────────────────────────────────

    private void loadRelatedSongs() {
        if (isPlayingFallback) {
            cardRelatedSongs.setVisibility(View.GONE);
            return;
        }
        ReservationModel current = queueManager.getNowPlaying();
        if (current == null && pendingDirectPlay == null) {
            cardRelatedSongs.setVisibility(View.GONE);
            return;
        }
        String songTitle = current != null ? current.getTitle() : pendingDirectPlay.getTitle();
        String exclude = current != null ? current.getVideoId() : pendingDirectPlay.getVideoId();
        String artist = SongParser.extractArtist(songTitle);

        if (artist.isEmpty()) {
            cardRelatedSongs.setVisibility(View.GONE);
            return;
        }

        cardRelatedSongs.setVisibility(View.VISIBLE);
        txtRelatedTitle.setText(getString(R.string.related_searching, artist.toUpperCase()));
        txtNoRelated.setVisibility(View.GONE);
        rvRelatedSongs.setVisibility(View.VISIBLE);

        repository.fetchRelatedSongs(artist, exclude, new VideoRepository.PageCallback() {
            @Override
            public void onSuccess(List<VideoModel> videos, boolean hasMore) {
                if (videos.isEmpty()) {
                    txtRelatedTitle.setText(R.string.related_title);
                    txtNoRelated.setVisibility(View.VISIBLE);
                    txtNoRelated.setText(getString(R.string.related_none_found, artist));
                    rvRelatedSongs.setVisibility(View.GONE);
                } else {
                    txtRelatedTitle.setText(getString(R.string.related_more_from, artist.toUpperCase()));
                    txtNoRelated.setVisibility(View.GONE);
                    rvRelatedSongs.setVisibility(View.VISIBLE);
                    relatedAdapter.setSongs(videos);
                }
            }

            @Override
            public void onError(String message) {
                cardRelatedSongs.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onAddClick(@NonNull VideoModel video) {
        if (queueManager.isInQueue(video.getVideoId())) {
            Toast.makeText(this, R.string.toast_already_queued, Toast.LENGTH_SHORT).show();
            return;
        }
        queueManager.add(new ReservationModel(video.getVideoId(), video.getTitle(), video.getChannel(), video.getThumbnail(), "Karaoke"));
        relatedAdapter.notifyQueueChanged();
        refreshSearchIfOpen();
        updateNextSongCard();
        Toast.makeText(this, getString(R.string.toast_added_title, formatter.formatSongTitle(video.getTitle())), Toast.LENGTH_SHORT).show();
    }

    // ── RelatedSongsAdapter.OnAddClickListener ────────────────────────────────

    private void refreshDisplay() {
        updateNowPlaying();
        updateNextSongCard();
    }

    // ── Display ───────────────────────────────────────────────────────────────

    private void updateNowPlaying() {
        if (isPlayingFallback) {
            txtSongTitle.setText(R.string.fallback_title);
            txtSongChannel.setText("");
            return;
        }
        ReservationModel current = pendingDirectPlay != null ? pendingDirectPlay : queueManager.getNowPlaying();
        if (current == null) {
            txtSongTitle.setText(R.string.no_song_playing);
            txtSongChannel.setText("");
        } else {
            txtSongTitle.setText(formatter.formatSongTitle(current.getTitle()));
            txtSongChannel.setText(formatter.formatChannelName(current.getChannel()));
        }
    }

    private void showLoading() {
        if (loadingIndicator != null) loadingIndicator.setVisibility(View.VISIBLE);
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private void hideLoading() {
        if (loadingIndicator != null) loadingIndicator.setVisibility(View.GONE);
    }

    private void setupFullscreenButton() {
        btnFullscreen.setOnClickListener(v -> {
            hideBtnStrip();
            isFullscreen = !isFullscreen;
            applyFullscreenState();
        });
    }

    // ── Fullscreen ────────────────────────────────────────────────────────────

    private void applyFullscreenState() {
        if (isFullscreen) enterFullscreen();
        else exitFullscreen();
        btnFullscreen.bringToFront();
    }

    private void enterFullscreen() {
        playerWrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        playerContainer.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ViewGroup.LayoutParams p = youtubePlayerView.getLayoutParams();
        p.width = ViewGroup.LayoutParams.MATCH_PARENT;
        p.height = ViewGroup.LayoutParams.MATCH_PARENT;
        youtubePlayerView.setLayoutParams(p);
        appBarLayout.setVisibility(View.GONE);
        contentScrollView.setVisibility(View.GONE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideSystemUI();
        btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit);
    }

    private void exitFullscreen() {
        playerWrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        playerContainer.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        ViewGroup.LayoutParams p = youtubePlayerView.getLayoutParams();
        p.width = ViewGroup.LayoutParams.MATCH_PARENT;
        p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        youtubePlayerView.setLayoutParams(p);
        appBarLayout.setVisibility(View.VISIBLE);
        contentScrollView.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        showSystemUI();
        btnFullscreen.setImageResource(R.drawable.ic_fullscreen);
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void showSearchDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_search_song, null);
        searchInput = view.findViewById(R.id.searchInput);
        TextView txtResultCount = view.findViewById(R.id.txtResultCount);
        TextView txtEmpty = view.findViewById(R.id.txtEmpty);
        ProgressBar searchProgress = view.findViewById(R.id.progressBar);
        RecyclerView recycler = view.findViewById(R.id.recyclerResults);
        MaterialButton micBtn = view.findViewById(R.id.btnMic);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SearchResultAdapter(this, this::addToQueueFromSearch);
        recycler.setAdapter(searchAdapter);

        micBtn.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC_PERM);
            } else {
                startVoiceSearch();
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
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
                currentSearchQuery = query;
                if (query.trim().isEmpty()) {
                    txtEmpty.setVisibility(View.VISIBLE);
                    txtEmpty.setText(R.string.search_prompt);
                    searchAdapter.updateResults(new ArrayList<>());
                    txtResultCount.setText(R.string.search_found_zero);
                    searchProgress.setVisibility(View.GONE);
                    return;
                }
                txtEmpty.setVisibility(View.GONE);
                searchProgress.setVisibility(View.VISIBLE);
                txtResultCount.setText(R.string.searching);
                searchRunnable = () -> runSearch(query, txtResultCount, searchProgress, txtEmpty);
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }
        });

        searchDialog = new AlertDialog.Builder(this, R.style.KantaAlertDialog).setTitle(R.string.search_title).setView(view).setNegativeButton(R.string.btn_close, null).create();
        searchDialog.show();
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void runSearch(String query, TextView countView, ProgressBar progress, TextView emptyView) {
        repository.fetchPage(0, query, new VideoRepository.PageCallback() {
            @Override
            public void onSuccess(List<VideoModel> videos, boolean hasMore) {
                progress.setVisibility(View.GONE);
                searchAdapter.updateResults(videos);
                countView.setText(getString(R.string.search_found_count, videos.size()));
                emptyView.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
                if (videos.isEmpty()) emptyView.setText(R.string.search_no_results);
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                countView.setText(getString(R.string.search_error, message));
            }
        });
    }

    private void addToQueueFromSearch(@NonNull VideoModel video) {
        if (queueManager.isInQueue(video.getVideoId())) {
            Toast.makeText(this, R.string.toast_already_queued, Toast.LENGTH_SHORT).show();
            return;
        }
        queueManager.add(new ReservationModel(video.getVideoId(), video.getTitle(), video.getChannel(), video.getThumbnail(), "Karaoke"));
        updateNextSongCard();
        relatedAdapter.notifyQueueChanged();
        Toast.makeText(this, getString(R.string.toast_added_title, formatter.formatSongTitle(video.getTitle())), Toast.LENGTH_SHORT).show();
        refreshSearchIfOpen();
    }

    private void refreshSearchIfOpen() {
        if (searchDialog == null || !searchDialog.isShowing() || currentSearchQuery.isEmpty())
            return;
        TextView count = searchDialog.findViewById(R.id.txtResultCount);
        ProgressBar prog = searchDialog.findViewById(R.id.progressBar);
        TextView empty = searchDialog.findViewById(R.id.txtEmpty);
        if (count != null && prog != null && empty != null) {
            runSearch(currentSearchQuery, count, prog, empty);
        }
    }

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_prompt));
        try {
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_speech_unsupported, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Voice search ──────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.player_menu, menu);
        return true;
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) finish();
        else if (id == R.id.menu_search) showSearchDialog();
        else if (id == R.id.menu_queue_info) showQueueInfo();
        else if (id == R.id.menu_clear_queue) confirmClearQueue();
        else if (id == R.id.menu_help) showHelp();
        return super.onOptionsItemSelected(item);
    }

    private void showQueueInfo() {
        int size = queueManager.getQueueSize();
        if (size == 0) {
            new AlertDialog.Builder(this).setTitle(R.string.queue_info_title).setMessage(R.string.queue_empty_message).setPositiveButton(R.string.btn_ok, null).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.queue_total, size)).append("\n\n");
        List<ReservationModel> all = queueManager.getAllQueue();
        int shown = Math.min(all.size(), 10);
        for (int i = 0; i < shown; i++) {
            sb.append(i + 1).append(". ").append(formatter.formatSongTitle(all.get(i).getTitle())).append("\n");
        }
        if (all.size() > 10) sb.append("\n…and ").append(all.size() - 10).append(" more");
        new AlertDialog.Builder(this).setTitle(R.string.queue_info_title).setMessage(sb.toString()).setPositiveButton(R.string.btn_ok, null).show();
    }

    private void confirmClearQueue() {
        new AlertDialog.Builder(this).setTitle(R.string.clear_queue_title).setMessage(R.string.clear_queue_message).setPositiveButton(R.string.btn_clear, (d, w) -> {
            queueManager.clearQueue();
            clearSavedSession();
            refreshDisplay();
            cardRelatedSongs.setVisibility(View.GONE);
            Toast.makeText(this, R.string.toast_queue_cleared, Toast.LENGTH_SHORT).show();
        }).setNegativeButton(R.string.btn_cancel, null).show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this, R.style.KantaAlertDialog).setTitle("How to Use").setMessage("1. Tap Search button to find and add songs\n\n" + "2. Tap center of screen for Play/Pause controls\n\n" + "3. Tap Fullscreen button for immersive view\n\n" + "4. Queue automatically plays next song\n\n" + "5. Tap Play on Next Song card to skip ahead").setPositiveButton("Got it", null).show();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideBtnStrip();
        boolean landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape && !isFullscreen) {
            isFullscreen = true;
            applyFullscreenState();
        } else if (!landscape && isFullscreen) {
            isFullscreen = false;
            applyFullscreenState();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullscreen) hideSystemUI();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQUEST_SPEECH || res != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results != null && !results.isEmpty() && searchInput != null) {
            searchInput.setText(results.get(0));
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQUEST_MIC_PERM && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceSearch();
        } else if (req == REQUEST_MIC_PERM) {
            Toast.makeText(this, R.string.toast_mic_denied, Toast.LENGTH_SHORT).show();
        }
    }


}