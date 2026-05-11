package com.sns.kanta.queueing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton queue manager. Queue is persisted to SharedPreferences so it
 * survives process death and screen rotation.
 * <p>
 * All public methods are synchronized — safe to call from any thread.
 */
public final class QueueManager {

    private static final String TAG = "QueueManager";
    private static final String PREF_NAME = "karaoke_queue";
    private static final String KEY_QUEUE = "song_queue";

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile QueueManager instance;
    // ── State ─────────────────────────────────────────────────────────────────
    private final SharedPreferences prefs;
    private final Gson gson;
    private final List<ReservationModel> queue = new ArrayList<>();

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

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadFromPrefs() {
        String json = prefs.getString(KEY_QUEUE, null);
        if (json == null || json.isEmpty()) return;
        try {
            Type type = new TypeToken<List<ReservationModel>>() {
            }.getType();
            List<ReservationModel> saved = gson.fromJson(json, type);
            if (saved != null) {
                // Filter out any corrupt entries that slipped through
                for (ReservationModel item : saved) {
                    if (item != null && !item.getVideoId().isEmpty()) {
                        queue.add(item);
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            // Corrupt prefs — start with empty queue rather than crashing
            Log.w(TAG, "Queue prefs corrupted, resetting", e);
            prefs.edit().remove(KEY_QUEUE).apply();
        }
    }

    private void persist() {
        prefs.edit()
                .putString(KEY_QUEUE, gson.toJson(queue))
                .apply();
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Add a song to the end of the queue.
     * Silently ignored if the videoId is already present.
     * Returns the 1-based position it was added at, or -1 if duplicate.
     */
    public synchronized int add(@NonNull ReservationModel item) {
        if (isInQueue(item.getVideoId())) return -1;
        queue.add(item);
        persist();
        return queue.size(); // 1-based position
    }

    /**
     * Remove the item at the given 0-based index.
     * Index 0 = now playing; use {@link #removePlayedSong()} for that case.
     */
    public synchronized void remove(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
            persist();
        }
    }

    /**
     * Pop the head of the queue (song that just finished playing).
     */
    public synchronized void removePlayedSong() {
        if (!queue.isEmpty()) {
            queue.remove(0);
            persist();
        }
    }

    /**
     * Remove all items from the queue.
     */
    public synchronized void clearQueue() {
        queue.clear();
        persist();
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * The song currently at the head of the queue (index 0).
     * Returns null if queue is empty.
     */
    @Nullable
    public synchronized ReservationModel getNowPlaying() {
        return queue.isEmpty() ? null : queue.get(0);
    }

    /**
     * Peek at the next song (index 1) without removing it.
     * Returns null if there is no next song.
     */
    @Nullable
    public synchronized ReservationModel peekNext() {
        return queue.size() >= 2 ? queue.get(1) : null;
    }

    /**
     * All songs after the current one (index 1 onwards).
     * Returns an unmodifiable snapshot — safe to iterate on any thread.
     */
    @NonNull
    public synchronized List<ReservationModel> getUpNext() {
        if (queue.size() <= 1) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(queue.subList(1, queue.size())));
    }

    /**
     * Full queue snapshot including the currently playing song at index 0.
     */
    @NonNull
    public synchronized List<ReservationModel> getAllQueue() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    public synchronized int getQueueSize() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns true if a song with this videoId is anywhere in the queue.
     */
    public synchronized boolean isInQueue(@Nullable String videoId) {
        if (videoId == null || videoId.isEmpty()) return false;
        for (ReservationModel item : queue) {
            if (videoId.equals(item.getVideoId())) return true;
        }
        return false;
    }
}