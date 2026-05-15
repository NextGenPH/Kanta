package com.sns.kanta.server;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.model.UpdateModel;
import com.sns.kanta.model.VideoModel;

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

public final class VideoRepository {

    // ── Page sizes ────────────────────────────────────────────────────────────
    public static final int PAGE_SIZE = 50;
    private static final String TAG = "VideoRepository";
    // ── Supabase ──────────────────────────────────────────────────────────────
    private static final String SUPABASE_URL = "https://azjtejdxfqjwyxquszzm.supabase.co";
    private static final String SUPABASE_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String AUTH_HEADER = "Bearer " + SUPABASE_KEY;
    // ── Select fields — includes artist + published_at ────────────────────────
    private static final String SELECT_FIELDS =
            "id,title,video_id,thumbnail,channel,created_at,artist,published_at";
    private static final int RELATED_PAGE_SIZE = 30;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile VideoRepository instance;
    // ── Dependencies ──────────────────────────────────────────────────────────
    private final ApiService apiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService feedExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    // ── Guards ────────────────────────────────────────────────────────────────
    private final AtomicBoolean isFetching = new AtomicBoolean(false);
    private volatile Call<List<VideoModel>> activeCall;
    private volatile Call<List<VideoModel>> activeArtistCall;

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

    // ── Retrofit ──────────────────────────────────────────────────────────────

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

    // ── Callback interface ────────────────────────────────────────────────────

