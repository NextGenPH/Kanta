package com.sns.kanta.server;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.adapter.VideoModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Single source of truth for all video/song data from Supabase.
 * <p>
 * Threading model:
 * - Network calls run on a single background executor thread (sync Retrofit)
 * - Results are always posted back to the main thread via mainHandler
 * - AtomicBoolean prevents duplicate in-flight requests for the main feed
 * - Artist search uses a separate dedicated executor so it never blocks
 * or gets blocked by the main feed fetcher
 */
public final class VideoRepository {

    // ── Fetch config ──────────────────────────────────────────────────────────
    public static final int PAGE_SIZE = 50;
    private static final String TAG = "VideoRepository";
    // ── Supabase ──────────────────────────────────────────────────────────────
    private static final String SUPABASE_URL = "https://azjtejdxfqjwyxquszzm.supabase.co";
    private static final String SUPABASE_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String AUTH_HEADER = "Bearer " + SUPABASE_KEY;
    private static final int RELATED_PAGE_SIZE = 30;
    private static final String SELECT_FIELDS =
            "id,title,video_id,thumbnail,channel,created_at";

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile VideoRepository instance;
    // ── Dependencies ──────────────────────────────────────────────────────────
    private final ApiService apiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Main feed executor — single thread, serialises pagination requests
    private final ExecutorService feedExecutor = Executors.newSingleThreadExecutor();
    // Separate executor for related/artist searches so they never race with
    // the main feed and never get blocked by isFetching guard
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    // ── In-flight guard (main feed only) ──────────────────────────────────────
    private final AtomicBoolean isFetching = new AtomicBoolean(false);
    private volatile Call<List<VideoModel>> activeCall;
    // ── Active artist search call (separate, cancellable) ────────────────────
    private volatile Call<List<VideoModel>> activeArtistCall;

    // ── Constructor ───────────────────────────────────────────────────────────
    private VideoRepository() {
        apiService = buildRetrofit().create(ApiService.class);
    }

    @NonNull
    public static VideoRepository getInstance() {
        if (instance == null) {
            synchronized (VideoRepository.class) {
                if (instance == null) instance = new VideoRepository();
            }
        }
        return instance;
    }

    // ── OkHttp / Retrofit ─────────────────────────────────────────────────────

    private Retrofit buildRetrofit() {
        OkHttpClient.Builder http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followSslRedirects(false);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            http.addInterceptor(logging);
        }

