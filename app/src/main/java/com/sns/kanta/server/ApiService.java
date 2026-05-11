package com.sns.kanta.server;

import com.sns.kanta.adapter.UpdateModel;
import com.sns.kanta.adapter.VideoModel;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface ApiService {

    // ── Videos — no filter ───────────────────────────────────────────────────

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

    // ── Videos — server-side title search (ilike) ─────────────────────────────
    // Supabase translates ?title=ilike.*term* → WHERE title ILIKE '%term%'

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideosFiltered(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("title") String titleFilter,   // "ilike.*term*"
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    // ── Videos — channel filter ───────────────────────────────────────────────
    // Supabase translates ?channel=eq.ChannelName → WHERE channel = 'ChannelName'

    @Headers("Prefer: count=exact")
    @GET("rest/v1/youtube_videos")
    Call<List<VideoModel>> getVideosWithChannelFilter(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("order") String order,
            @Query("channel") String channel,       // "eq.ChannelName"
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    // ── App update check ──────────────────────────────────────────────────────

    @GET("rest/v1/karaoke_apk_update")
    Call<List<UpdateModel>> getLatestVersion(
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") int limit
    );
}