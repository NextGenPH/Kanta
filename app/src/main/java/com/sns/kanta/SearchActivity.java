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

import com.sns.kanta.adapter.SearchHistoryAdapter;
import com.sns.kanta.adapter.SearchResultAdapter;
import com.sns.kanta.databinding.ActivitySearchBinding;
import com.sns.kanta.helper.SearchHistoryManager;
import com.sns.kanta.model.ReservationModel;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.player.GlobalPlayerManager;
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
    private SearchResultAdapter searchAdapter;
    private SearchResultAdapter trendingAdapter;
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

        String initialQuery = getIntent().getStringExtra("query");
        if (initialQuery != null && !initialQuery.isEmpty()) {
            binding.searchInput.setText(initialQuery);
            binding.searchInput.setSelection(initialQuery.length());
            performSearch(initialQuery);
        } else {
            // Auto-focus search input and show keyboard only if no initial query
            binding.searchInput.requestFocus();
            binding.searchInput.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT);
            }, 200);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            // 1. Fixed Top Padding for AppBarLayout
            int topSafe = Math.max(systemBars.top, displayCutout.top);
            binding.appBarLayout.setPadding(0, topSafe, 0, 0);

            // 2. Fixed Bottom Padding for system nav bar
            binding.bottomContainer.setPadding(0, 0, 0, systemBars.bottom);

            // 3. Bottom Padding for scrollable lists
            int bottomSafe = systemBars.bottom;
            int leftSafe = systemBars.left;
            int rightSafe = systemBars.right;

            // Add extra padding so content doesn't hide behind mini player (approx 64dp)
            int miniPlayerHeight = (int) (64 * getResources().getDisplayMetrics().density);

            binding.recyclerHistory.setPadding(leftSafe, 0, rightSafe, bottomSafe + miniPlayerHeight);
            binding.recyclerTrending.setPadding(leftSafe, 0, rightSafe, bottomSafe + miniPlayerHeight);
            binding.recyclerResults.setPadding(leftSafe, 0, rightSafe, bottomSafe + miniPlayerHeight);

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
        searchAdapter = new SearchResultAdapter(this, this::onVideoSelected);
        binding.recyclerResults.setAdapter(searchAdapter);

        binding.recyclerTrending.setLayoutManager(new LinearLayoutManager(this));
        trendingAdapter = new SearchResultAdapter(this, this::onVideoSelected);
        binding.recyclerTrending.setAdapter(trendingAdapter);

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
        searchHandler.postDelayed(searchRunnable, 300);
    }

    private void performSearch(String query) {
        binding.historySection.setVisibility(View.GONE);
        binding.resultsSection.setVisibility(View.VISIBLE);
        binding.layoutPrompt.setVisibility(View.GONE);
        viewModel.runSearch(query);
        historyManager.addSearchQuery(query);
    }

    private void showHistory() {
        updateHistoryUI(historyManager.getHistory());
    }

    private void updateHistoryUI(List<String> history) {
        String query = binding.searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            binding.resultsSection.setVisibility(View.GONE);
            if (history.isEmpty()) {
                binding.historySection.setVisibility(View.GONE);
                binding.layoutPrompt.setVisibility(View.VISIBLE);
            } else {
                binding.historySection.setVisibility(View.VISIBLE);
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
                    break;
                case SUCCESS:
                    binding.shimmerSearch.stopShimmer();
                    binding.shimmerSearch.setVisibility(View.GONE);
                    List<VideoModel> results = resource.data != null ? resource.data : new ArrayList<>();
                    binding.recyclerResults.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
                    binding.txtNoResults.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
                    searchAdapter.updateResults(results);
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
                // If we are currently in results mode and it was trending-based (chips removed, but logic might remain)
                // we update the results list too if it's currently showing
                if (binding.resultsSection.getVisibility() == View.VISIBLE && binding.recyclerResults.getVisibility() == View.VISIBLE) {
                    searchAdapter.updateResults(songs);
                    binding.txtNoResults.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);
                }

                // Always update the recommended trending list in history section
                trendingAdapter.updateResults(songs);
                binding.txtTrendingTitle.setVisibility(songs.isEmpty() ? View.GONE : View.VISIBLE);
                binding.recyclerTrending.setVisibility(songs.isEmpty() ? View.GONE : View.VISIBLE);
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