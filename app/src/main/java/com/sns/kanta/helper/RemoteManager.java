package com.sns.kanta.helper;

import android.util.Log;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.server.VideoRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Handles the "Remote Controller" logic for Kanta.
 * This class coordinates with the Web Player via Supabase.
 */
public final class RemoteManager {
    private static final String TAG = "RemoteManager";
    private static RemoteManager instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final androidx.lifecycle.MutableLiveData<Map<String, Object>> _sessionState = new androidx.lifecycle.MutableLiveData<>();
    public final androidx.lifecycle.LiveData<Map<String, Object>> sessionState = _sessionState;
    private volatile String activeSessionId = null;
    private volatile boolean isRemoteModeEnabled = false;

    private RemoteManager() {
    }

    public static synchronized RemoteManager getInstance() {
        if (instance == null) instance = new RemoteManager();
        return instance;
    }

    public void connectToSession(String code, ConnectionCallback callback) {
        executor.execute(() -> {
            try {
                Response<List<Map<String, Object>>> response = VideoRepository.getInstance().getApiService().getRemoteSession(
                        BuildConfig.SUPABASE_ANON_KEY,
                        "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        "eq." + code
                ).execute();

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    this.activeSessionId = code;
                    this.isRemoteModeEnabled = true;
                    this._sessionState.postValue(response.body().get(0));
                    callback.onConnected();
                } else {
                    callback.onError("Invalid session code. Please check your TV screen.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                callback.onError("Network error. Could not verify code.");
            }
        });
    }

    public void disconnect() {
        if (activeSessionId != null) {
            String sessionIdToClear = activeSessionId;
            executor.execute(() -> {
                try {
                    VideoRepository.getInstance().getApiService().clearRemoteQueue(
                            BuildConfig.SUPABASE_ANON_KEY,
                            "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                            "eq." + sessionIdToClear
                    ).execute();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Cleared remote queue for session: " + sessionIdToClear);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to clear remote queue", e);
                }
            });
        }
        this.activeSessionId = null;
        this.isRemoteModeEnabled = false;
    }

    public boolean isRemoteMode() {
        return isRemoteModeEnabled && activeSessionId != null;
    }

    public String getFormattedSessionId() {
        return activeSessionId != null ? activeSessionId : "";
    }

    /**
     * Sends a song to the Web Player's queue.
     */
    public void sendToRemoteQueue(VideoModel video) {
        if (!isRemoteMode()) return;

        executor.execute(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("session_id", activeSessionId);
                body.put("video_id", video.getVideoId());
                body.put("title", video.getTitle());
                body.put("artist", video.getArtist());
                body.put("thumbnail", video.getThumbnail());

                VideoRepository.getInstance().getApiService().addToRemoteQueue(
                        BuildConfig.SUPABASE_ANON_KEY,
                        "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        body
                ).execute();

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Sent to remote queue: " + video.getTitle());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send to remote queue", e);
            }
        });
    }

    /**
     * Commands the Web Player to perform an action (Play, Pause, Skip).
     */
    public void sendRemoteCommand(String action, Object value) {
        if (!isRemoteMode()) return;

        executor.execute(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put(action, value);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US);
                body.put("updated_at", sdf.format(new java.util.Date()));

                VideoRepository.getInstance().getApiService().updateRemoteState(
                        BuildConfig.SUPABASE_ANON_KEY,
                        "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        "eq." + activeSessionId, // CRITICAL: Added 'eq.' prefix for PostgREST
                        body
                ).execute();

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Command sent to session eq." + activeSessionId + ": " + action + "=" + value);
                }
            } catch (Exception e) {
                Log.e(TAG, "Command failed: " + action, e);
            }
        });
    }

    public interface ConnectionCallback {
        void onConnected();

        void onError(String message);
    }
}
