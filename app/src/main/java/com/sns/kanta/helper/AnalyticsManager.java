package com.sns.kanta.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.server.VideoRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Lightweight manager to track unique app installations using a UUID.
 */
public final class AnalyticsManager {
    private static final String TAG = "AnalyticsManager";
    private static final String PREF_NAME = "app_analytics_prefs";
    private static final String KEY_INSTALLATION_ID = "installation_id";

    private static volatile AnalyticsManager instance;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AnalyticsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static AnalyticsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AnalyticsManager.class) {
                if (instance == null)
                    instance = new AnalyticsManager(context.getApplicationContext());
            }
        }
        return instance;
    }

    /**
     * Tracks the current installation by sending the unique ID to the server.
     * This performs an upsert: if the ID exists, last_seen_at is updated.
     */
    public void trackInstallation() {
        executor.execute(() -> {
            String installationId = getInstallationId();

            try {
                // Prepare simple body with ID. 
                // We use a trigger on the DB to update 'last_seen_at' automatically.
                Map<String, String> body = new HashMap<>();
                body.put("installation_id", installationId);

                String apiKey = BuildConfig.SUPABASE_ANON_KEY;
                String auth = "Bearer " + apiKey;

                Response<Void> response = VideoRepository.getInstance()
                        .getApiService()
                        .trackInstallation(apiKey, auth, body)
                        .execute();

                if (response.isSuccessful()) {
                    Log.d(TAG, "Installation tracked successfully: " + installationId);
                } else {
                    Log.w(TAG, "Failed to track installation: " + response.code() + " " + response.message());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error tracking installation", e);
            }
        });
    }

    @NonNull
    public synchronized String getInstallationId() {
        String existingId = prefs.getString(KEY_INSTALLATION_ID, null);
        if (existingId != null) {
            return existingId;
        }

        String newId = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_INSTALLATION_ID, newId).apply();
        Log.i(TAG, "Generated new Installation ID: " + newId);
        return newId;
    }
}
