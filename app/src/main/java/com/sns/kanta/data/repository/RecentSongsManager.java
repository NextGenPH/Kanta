package com.sns.kanta.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.sns.kanta.data.local.dao.RecentSongsDao;
import com.sns.kanta.data.local.db.KantaDatabase;
import com.sns.kanta.data.local.entity.RecentSongEntity;
import com.sns.kanta.model.VideoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecentSongsManager {
    private static volatile RecentSongsManager instance;
    private final RecentSongsDao recentSongsDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private RecentSongsManager(Context context) {
        recentSongsDao = KantaDatabase.getInstance(context).recentSongsDao();
    }

    public static RecentSongsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (RecentSongsManager.class) {
                if (instance == null) {
                    instance = new RecentSongsManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void addSong(@NonNull VideoModel video) {
        executor.execute(() -> {
            recentSongsDao.insertRecent(new RecentSongEntity(
                    video.getVideoId(),
                    video.getTitle(),
                    video.getChannel(),
                    video.getThumbnail(),
                    video.getArtist()
            ));
        });
    }

    public void getRecentSongs(int limit, @NonNull Callback<List<VideoModel>> callback) {
        executor.execute(() -> {
            List<RecentSongEntity> entities = recentSongsDao.getRecentSongs(limit);
            List<VideoModel> models = new ArrayList<>();
            for (RecentSongEntity e : entities) {
                models.add(new VideoModel(e.videoId, e.title, e.channel, e.thumbnail, e.artist));
            }
            callback.onResult(models);
        });
    }

    public void clearHistory() {
        executor.execute(recentSongsDao::clearRecent);
    }

    public interface Callback<T> {
        void onResult(T result);
    }
}
