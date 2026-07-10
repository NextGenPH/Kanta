package com.sns.kanta;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sns.kanta.adapter.PopularArtistsAdapter;
import com.sns.kanta.adapter.SearchHistoryAdapter;
import com.sns.kanta.adapter.SongAdapter;
import com.sns.kanta.databinding.ActivitySearchBinding;
import com.sns.kanta.helper.SearchHistoryManager;
import com.sns.kanta.model.ArtistModel;
import com.sns.kanta.model.ReservationModel;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.player.GlobalPlayerManager;
import com.sns.kanta.server.VideoRepository;
import com.sns.kanta.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private ActivitySearchBinding binding;
    private final ActivityResultLauncher<Intent> voiceSearchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        binding.searchInput.setText(matches.get(0));
                    }
                }
            }
    );
    private MainViewModel viewModel;
    private SearchHistoryManager historyManager;
    private SongAdapter searchAdapter;
    private SongAdapter trendingAdapter;
    private PopularArtistsAdapter artistsAdapter;
    private SearchHistoryAdapter historyAdapter;
    private final SearchHistoryManager.HistoryListener historyListener = this::updateHistoryUI;
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        historyManager = SearchHistoryManager.getInstance(this);

        setupWindowInsets();

        setupUI();
        setupMiniPlayer();
        observeViewModel();
        historyManager.addListener(historyListener);

        viewModel.loadTrendingSongs();
        loadTopArtists();

        String initialQuery = getIntent().getStringExtra("query");
        if (initialQuery != null && !initialQuery.isEmpty()) {
            binding.searchInput.setText(initialQuery);
            binding.searchInput.setSelection(initialQuery.length());
            performSearch(initialQuery);
        } else {
            binding.searchInput.requestFocus();
            binding.searchInput.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.showSoftInput(binding.searchInput, 0);
            }, 200);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int topSafe = Math.max(systemBars.top, displayCutout.top);
            binding.appBarLayout.setPadding(0, topSafe, 0, 0);
            binding.bottomContainer.setPadding(0, 0, 0, systemBars.bottom);

            int miniPlayerHeight = (int) (64 * getResources().getDisplayMetrics().density);
            int totalBottomPadding = systemBars.bottom + miniPlayerHeight;

            binding.historySection.setPadding(systemBars.left, 0, systemBars.right, totalBottomPadding);
            binding.resultsSection.setPadding(systemBars.left, 0, systemBars.right, totalBottomPadding);

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupMiniPlayer() {
        GlobalPlayerManager gpm = GlobalPlayerManager.getInstance();
        View miniPlayerContainer = findViewById(R.id.miniPlayerView);
        if (miniPlayerContainer == null) return;

        android.widget.TextView miniTitle = miniPlayerContainer.findViewById(R.id.miniTitle);
        android.widget.TextView miniArtist = miniPlayerContainer.findViewById(R.id.miniArtist);
        android.widget.ImageView miniThumbnail = miniPlayerContainer.findViewById(R.id.miniThumbnail);
        android.widget.ImageButton btnMiniPlayPause = miniPlayerContainer.findViewById(R.id.btnMiniPlayPause);
        android.widget.ImageButton btnMiniClose = miniPlayerContainer.findViewById(R.id.btnMiniClose);
        com.google.android.material.progressindicator.LinearProgressIndicator miniProgress = miniPlayerContainer.findViewById(R.id.miniProgress);

        gpm.currentSong.observe(this, song -> {
            if (song != null) {
                miniPlayerContainer.setVisibility(View.VISIBLE);
                if (miniTitle != null) miniTitle.setText(song.getTitle());
                if (miniArtist != null) miniArtist.setText(song.getArtistSafe());
                if (miniThumbnail != null) {
                    com.bumptech.glide.Glide.with(this)
                            .load(song.getThumbnail())
                            .placeholder(R.drawable.ic_thumbnail_placeholder)
                            .into(miniThumbnail);
                }
            } else {
                miniPlayerContainer.setVisibility(View.GONE);
            }
        });

        gpm.playerState.observe(this, state -> {
            if (btnMiniPlayPause != null) {
                if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.PLAYING) {
                    btnMiniPlayPause.setImageResource(R.drawable.ic_pause);
                } else {
                    btnMiniPlayPause.setImageResource(R.drawable.ic_play);
                }
            }
        });

        gpm.currentTime.observe(this, time -> {
            Float duration = gpm.duration.getValue();
            if (duration != null && duration > 0 && miniProgress != null) {
                int progress = (int) ((time / duration) * 1000);
                miniProgress.setProgressCompat(progress, true);
            }
        });

        miniPlayerContainer.setOnClickListener(v -> {
            ReservationModel song = gpm.currentSong.getValue();
            if (song != null) {
                onVideoSelected(new VideoModel(song.getVideoId(), song.getTitle(), song.getChannel(), song.getThumbnail(), song.getArtist()));
            }
        });

        if (btnMiniClose != null) btnMiniClose.setOnClickListener(v -> gpm.stop());
        if (btnMiniPlayPause != null)
            btnMiniPlayPause.setOnClickListener(v -> miniPlayerContainer.performClick());
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnClearSearch.setOnClickListener(v -> {
            binding.searchInput.setText("");
            binding.resultsSection.setVisibility(View.GONE);
            showHistory();
        });

        binding.btnClearHistory.setOnClickListener(v -> {
            historyManager.clearHistory();
            showHistory();
        });

        binding.btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            } else {
                startVoiceSearch();
            }
        });

        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SongAdapter(this, SongAdapter.Style.HORIZONTAL_LIST, this::onVideoSelected, null);
        binding.recyclerResults.setAdapter(searchAdapter);

        binding.recyclerTrending.setLayoutManager(new LinearLayoutManager(this));
        trendingAdapter = new SongAdapter(this, SongAdapter.Style.HORIZONTAL_LIST, this::onVideoSelected, null);
        binding.recyclerTrending.setAdapter(trendingAdapter);

        binding.recyclerArtists.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        artistsAdapter = new PopularArtistsAdapter(artistName -> {
            binding.searchInput.setText(artistName);
            binding.searchInput.setSelection(artistName.length());
            performSearch(artistName);
        });
        binding.recyclerArtists.setAdapter(artistsAdapter);

        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new SearchHistoryAdapter(new SearchHistoryAdapter.OnHistoryClickListener() {
            @Override
            public void onHistoryClick(String query) {
                binding.searchInput.setText(query);
                binding.searchInput.setSelection(query.length());
                performSearch(query);
            }

            @Override
            public void onRemoveHistory(String query) {
                historyManager.removeQuery(query);
                showHistory();
            }
        });
        binding.recyclerHistory.setAdapter(historyAdapter);

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int i, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (query.isEmpty()) {
                    showHistory();
                } else {
                    scheduleSearch(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        showHistory();
    }

    private void scheduleSearch(String query) {
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        searchRunnable = () -> performSearch(query);
        searchHandler.postDelayed(searchRunnable, 400);
    }

    private void performSearch(String query) {
        binding.historySection.setVisibility(View.GONE);
        binding.resultsSection.setVisibility(View.VISIBLE);
        binding.layoutPrompt.setVisibility(View.GONE);
        viewModel.runSearch(query);
        historyManager.addSearchQuery(query);

        // Hide keyboard after starting search
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.searchInput.getWindowToken(), 0);
    }

    private void showHistory() {
        updateHistoryUI(historyManager.getHistory());
    }

    private void updateHistoryUI(List<String> history) {
        String query = binding.searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            binding.resultsSection.setVisibility(View.GONE);
            binding.historySection.setVisibility(View.VISIBLE);

            if (history.isEmpty()) {
                binding.layoutRecentHeader.setVisibility(View.GONE);
                binding.recyclerHistory.setVisibility(View.GONE);
                // Prompt is always visible in background of discovery unless blocked by history
                binding.layoutPrompt.setVisibility(View.VISIBLE);
            } else {
                binding.layoutRecentHeader.setVisibility(View.VISIBLE);
                binding.recyclerHistory.setVisibility(View.VISIBLE);
                binding.layoutPrompt.setVisibility(View.GONE);
                historyAdapter.setHistoryItems(history);
            }
        }
    }

    private void onVideoSelected(VideoModel video) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(MainActivity.EXTRA_VIDEO_ID, video.getVideoId());
        intent.putExtra(MainActivity.EXTRA_TITLE, video.getTitle());
        intent.putExtra(MainActivity.EXTRA_CHANNEL, video.getChannel());
        intent.putExtra("extra_thumbnail", video.getThumbnail());
        intent.putExtra("extra_artist", video.getArtist());

        android.app.ActivityOptions options = android.app.ActivityOptions.makeCustomAnimation(this, R.anim.slide_up, R.anim.no_animation);
        startActivity(intent, options.toBundle());
    }

    private void observeViewModel() {
        viewModel.searchResults.observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.shimmerSearch.setVisibility(View.VISIBLE);
                    binding.shimmerSearch.startShimmer();
                    binding.recyclerResults.setVisibility(View.GONE);
                    binding.txtNoResults.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.shimmerSearch.stopShimmer();
                    binding.shimmerSearch.setVisibility(View.GONE);
                    List<VideoModel> results = resource.data != null ? resource.data : new ArrayList<>();
                    binding.recyclerResults.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
                    binding.txtNoResults.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
                    searchAdapter.setSongs(results);
                    break;
                case ERROR:
                    binding.shimmerSearch.stopShimmer();
                    binding.shimmerSearch.setVisibility(View.GONE);
                    binding.recyclerResults.setVisibility(View.GONE);
                    binding.txtNoResults.setVisibility(View.VISIBLE);
                    binding.txtNoResults.setText(R.string.search_failed);
                    break;
            }
        });

        viewModel.trendingSongs.observe(this, songs -> {
            if (songs != null) {
                trendingAdapter.setSongs(songs);
                binding.txtTrendingTitle.setVisibility(songs.isEmpty() ? View.GONE : View.VISIBLE);
                binding.recyclerTrending.setVisibility(songs.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void loadTopArtists() {
        VideoRepository.getInstance().fetchTopArtists(10, new VideoRepository.ArtistCallback() {
            @Override
            public void onSuccess(List<ArtistModel> artists) {
                if (artists != null && !artists.isEmpty()) {
                    binding.txtArtistsTitle.setVisibility(View.VISIBLE);
                    binding.recyclerArtists.setVisibility(View.VISIBLE);
                    artistsAdapter.setArtists(artists);
                } else {
                    binding.txtArtistsTitle.setVisibility(View.GONE);
                    binding.recyclerArtists.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                binding.txtArtistsTitle.setVisibility(View.GONE);
                binding.recyclerArtists.setVisibility(View.GONE);
            }
        });
    }

    private void startVoiceSearch() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        voiceSearchLauncher.launch(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceSearch();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        historyManager.removeListener(historyListener);
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
    }
}