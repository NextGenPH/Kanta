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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sns.kanta.adapter.SearchHistoryAdapter;
import com.sns.kanta.adapter.SearchResultAdapter;
import com.sns.kanta.databinding.ActivitySearchBinding;
import com.sns.kanta.helper.SearchHistoryManager;
import com.sns.kanta.model.VideoModel;
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
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        historyManager = SearchHistoryManager.getInstance(this);

        setupUI();
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
        Intent result = new Intent();
        result.putExtra("video_id", video.getVideoId());
        result.putExtra("title", video.getTitle());
        result.putExtra("channel", video.getChannel());
        result.putExtra("thumbnail", video.getThumbnail());
        result.putExtra("artist", video.getArtist());
        setResult(RESULT_OK, result);
        finish();
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