        return new Retrofit.Builder()
                .baseUrl(SUPABASE_URL + "/")
                .client(http.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    // ── Public callback interface ─────────────────────────────────────────────

    /**
     * Fetch one page of songs with optional title search.
     * Uses AtomicBoolean guard — duplicate calls while one is in-flight
     * are silently ignored. Call {@link #cancelActive()} before starting
     * a new search to release the lock.
     *
     * @param offset rows to skip (0 = first page)
     * @param query  search term; empty string = no filter
     */
    public void fetchPage(int offset, @NonNull String query,
                          @NonNull PageCallback callback) {

        if (!isFetching.compareAndSet(false, true)) {
            Log.d(TAG, "fetchPage ignored — already fetching");
            return;
        }

        feedExecutor.execute(() -> {
            try {
                Call<List<VideoModel>> call = buildFeedCall(offset, query);
                activeCall = call;

                Response<List<VideoModel>> response = call.execute();

                if (call.isCanceled()) {
                    Log.d(TAG, "fetchPage cancelled");
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    List<VideoModel> safe = sanitize(response.body());
                    boolean hasMore = response.body().size() == PAGE_SIZE;
                    mainHandler.post(() -> callback.onSuccess(safe, hasMore));
                } else {
                    String err = "Server error " + response.code();
                    Log.w(TAG, err);
                    mainHandler.post(() -> callback.onError(err));
                }

            } catch (IOException e) {
                if (!isCancellation(e)) {
                    Log.e(TAG, "fetchPage failed", e);
                    mainHandler.post(() ->
                            callback.onError("Network error — check connection"));
                }
            } finally {
                // Always release lock — even on exception
                isFetching.set(false);
                activeCall = null;
            }
        });
    }

    // ── Main feed fetch ───────────────────────────────────────────────────────

    /**
     * Cancel the active main-feed request and reset the in-flight lock.
     * Must be called before starting a new search to allow the next
     * fetchPage() call through.
     */
    public void cancelActive() {
        Call<List<VideoModel>> call = activeCall;
        if (call != null && !call.isCanceled()) call.cancel();
        isFetching.set(false);
        activeCall = null;
        Log.d(TAG, "cancelActive — lock released");
    }

    /**
     * Fetch songs related to the currently playing song by artist name.
     * <p>
     * Uses a dedicated executor and separate call reference so it:
     * 1. Never blocks or is blocked by the main feed fetcher
     * 2. Can be independently cancelled via cancelArtistSearch()
     * 3. Does NOT touch the isFetching AtomicBoolean
     *
     * @param artist  extracted artist name from SongParser.extractArtist()
     * @param exclude videoId of the current song — excluded from results
     */
    public void fetchRelatedSongs(@NonNull String artist,
                                  @Nullable String exclude,
                                  @NonNull PageCallback callback) {

        // Cancel any in-flight artist search before starting a new one
        cancelArtistSearch();

        if (artist.trim().isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), false));
            return;
        }

        // Sanitize artist input before embedding in query param
        String safeArtist = artist.replaceAll("[()&|!<>=*%]", "").trim();
        if (safeArtist.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), false));
            return;
        }

        String filter = "ilike.*" + safeArtist + "*";

        searchExecutor.execute(() -> {
            try {
                Call<List<VideoModel>> call = apiService.getVideosFiltered(
                        SUPABASE_KEY, AUTH_HEADER,
                        SELECT_FIELDS,
                        filter,
                        "created_at.desc",
                        RELATED_PAGE_SIZE,
                        0);

                activeArtistCall = call;
                Response<List<VideoModel>> response = call.execute();

                if (call.isCanceled()) {
                    Log.d(TAG, "artist search cancelled");
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    List<VideoModel> all = sanitize(response.body());
                    List<VideoModel> filtered = excludeCurrent(all, exclude);
                    mainHandler.post(() ->
                            callback.onSuccess(filtered, false));
                } else {
                    String err = "Server error " + response.code();
                    Log.w(TAG, err);
                    mainHandler.post(() -> callback.onError(err));
                }

            } catch (IOException e) {
                if (!isCancellation(e)) {
                    Log.e(TAG, "fetchRelatedSongs failed", e);
                    mainHandler.post(() ->
                            callback.onError("Network error"));
                }
            } finally {
                activeArtistCall = null;
            }
        });
    }

    // ── Artist / related songs fetch ──────────────────────────────────────────

    public void cancelArtistSearch() {
        Call<List<VideoModel>> call = activeArtistCall;
        if (call != null && !call.isCanceled()) call.cancel();
        activeArtistCall = null;
    }

    public ApiService getApiService() {
        return apiService;
    }

    // ── Expose ApiService for UpdateManager ───────────────────────────────────

    public void shutdown() {
        cancelActive();
        cancelArtistSearch();
        feedExecutor.shutdownNow();
        searchExecutor.shutdownNow();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @NonNull
    private Call<List<VideoModel>> buildFeedCall(int offset,
                                                 @NonNull String query) {
        String filter = buildTitleFilter(query);
        if (filter != null) {
            return apiService.getVideosFiltered(
                    SUPABASE_KEY, AUTH_HEADER,
                    SELECT_FIELDS,
                    filter,
                    "created_at.desc",
                    PAGE_SIZE,
                    offset);
        }
        return apiService.getVideos(
                SUPABASE_KEY, AUTH_HEADER,
                SELECT_FIELDS,
                "created_at.desc",
                PAGE_SIZE,
                offset);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @Nullable
    private String buildTitleFilter(@NonNull String query) {
        if (query.isEmpty()) return null;
        String safe = query.replaceAll("[()&|!<>=*%]", "").trim();
        if (safe.isEmpty()) return null;
        return "ilike.*" + safe + "*";
    }

    @NonNull
    private List<VideoModel> excludeCurrent(@NonNull List<VideoModel> videos,
                                            @Nullable String excludeId) {
        if (excludeId == null || excludeId.isEmpty()) return videos;
        List<VideoModel> result = new ArrayList<>();
        for (VideoModel v : videos) {
            if (!excludeId.equals(v.getVideoId())) result.add(v);
        }
        return result;
    }

    @NonNull
    private List<VideoModel> sanitize(@NonNull List<VideoModel> raw) {
        List<VideoModel> clean = new ArrayList<>();
        for (VideoModel v : raw) {
            if (v == null) continue;
            if (v.getVideoId() == null || v.getVideoId().isEmpty()) continue;
            if (v.getTitle() == null || v.getTitle().isEmpty()) continue;
            clean.add(v);
        }
        return clean;
    }

    private boolean isCancellation(@NonNull IOException e) {
        String msg = e.getMessage();
        return msg != null
                && (msg.equals("Canceled") || msg.equals("Socket closed"));
    }

    public interface PageCallback {
        void onSuccess(List<VideoModel> videos, boolean hasMore);

        void onError(String message);
    }
}