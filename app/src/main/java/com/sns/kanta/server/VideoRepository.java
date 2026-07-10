package com.sns.kanta.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.R;
import com.sns.kanta.core.KantaApp;
import com.sns.kanta.core.Resource;
import com.sns.kanta.model.ArtistModel;
import com.sns.kanta.model.VideoModel;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    public static final int PAGE_SIZE = 50;
    private static final String TAG = "VideoRepository";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SELECT_FIELDS = "id,title,video_id,thumbnail,channel,created_at,artist,published_at";
    private static final int RELATED_PAGE_SIZE = 30;
    private static final Map<String, Long> playCountCache = new ConcurrentHashMap<>();

    private static volatile VideoRepository instance;
    private final ApiService apiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

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

    private Retrofit buildRetrofit() {
        OkHttpClient.Builder client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(new HttpLoggingInterceptor().setLevel(
                        BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE
                ));

        return new Retrofit.Builder()
                .baseUrl(SUPABASE_URL)
                .client(client.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /**
     * Modernized search method using LiveData and Resource wrapper.
     */
    public LiveData<Resource<List<VideoModel>>> searchVideos(String query, int offset) {
        MutableLiveData<Resource<List<VideoModel>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        executor.execute(() -> {
            try {
                Call<List<VideoModel>> call = buildFeedCall(offset, query != null ? query : "");
                Response<List<VideoModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    result.postValue(Resource.success(sanitize(response.body())));
                } else {
                    result.postValue(Resource.error("Error: " + response.code(), null));
                }
            } catch (IOException e) {
                if (!isCancellation(e)) {
                    result.postValue(Resource.error(getHumanReadableError(e), null));
                }
            }
        });
        return result;
    }

    private String getHumanReadableError(IOException e) {
        Context context = KantaApp.getInstance();
        if (context != null) {
            if (e instanceof UnknownHostException)
                return context.getString(R.string.error_no_internet);
            if (e instanceof SocketTimeoutException)
                return context.getString(R.string.error_timeout);
            return context.getString(R.string.error_network);
        }
        if (e instanceof UnknownHostException) return "No Internet Connection";
        if (e instanceof SocketTimeoutException) return "Connection Timeout";
        return "Network Error Occurred";
    }

    public void fetchPage(int offset, @NonNull String query, @NonNull PageCallback callback) {
        if (!isFetching.compareAndSet(false, true)) return;

        executor.execute(() -> {
            try {
                Call<List<VideoModel>> call = buildFeedCall(offset, query);
                activeCall = call;
                Response<List<VideoModel>> response = call.execute();

                if (call.isCanceled()) return;

                if (response.isSuccessful() && response.body() != null) {
                    List<VideoModel> safe = sanitize(response.body());
                    boolean hasMore = response.body().size() == PAGE_SIZE;
                    mainHandler.post(() -> callback.onSuccess(safe, hasMore));
                } else {
                    handleError(response.code(), callback);
                }
            } catch (IOException e) {
                if (!isCancellation(e)) handleException(e, callback);
            } finally {
                isFetching.set(false);
                activeCall = null;
            }
        });
    }

    public void fetchRelatedSongs(@NonNull String artist, @Nullable List<String> excludeIds, @NonNull PageCallback callback) {
        cancelArtistSearch();
        String safe = artist.replaceAll("[()&|!<>=*%]", "").trim();
        if (safe.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), false));
            return;
        }

        executor.execute(() -> {
            try {
                Call<List<VideoModel>> call = apiService.getVideosByArtist(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        SELECT_FIELDS, "ilike.*" + safe + "*", "created_at.desc", RELATED_PAGE_SIZE, 0);

                activeArtistCall = call;
                Response<List<VideoModel>> response = call.execute();

                if (call.isCanceled()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(filterExcluded(sanitize(response.body()), excludeIds), false));
                    return;
                }

                // Fallback search
                Call<List<VideoModel>> fallback = apiService.getVideosFiltered(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        SELECT_FIELDS, "ilike.*" + safe + "*", "created_at.desc", RELATED_PAGE_SIZE, 0);

                activeArtistCall = fallback;
                Response<List<VideoModel>> fbResponse = fallback.execute();

                if (fbResponse.isSuccessful() && fbResponse.body() != null) {
                    mainHandler.post(() -> callback.onSuccess(filterExcluded(sanitize(fbResponse.body()), excludeIds), false));
                } else {
                    handleError(fbResponse.code(), callback);
                }
            } catch (IOException e) {
                if (!isCancellation(e)) handleException(e, callback);
            } finally {
                activeArtistCall = null;
            }
        });
    }

    public void fetchTrendingSongs(int offset, @NonNull PageCallback callback) {
        executor.execute(() -> {
            try {
                // trending_songs view: top 10 songs played in the last 7 days, sorted by play_count desc
                Call<List<VideoModel>> call = apiService.getTrendingSongs(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY, "*");
                Response<List<VideoModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<VideoModel> safe = sanitize(response.body());
                    // trending_songs view is LIMIT 10 by definition — no more pages
                    mainHandler.post(() -> callback.onSuccess(safe, false));
                } else {
                    handleError(response.code(), callback);
                }
            } catch (IOException e) {
                handleException(e, callback);
            }
        });
    }

    /**
     * Fetches the trending_songs view ordered by all-time play_count (no date window).
     * Used for the "Popular" chip — shows the most-played songs overall.
     */
    public void fetchTrendingByPopularity(int offset, @NonNull PageCallback callback) {
        executor.execute(() -> {
            try {
                // trending_songs view already has play_count — order by it descending
                Call<List<VideoModel>> call = apiService.getTrendingSongsPaged(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        "*", "play_count.desc", PAGE_SIZE, offset);
                Response<List<VideoModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<VideoModel> safe = sanitize(response.body());
                    boolean hasMore = response.body().size() == PAGE_SIZE;
                    mainHandler.post(() -> callback.onSuccess(safe, hasMore));
                } else {
                    handleError(response.code(), callback);
                }
            } catch (IOException e) {
                handleException(e, callback);
            }
        });
    }

    public void fetchOrderedSongs(@NonNull String order, int offset, @NonNull PageCallback callback) {
        executor.execute(() -> {
            try {
                Call<List<VideoModel>> call = apiService.getVideos(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        SELECT_FIELDS, order, PAGE_SIZE, offset);
                Response<List<VideoModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<VideoModel> safe = sanitize(response.body());
                    boolean hasMore = response.body().size() == PAGE_SIZE;
                    mainHandler.post(() -> callback.onSuccess(safe, hasMore));
                } else {
                    handleError(response.code(), callback);
                }
            } catch (IOException e) {
                handleException(e, callback);
            }
        });
    }

    public void fetchArtistSongs(@NonNull String artist, int offset, @NonNull PageCallback callback) {
        String safeArtist = artist.replaceAll("[()&|!<>=*%]", "").trim();
        executor.execute(() -> {
            try {
                Call<List<VideoModel>> call = apiService.getVideosByArtist(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        SELECT_FIELDS, "ilike.*" + safeArtist + "*", "created_at.desc", PAGE_SIZE, offset);
                Response<List<VideoModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<VideoModel> safe = sanitize(response.body());
                    boolean hasMore = response.body().size() == PAGE_SIZE;
                    mainHandler.post(() -> callback.onSuccess(safe, hasMore));
                } else {
                    handleError(response.code(), callback);
                }
            } catch (IOException e) {
                handleException(e, callback);
            }
        });
    }

    public void fetchTopArtists(int limit, @NonNull ArtistCallback callback) {
        executor.execute(() -> {
            try {
                Call<List<ArtistModel>> call = apiService.getTopArtists(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        "*", "total_plays.desc", limit);
                Response<List<ArtistModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<ArtistModel> artists = response.body();
                    mainHandler.post(() -> callback.onSuccess(artists));
                } else {
                    mainHandler.post(() -> callback.onError("Error fetching artists"));
                }
            } catch (IOException e) {
                mainHandler.post(() -> callback.onError(getHumanReadableError(e)));
            }
        });
    }

    public void recordSongPlay(@NonNull String videoId, @NonNull RecordPlayCallback callback) {
        executor.execute(() -> {
            try {
                java.util.Map<String, String> body = new java.util.HashMap<>();
                body.put("video_id", videoId);
                Response<Void> response = apiService.recordPlay(BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY, body).execute();
                if (response.isSuccessful()) {
                    Long current = playCountCache.get(videoId);
                    if (current != null) {
                        playCountCache.put(videoId, current + 1);
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to record play", e);
            } finally {
                mainHandler.post(callback::onProcessed);
            }
        });
    }

    private Call<List<VideoModel>> buildFeedCall(int offset, @NonNull String query) {
        String apiKey = BuildConfig.SUPABASE_ANON_KEY;
        String auth = "Bearer " + apiKey;
        if (query.isEmpty()) {
            return apiService.getVideos(apiKey, auth, SELECT_FIELDS, "created_at.desc", PAGE_SIZE, offset);
        }
        String safe = query.replaceAll("[()&|!<>=*%]", "").trim();
        String orFilter = "(title.ilike.*" + safe + "*,artist.ilike.*" + safe + "*)";
        return apiService.searchVideosByTitleOrArtist(apiKey, auth, SELECT_FIELDS, orFilter, "created_at.desc", PAGE_SIZE, offset);
    }

    private void handleError(int code, PageCallback callback) {
        String msg = (code == 401 || code == 403) ? "Auth Error" : (code >= 500 ? "Server Error" : "Error (" + code + ")");
        mainHandler.post(() -> callback.onError(msg));
    }

    private void handleException(IOException e, PageCallback callback) {
        String msg = (e instanceof UnknownHostException) ? "No Internet" : (e instanceof SocketTimeoutException ? "Timeout" : "Network Error");
        mainHandler.post(() -> callback.onError(msg));
    }

    private List<VideoModel> sanitize(List<VideoModel> raw) {
        List<VideoModel> clean = new ArrayList<>();
        if (raw == null) return clean;
        for (VideoModel v : raw) {
            if (v != null && v.getVideoId() != null && !v.getVideoId().isEmpty()) {
                if (v.getPlayCount() != null) {
                    playCountCache.put(v.getVideoId(), v.getPlayCount());
                } else {
                    Long cachedCount = playCountCache.get(v.getVideoId());
                    if (cachedCount != null) {
                        v = new VideoModel(
                                v.getVideoId(),
                                v.getTitle(),
                                v.getChannel(),
                                v.getThumbnail(),
                                v.getArtist(),
                                v.getPublishedAt(),
                                v.getCreatedAt(),
                                cachedCount
                        );
                    }
                }
                clean.add(v);
            }
        }
        return clean;
    }

    private List<VideoModel> filterExcluded(List<VideoModel> videos, List<String> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) return videos;
        List<VideoModel> result = new ArrayList<>();
        for (VideoModel v : videos) {
            if (!excludeIds.contains(v.getVideoId())) {
                result.add(v);
            }
        }
        return result;
    }

    private boolean isCancellation(IOException e) {
        return e.getMessage() != null && (e.getMessage().equals("Canceled") || e.getMessage().equals("Socket closed"));
    }

    public void cancelActive() {
        if (activeCall != null) activeCall.cancel();
    }

    public void cancelArtistSearch() {
        if (activeArtistCall != null) activeArtistCall.cancel();
    }

    public ApiService getApiService() {
        return apiService;
    }

    public String getSupabaseKey() {
        return BuildConfig.SUPABASE_ANON_KEY;
    }

    public void fetchVideoDetails(@NonNull String videoId, @NonNull VideoDetailsCallback callback) {
        executor.execute(() -> {
            try {
                Call<List<VideoModel>> call = apiService.getVideoDetails(
                        BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                        SELECT_FIELDS, "eq." + videoId
                );
                Response<List<VideoModel>> response = call.execute();
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    VideoModel video = response.body().get(0);

                    // Fetch trending (play_count) information
                    Call<List<VideoModel>> trendingCall = apiService.getTrendingSongsFiltered(
                            BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                            "*", "eq." + videoId
                    );
                    Response<List<VideoModel>> trendingResponse = trendingCall.execute();
                    Long playCount = 0L;
                    if (trendingResponse.isSuccessful() && trendingResponse.body() != null && !trendingResponse.body().isEmpty()) {
                        playCount = trendingResponse.body().get(0).getPlayCount();
                    }

                    playCountCache.put(video.getVideoId(), playCount);

                    VideoModel fullVideo = new VideoModel(
                            video.getVideoId(),
                            video.getTitle(),
                            video.getChannel(),
                            video.getThumbnail(),
                            video.getArtist(),
                            video.getPublishedAt(),
                            video.getCreatedAt(),
                            playCount
                    );
                    mainHandler.post(() -> callback.onSuccess(fullVideo));
                } else {
                    mainHandler.post(() -> callback.onError("Video details not found"));
                }
            } catch (IOException e) {
                if (!isCancellation(e)) {
                    mainHandler.post(() -> callback.onError(getHumanReadableError(e)));
                }
            }
        });
    }

    public interface PageCallback {
        void onSuccess(List<VideoModel> videos, boolean hasMore);

        void onError(String message);
    }

    public interface ArtistCallback {
        void onSuccess(List<ArtistModel> artists);

        void onError(String message);
    }

    public interface VideoDetailsCallback {
        void onSuccess(VideoModel video);

        void onError(String message);
    }

    public interface RecordPlayCallback {
        void onProcessed();
    }
}
