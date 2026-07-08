package com.sns.kanta;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import android.view.ViewGroup;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sns.kanta.adapter.RelatedSongsAdapter;
import com.sns.kanta.databinding.ActivityRemoteBinding;
import com.sns.kanta.helper.RemoteManager;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.viewmodel.MainViewModel;

public class RemoteActivity extends AppCompatActivity
        implements RelatedSongsAdapter.OnAddClickListener {

    private ActivityRemoteBinding binding;
    private MainViewModel viewModel;
    private RemoteManager remoteManager;
    private final ActivityResultLauncher<Intent> searchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    VideoModel video = new VideoModel(
                            data.getStringExtra("video_id"),
                            data.getStringExtra("title"),
                            data.getStringExtra("channel"),
                            data.getStringExtra("thumbnail"),
                            data.getStringExtra("artist")
                    );
                    playOnRemote(video);
                }
            }
    );
    private RelatedSongsAdapter trendingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityRemoteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        remoteManager = RemoteManager.getInstance();

        if (!remoteManager.isRemoteMode()) {
            finish();
            return;
        }

        setupWindowInsets();
        setupUI();
        observeViewModel();

        viewModel.loadTrendingSongs();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            
            int topInset = Math.max(systemBars.top, displayCutout.top);
            
            binding.mainContentLayout.setPadding(
                binding.mainContentLayout.getPaddingLeft(),
                topInset,
                binding.mainContentLayout.getPaddingRight(),
                binding.mainContentLayout.getPaddingBottom()
            );
            
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) binding.floatingControlsLayout.getLayoutParams();
            int baseMargin = (int) (16 * getResources().getDisplayMetrics().density);
            lp.bottomMargin = baseMargin + systemBars.bottom;
            binding.floatingControlsLayout.setLayoutParams(lp);
            
            int baseScrollPadding = (int) (100 * getResources().getDisplayMetrics().density);
            binding.nestedScrollView.setPadding(
                binding.nestedScrollView.getPaddingLeft(),
                binding.nestedScrollView.getPaddingTop(),
                binding.nestedScrollView.getPaddingRight(),
                baseScrollPadding + systemBars.bottom
            );
            
            // Side insets for landscape
            binding.coordinatorLayout.setPadding(systemBars.left, 0, systemBars.right, 0);
            
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupUI() {
        binding.tvSessionCode.setText(remoteManager.getFormattedSessionId());

        binding.btnDisconnect.setOnClickListener(v -> {
            remoteManager.disconnect();
            finish();
        });

        binding.rvTrending.setLayoutManager(new LinearLayoutManager(this));
        trendingAdapter = new RelatedSongsAdapter(this, this);
        binding.rvTrending.setAdapter(trendingAdapter);

        binding.btnRemoteNext.setOnClickListener(v -> remoteManager.sendRemoteCommand("command", "skip_next"));
        binding.btnRemotePrev.setOnClickListener(v -> remoteManager.sendRemoteCommand("command", "skip_previous"));

        binding.btnRemoteMute.setOnClickListener(v -> {
            remoteManager.sendRemoteCommand("command", "toggle_mute");
            Toast.makeText(this, "Toggled Mute on TV", Toast.LENGTH_SHORT).show();
        });

        binding.btnRemoteFullscreen.setOnClickListener(v -> {
            remoteManager.sendRemoteCommand("command", "toggle_fullscreen");
            Toast.makeText(this, "Requesting Fullscreen on TV...", Toast.LENGTH_SHORT).show();
        });

        binding.btnRemoteToggleQueue.setOnClickListener(v -> {
            remoteManager.sendRemoteCommand("command", "toggle_queue");
        });

        binding.btnRemoteClearQueue.setOnClickListener(v -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.KantaAlertDialog)
                    .setTitle("Clear TV Queue?")
                    .setMessage("This will remove all upcoming songs on the TV.")
                    .setPositiveButton("Clear", (d, w) -> {
                        remoteManager.sendRemoteCommand("command", "clear_queue");
                        Toast.makeText(this, "TV Queue Cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.searchBar.setOnClickListener(v -> searchLauncher.launch(new Intent(this, SearchActivity.class)));
        binding.btnRemoteMic.setOnClickListener(v -> {
            searchLauncher.launch(new Intent(this, SearchActivity.class));
        });
    }

    private void playOnRemote(VideoModel video) {
        remoteManager.sendToRemoteQueue(video);
        Toast.makeText(this, "Playing on TV: " + video.getTitle(), Toast.LENGTH_SHORT).show();
    }

    private void observeViewModel() {
        remoteManager.sessionState.observe(this, state -> {
            if (state == null) return;
            String songId = (String) state.get("current_video_id");
            if (songId != null && !songId.isEmpty()) {
                binding.tvRemoteNowPlaying.setText(getString(R.string.next_song_playing, songId));
            }
        });

        viewModel.trendingSongs.observe(this, songs -> trendingAdapter.setSongs(songs));
    }

    @Override
    public void onAddClick(@NonNull VideoModel v) {
        playOnRemote(v);
    }
}