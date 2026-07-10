package com.sns.kanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.nextgen.updater.NextGenUpdater;
import com.sns.kanta.adapter.SongAdapter;
import com.sns.kanta.databinding.ActivityMainBinding;
import com.sns.kanta.helper.RemoteManager;
import com.sns.kanta.model.ArtistModel;
import com.sns.kanta.model.ReservationModel;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.player.GlobalPlayerManager;
import com.sns.kanta.server.VideoRepository;
import com.sns.kanta.viewmodel.MainViewModel;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "extra_video_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CHANNEL = "extra_channel";
    private final ActivityResultLauncher<Intent> searchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    onVideoSelectedFromData(result.getData());
                }
            }
    );
    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private SongAdapter homeFeedAdapter;
    private RemoteManager remoteManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        remoteManager = RemoteManager.getInstance();

        NextGenUpdater.checkForUpdates(this, true);

        setupWindowInsets();
        setupToolbar();
        setupFeed();
        setupFilters();
        setupSwipeRefresh();
        setupEmptyState();
        setupMiniPlayer();
        setupBackPressedHandler();
        observeViewModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            // Fixed top (Toolbar doesn't scroll)
            int topSafe = Math.max(systemBars.top, displayCutout.top);
            binding.appBarLayout.setPadding(0, topSafe, 0, 0);

            // Fixed bottom (Container has solid background)
            binding.bottomContainer.setPadding(0, 0, 0, systemBars.bottom);

            // RecyclerView clears the UI but content scrolls behind bottom bar
            // Mini Player is approx 64dp
            int miniPlayerHeight = (int) (64 * getResources().getDisplayMetrics().density);
            binding.rvRecentSongs.setPadding(
                    systemBars.left,
                    0,
                    systemBars.right,
                    miniPlayerHeight + systemBars.bottom
            );

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupMiniPlayer() {
        GlobalPlayerManager gpm = GlobalPlayerManager.getInstance();

        // Use the ID assigned in activity_main.xml's <include>
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

    private void setupEmptyState() {
        binding.btnResetFilter.setOnClickListener(v -> {
            com.google.android.material.chip.Chip trending = binding.filterChipGroup.findViewById(R.id.chipTrending);
            if (trending != null) {
                trending.setChecked(true);
                // The listener will trigger loadFeed("Trending")
            } else {
                viewModel.setFilter("Trending");
            }
        });
    }

    private void setupFilters() {
        // Load Top Artists for dynamic chips
        VideoRepository.getInstance().fetchTopArtists(10, new VideoRepository.ArtistCallback() {
            @Override
            public void onSuccess(List<ArtistModel> artists) {
                if (artists != null && !artists.isEmpty()) {
                    addArtistChips(artists);
                }
            }

            @Override
            public void onError(String message) {
                // Fail silently, keep static core chips
            }
        });

        // Initial core setup...
        String lastFilter = viewModel.getCurrentFilter();
        refreshChipSelection(lastFilter);

        binding.filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            com.google.android.material.chip.Chip chip = group.findViewById(checkedId);
            if (chip != null) {
                String filterValue = chip.getText().toString();
                viewModel.setFilter(filterValue);
                // Smooth scroll to keep selected chip visible
                binding.filterScroll.smoothScrollTo(chip.getLeft() - 40, 0);
            }
        });

        // Initial load
        viewModel.loadFeed(lastFilter, false);
    }

    private void addArtistChips(List<ArtistModel> artists) {
        String currentFilter = viewModel.getCurrentFilter();
        for (ArtistModel artist : artists) {
            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) getLayoutInflater()
                    .inflate(R.layout.layout_filter_chip, binding.filterChipGroup, false);
            chip.setText(artist.getName());
            chip.setId(View.generateViewId());

            if (artist.getName().equalsIgnoreCase(currentFilter)) {
                chip.setChecked(true);
            }

            binding.filterChipGroup.addView(chip);
        }
    }

    private void refreshChipSelection(String lastFilter) {
        boolean found = false;
        for (int i = 0; i < binding.filterChipGroup.getChildCount(); i++) {
            View child = binding.filterChipGroup.getChildAt(i);
            if (child instanceof com.google.android.material.chip.Chip) {
                com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) child;
                if (chip.getText().toString().equalsIgnoreCase(lastFilter)) {
                    chip.setChecked(true);
                    found = true;
                    binding.filterScroll.post(() -> {
                        if (binding != null && binding.filterScroll != null)
                            binding.filterScroll.smoothScrollTo(chip.getLeft(), 0);
                    });
                    break;
                }
            }
        }

        if (!found && binding.filterChipGroup.getChildCount() > 0) {
            View first = binding.filterChipGroup.getChildAt(0);
            if (first instanceof com.google.android.material.chip.Chip) {
                ((com.google.android.material.chip.Chip) first).setChecked(true);
            }
        }
    }

    private void setupToolbar() {
        binding.btnRemote.setOnClickListener(v -> {
            if (remoteManager.isRemoteMode()) {
                startActivity(new Intent(this, RemoteActivity.class));
            } else {
                showRemotePairingDialog();
            }
        });
        binding.btnSearch.setOnClickListener(v -> searchLauncher.launch(new Intent(this, SearchActivity.class)));
        binding.btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void setupFeed() {
        binding.rvRecentSongs.setLayoutManager(new LinearLayoutManager(this));
        homeFeedAdapter = new SongAdapter(this, SongAdapter.Style.VERTICAL_FEED, this::onVideoSelected, null);
        binding.rvRecentSongs.setAdapter(homeFeedAdapter);

        // Standard RecyclerView scroll listener for pagination
        binding.rvRecentSongs.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) { // Scrolling down
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                            // Near the end (5 items buffer)
                            viewModel.loadNextPage();
                        }
                    }
                }
            }
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadFeed(viewModel.getCurrentFilter(), false);
        });
        binding.swipeRefresh.setColorSchemeColors(getResources().getColor(R.color.accent_green, getTheme()));
    }

    private void showRemotePairingDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter Pairing Code (e.g. AB12XY)");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.KantaAlertDialog)
                .setTitle("Connect to Web Player")
                .setMessage("Enter the code shown on your TV.")
                .setView(container)
                .setPositiveButton("Connect", (d, w) -> {
                    String code = input.getText().toString().trim().toUpperCase(java.util.Locale.US);
                    if (!code.isEmpty()) {
                        remoteManager.connectToSession(code, new RemoteManager.ConnectionCallback() {
                            @Override
                            public void onConnected() {
                                runOnUiThread(() -> {
                                    startActivity(new Intent(MainActivity.this, RemoteActivity.class));
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, message, android.widget.Toast.LENGTH_SHORT).show());
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    private void observeViewModel() {
        viewModel.trendingSongs.observe(this, songs -> {
            binding.swipeRefresh.setRefreshing(false);
            binding.shimmerFeed.stopShimmer();
            binding.shimmerFeed.setVisibility(View.GONE);

            if (songs == null || songs.isEmpty()) {
                binding.rvRecentSongs.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvRecentSongs.setVisibility(View.VISIBLE);
                binding.layoutEmpty.setVisibility(View.GONE);
                homeFeedAdapter.setSongs(songs);
            }
        });

        viewModel.trendingError.observe(this, error -> {
            if (error != null) {
                binding.swipeRefresh.setRefreshing(false);
                binding.shimmerFeed.stopShimmer();
                binding.shimmerFeed.setVisibility(View.GONE);

                if ("empty_feed".equals(error)) {
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                } else {
                    android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });

        viewModel.isLoadingFeed.observe(this, isLoading -> {
            if (isLoading) {
                binding.rvRecentSongs.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.GONE);
                binding.shimmerFeed.setVisibility(View.VISIBLE);
                binding.shimmerFeed.startShimmer();

                // Scroll to top when loading a new feed
                binding.rvRecentSongs.scrollToPosition(0);
            }
        });

        viewModel.isLoadingMore.observe(this, isLoading -> {
            binding.pbLoadMore.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
    }

    private void onVideoSelected(VideoModel video) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(EXTRA_VIDEO_ID, video.getVideoId());
        intent.putExtra(EXTRA_TITLE, video.getTitle());
        intent.putExtra(EXTRA_CHANNEL, video.getChannel());
        intent.putExtra("extra_thumbnail", video.getThumbnail());
        intent.putExtra("extra_artist", video.getArtist());

        // Add transition animation
        android.app.ActivityOptions options = android.app.ActivityOptions.makeCustomAnimation(this, R.anim.slide_up, R.anim.no_animation);
        startActivity(intent, options.toBundle());
    }

    private void onVideoSelectedFromData(Intent data) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(EXTRA_VIDEO_ID, data.getStringExtra("video_id"));
        intent.putExtra(EXTRA_TITLE, data.getStringExtra("title"));
        intent.putExtra(EXTRA_CHANNEL, data.getStringExtra("channel"));
        intent.putExtra("extra_thumbnail", data.getStringExtra("thumbnail"));
        intent.putExtra("extra_artist", data.getStringExtra("artist"));
        startActivity(intent);
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }
}