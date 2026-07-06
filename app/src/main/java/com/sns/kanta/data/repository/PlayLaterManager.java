package com.sns.kanta.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.sns.kanta.data.local.dao.PlayLaterDao;
import com.sns.kanta.data.local.db.KantaDatabase;
import com.sns.kanta.data.local.entity.PlayLaterEntity;
import com.sns.kanta.model.VideoModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlayLaterManager {
    private static volatile PlayLaterManager instance;
    private final PlayLaterDao playLaterDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<String> savedVideoIds = new HashSet<>();

    private PlayLaterManager(Context context) {
        playLaterDao = KantaDatabase.getInstance(context).playLaterDao();
        // Preload saved IDs into cache
        executor.execute(() -> {
            List<PlayLaterEntity> list = playLaterDao.getAllPlayLaterSongs();
            synchronized (savedVideoIds) {
                for (PlayLaterEntity e : list) {
                    savedVideoIds.add(e.videoId);
                }
            }
        });
    }

    public static PlayLaterManager getInstance(Context context) {
        if (instance == null) {
            synchronized (PlayLaterManager.class) {
                if (instance == null) {
                    instance = new PlayLaterManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Checks synchronously if a video is in the Play Later list.
     */
    public boolean isSaved(@NonNull String videoId) {
        synchronized (savedVideoIds) {
            return savedVideoIds.contains(videoId);
        }
    }

    /**
     * Toggles play later state for a video.
     *
     * @param video    The video item.
     * @param callback Called with true if added, false if removed.
     */
    public void togglePlayLater(@NonNull VideoModel video, @NonNull Callback<Boolean> callback) {
        executor.execute(() -> {
            boolean isCurrentlySaved;
            synchronized (savedVideoIds) {
                isCurrentlySaved = savedVideoIds.contains(video.getVideoId());
            }

            if (isCurrentlySaved) {
                playLaterDao.deleteById(video.getVideoId());
                synchronized (savedVideoIds) {
                    savedVideoIds.remove(video.getVideoId());
                }
                callback.onResult(false);
            } else {
                playLaterDao.insert(PlayLaterEntity.fromVideoModel(video));
                synchronized (savedVideoIds) {
                    savedVideoIds.add(video.getVideoId());
                }
                callback.onResult(true);
            }
        });
    }

    /**
     * Gets all saved play later songs ordered by save date (newest first).
     */
    public void getPlayLaterSongs(@NonNull Callback<List<VideoModel>> callback) {
        executor.execute(() -> {
            List<PlayLaterEntity> entities = playLaterDao.getAllPlayLaterSongs();
            List<VideoModel> models = new ArrayList<>();
            for (PlayLaterEntity e : entities) {
                models.add(e.toVideoModel());
            }
            callback.onResult(models);
        });
    }

    public interface Callback<T> {
        void onResult(T result);
    }
}