    /**
     * Fetch one page. Searches title AND artist columns simultaneously
     * so voice/text search returns results from both.
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
                    mainHandler.post(() -> callback.onError("Network error"));
                }
            } finally {
                isFetching.set(false);
                activeCall = null;
            }
        });
    }

    public void cancelActive() {
        Call<List<VideoModel>> call = activeCall;
        if (call != null && !call.isCanceled()) call.cancel();
        isFetching.set(false);
        activeCall = null;
        Log.d(TAG, "cancelActive — lock released");
    }

    // ── Main feed ─────────────────────────────────────────────────────────────

    /**
     * Fetch related songs.
     * <p>
     * Strategy:
     * 1. If artist is non-empty → query the indexed `artist` column directly.
     * This is fast (btree index on artist) and accurate.
     * 2. If artist column returns 0 results → fall back to title ilike search.
     * Handles legacy rows where artist column is still null.
     */
    public void fetchRelatedSongs(@NonNull String artist,
                                  @Nullable String exclude,
                                  @NonNull PageCallback callback) {
        cancelArtistSearch();

        String safe = artist.replaceAll("[()&|!<>=*%]", "").trim();
        if (safe.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), false));
            return;
        }

        searchExecutor.execute(() -> {
            try {
                // ── Step 1: search by artist column ───────────────────────────
                Call<List<VideoModel>> call = apiService.getVideosByArtist(
                        SUPABASE_KEY, AUTH_HEADER,
                        SELECT_FIELDS,
                        "ilike.*" + safe + "*",
                        "created_at.desc",
                        RELATED_PAGE_SIZE,
                        0);

                activeArtistCall = call;
                Response<List<VideoModel>> response = call.execute();

                if (call.isCanceled()) return;

                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    // Artist column hit — use these results
                    List<VideoModel> filtered = excludeCurrent(
                            sanitize(response.body()), exclude);
                    mainHandler.post(() -> callback.onSuccess(filtered, false));
                    return;
                }

                // ── Step 2: title ilike fallback ──────────────────────────────
                Log.d(TAG, "artist column empty, falling back to title search");
                Call<List<VideoModel>> fallback = apiService.getVideosFiltered(
                        SUPABASE_KEY, AUTH_HEADER,
                        SELECT_FIELDS,
                        "ilike.*" + safe + "*",
                        "created_at.desc",
                        RELATED_PAGE_SIZE,
                        0);

                activeArtistCall = fallback;
                Response<List<VideoModel>> fbResponse = fallback.execute();

                if (fallback.isCanceled()) return;

                if (fbResponse.isSuccessful() && fbResponse.body() != null) {
                    List<VideoModel> filtered = excludeCurrent(
                            sanitize(fbResponse.body()), exclude);
                    mainHandler.post(() -> callback.onSuccess(filtered, false));
                } else {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), false));
                }

            } catch (IOException e) {
                if (!isCancellation(e)) {
                    Log.e(TAG, "fetchRelatedSongs failed", e);
                    mainHandler.post(() -> callback.onError("Network error"));
                }
            } finally {
                activeArtistCall = null;
            }
        });
    }

    public void cancelArtistSearch() {
        Call<List<VideoModel>> call = activeArtistCall;
        if (call != null && !call.isCanceled()) call.cancel();
        activeArtistCall = null;
    }

    // ── Related songs — artist column first, title fallback ───────────────────

    public void fetchVideoById(@NonNull String videoId,
                               @NonNull SingleVideoCallback callback) {
        searchExecutor.execute(() -> {
            try {
                Call<List<VideoModel>> call = apiService.getVideosByVideoId(
                        SUPABASE_KEY, AUTH_HEADER,
                        SELECT_FIELDS,
                        "eq." + videoId,
                        1, 0);

                Response<List<VideoModel>> response = call.execute();

                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(response.body().get(0)));
                } else {
                    mainHandler.post(() -> callback.onSuccess(null));
                }
            } catch (IOException e) {
                mainHandler.post(() -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        });
    }

    public ApiService getApiService() {
        return apiService;
    }

    // ── Single video lookup ───────────────────────────────────────────────────

    public void shutdown() {
        cancelActive();
        cancelArtistSearch();
        feedExecutor.shutdownNow();
        searchExecutor.shutdownNow();
    }

    // ── ApiService accessor (for UpdateManager) ───────────────────────────────

    /**
     * For empty query: plain getVideos.
     * For non-empty query: search title OR artist simultaneously using
     * Supabase's `or` filter so one request covers both columns.
     */
    @NonNull
    private Call<List<VideoModel>> buildFeedCall(int offset, @NonNull String query) {
        if (query.isEmpty()) {
            return apiService.getVideos(
                    SUPABASE_KEY, AUTH_HEADER,
                    SELECT_FIELDS,
                    "created_at.desc",
                    PAGE_SIZE,
                    offset);
        }

        String safe = query.replaceAll("[()&|!<>=*%]", "").trim();
        if (safe.isEmpty()) {
            return apiService.getVideos(
                    SUPABASE_KEY, AUTH_HEADER,
                    SELECT_FIELDS,
                    "created_at.desc",
                    PAGE_SIZE,
                    offset);
        }

        // Search title OR artist in one request
        // Supabase or() syntax: (title.ilike.*term*,artist.ilike.*term*)
        String orFilter = "(title.ilike.*" + safe + "*,artist.ilike.*" + safe + "*)";
        return apiService.searchVideosByTitleOrArtist(
                SUPABASE_KEY, AUTH_HEADER,
                SELECT_FIELDS,
                orFilter,
                "created_at.desc",
                PAGE_SIZE,
                offset);
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

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

    // ── Internal ──────────────────────────────────────────────────────────────

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
        return msg != null && (msg.equals("Canceled") || msg.equals("Socket closed"));
    }

    public void fetchLatestVersion(@NonNull UpdateCallback callback) {
        searchExecutor.execute(() -> {
            try {
                Call<List<UpdateModel>> call = apiService.getLatestVersion(
                        SUPABASE_KEY, AUTH_HEADER, "*", "version_code.desc", 1);
                Response<List<UpdateModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(response.body().get(0)));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to fetch update info"));
                }
            } catch (IOException e) {
                mainHandler.post(() -> callback.onError("Network error"));
            }
        });
    }

    public interface PageCallback {
        void onSuccess(List<VideoModel> videos, boolean hasMore);

        void onError(String message);
    }

    public interface SingleVideoCallback {
        void onSuccess(@Nullable VideoModel video);

        void onError(String message);
    }

    public interface UpdateCallback {
        void onSuccess(UpdateModel update);

        void onError(String message);
    }
}
