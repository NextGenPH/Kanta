package com.sns.kanta.server;

import com.sns.kanta.model.UpdateModel;
import com.sns.kanta.model.VideoModel;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface ApiService {

    // ═══════════════════════════════════════════════════════════════════════════
    //  BASE QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    //  SEARCH BY TITLE
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    //  SEARCH BY ARTIST (for related songs)
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    //  SEARCH BY VIDEO ID (single video lookup)
    // ═══════════════════════════════════════════════════════════════════════════

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideosByVideoId(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("video_id") String videoIdFilter,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    // ═══════════════════════════════════════════════════════════════════════════
    //  FILTER BY CHANNEL
    // ═══════════════════════════════════════════════════════════════════════════

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideosWithChannelFilter(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("order") String order,
            @Query("channel") String channel,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    // ═══════════════════════════════════════════════════════════════════════════
    //  SEARCH BY TITLE OR ARTIST (combined)
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    //  APP UPDATES
    // ═══════════════════════════════════════════════════════════════════════════

    @GET("rest/v1/karaoke_apk_update")
    Call<List<UpdateModel>> getLatestVersion(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") int limit
    );
}
