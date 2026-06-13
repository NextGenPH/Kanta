package com.sns.kanta.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.sns.kanta.model.VideoModel;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton manager for user's favorite songs.
 * Persisted to SharedPreferences.
 */
public final class FavoritesManager {

    private static final String TAG = "FavoritesManager";
    private static final String PREF_NAME = "karaoke_favorites";
    private static final String KEY_FAVORITES = "favorite_songs";

    private static volatile FavoritesManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final List<VideoModel> favorites = new ArrayList<>();

    private FavoritesManager(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadFromPrefs();
    }

    @NonNull
    public static FavoritesManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (FavoritesManager.class) {
                if (instance == null) {
                    instance = new FavoritesManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private void loadFromPrefs() {
        String json = prefs.getString(KEY_FAVORITES, null);
        if (json == null || json.isEmpty()) return;
        try {
            Type type = new TypeToken<List<VideoModel>>() {
            }.getType();
            List<VideoModel> saved = gson.fromJson(json, type);
            if (saved != null) {
                for (VideoModel item : saved) {
                    if (item != null && !item.getVideoId().isEmpty()) {
                        favorites.add(item);
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "Favorites corrupted, resetting", e);
            prefs.edit().remove(KEY_FAVORITES).apply();
        }
    }

    private void persist() {
        prefs.edit()
                .putString(KEY_FAVORITES, gson.toJson(favorites))
                .apply();
    }

    public synchronized void add(@NonNull VideoModel video) {
        if (isFavorite(video.getVideoId())) return;
        favorites.add(0, video); // Add to top
        persist();
    }

    public synchronized void remove(@NonNull String videoId) {
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).getVideoId().equals(videoId)) {
                favorites.remove(i);
                persist();
                break;
            }
        }
    }

    public synchronized boolean isFavorite(String videoId) {
        if (videoId == null) return false;
        for (VideoModel v : favorites) {
            if (videoId.equals(v.getVideoId())) return true;
        }
        return false;
    }

    @NonNull
    public synchronized List<VideoModel> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(favorites));
    }
}
