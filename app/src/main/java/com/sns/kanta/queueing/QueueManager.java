package com.sns.kanta.queueing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sns.kanta.model.ReservationModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simplified Manager for v1.6.
 * Manages only the "Now Playing" item and a short "History" for the Previous button.
 * Reservation/Queue logic removed.
 */
public final class QueueManager {

    private static final String TAG = "QueueManager";
    private static final String PREF_NAME = "karaoke_queue";
    private static final String KEY_CURRENT = "now_playing";
    private static final int MAX_HISTORY = 10;

    private static volatile QueueManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final List<ReservationModel> queue = new ArrayList<>();
    private final List<ReservationModel> history = new ArrayList<>();

    private QueueManager(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadFromPrefs();
    }

    @NonNull
    public static QueueManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (QueueManager.class) {
                if (instance == null) {
                    instance = new QueueManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private void loadFromPrefs() {
        String json = prefs.getString(KEY_CURRENT, null);
        if (json == null || json.isEmpty()) return;
        try {
            ReservationModel saved = gson.fromJson(json, ReservationModel.class);
            if (saved != null) queue.add(saved);
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "Prefs corrupted", e);
        }
    }

    private void persist() {
        if (!queue.isEmpty()) {
            prefs.edit().putString(KEY_CURRENT, gson.toJson(queue.get(0))).apply();
        } else {
            prefs.edit().remove(KEY_CURRENT).apply();
        }
    }

    public synchronized void add(@NonNull ReservationModel item) {
        if (!queue.isEmpty()) {
            ReservationModel current = queue.remove(0);
            addToHistory(current);
        }
        queue.add(item);
        persist();
    }

    public synchronized void clearQueue() {
        if (!queue.isEmpty()) {
            ReservationModel current = queue.remove(0);
            addToHistory(current);
        }
        persist();
    }

    private void addToHistory(ReservationModel item) {
        history.remove(item);
        history.add(0, item);
        if (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
    }

    public synchronized boolean moveToPrevious() {
        if (history.isEmpty()) return false;
        ReservationModel prev = history.remove(0);
        queue.clear();
        queue.add(prev);
        persist();
        return true;
    }

    @Nullable
    public synchronized ReservationModel getNowPlaying() {
        return queue.isEmpty() ? null : queue.get(0);
    }

    public synchronized boolean isInQueue(@Nullable String videoId) {
        if (videoId == null || queue.isEmpty()) return false;
        return Objects.equals(videoId, queue.get(0).getVideoId());
    }
}
