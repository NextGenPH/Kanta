package com.sns.kanta.server;

import com.sns.kanta.model.ReportRequest;
import com.sns.kanta.model.VideoModel;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideos(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideosFiltered(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("title") String titleFilter,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideosByArtist(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("artist") String artistFilter,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> searchVideosByTitleOrArtist(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("or") String orFilter,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    // ── TRENDING SYSTEM ─────────────────────────────────────────────────────────

    @POST("rest/v1/song_plays")
    Call<Void> recordPlay(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Body java.util.Map<String, String> body
    );

    @GET("rest/v1/trending_songs")
    Call<List<VideoModel>> getTrendingSongs(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select
    );

    /**
     * Paginated version — used for the "Popular" chip to order by play_count across all time.
     */
    @Headers("Prefer: count=exact")
    @GET("rest/v1/trending_songs")
    Call<List<VideoModel>> getTrendingSongsPaged(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideoDetails(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("video_id") String videoIdFilter
    );

    @GET("rest/v1/trending_songs")
    Call<List<VideoModel>> getTrendingSongsFiltered(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("video_id") String videoIdFilter
    );

    // ── REMOTE MODE SYSTEM ──────────────────────────────────────────────────

    @POST("rest/v1/remote_queue")
    Call<Void> addToRemoteQueue(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Body Map<String, Object> body
    );

    @GET("rest/v1/remote_sessions")
    Call<List<Map<String, Object>>> getRemoteSession(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("session_id") String sessionId
    );

    @PATCH("rest/v1/remote_sessions")
    @Headers("Prefer: resolution=merge-duplicates")
    Call<Void> updateRemoteState(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("session_id") String sessionId,
            @Body Map<String, Object> body
    );

    @DELETE("rest/v1/remote_queue")
    Call<Void> clearRemoteQueue(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("session_id") String sessionId
    );

    @POST("rest/v1/reports")
    Call<Void> submitReport(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Body ReportRequest body
    );
}
