package com.sns.kanta.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public final class VideoModel {

    @SerializedName("id")
    private final int id;

    @SerializedName("title")
    private final String title;

    @SerializedName("video_id")
    private final String videoId;

    @SerializedName("thumbnail")
    private final String thumbnail;

    @SerializedName("channel")
    private final String channel;

    @SerializedName("created_at")
    private final String createdAt;

    @SerializedName("artist")
    private final String artist;

    @SerializedName("published_at")
    private final String publishedAt;

    // Gson no-arg constructor
    @SuppressWarnings("unused")
    VideoModel() {
        this.id = 0;
        this.title = "";
        this.videoId = "";
        this.thumbnail = null;
        this.channel = "";
        this.createdAt = "";
        this.artist = null;
        this.publishedAt = null;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title != null ? title : "";
    }

    @NonNull
    public String getVideoId() {
        return videoId != null ? videoId : "";
    }

    @Nullable
    public String getThumbnail() {
        return thumbnail;
    }

    @NonNull
    public String getChannel() {
        return channel != null ? channel : "";
    }

    @NonNull
    public String getCreatedAt() {
        return createdAt != null ? createdAt : "";
    }

    /**
     * Artist from the database column — preferred over SongParser extraction.
     * Nullable: not all rows have an artist set yet.
     */
    @Nullable
    public String getArtist() {
        return artist;
    }

    /**
     * Returns artist if set, otherwise falls back to channel name.
     * Never returns null — safe to use directly in UI.
     */
    @NonNull
    public String getArtistOrChannel() {
        if (artist != null && !artist.isEmpty()) return artist;
        return channel != null ? channel : "";
    }

    @Nullable
    public String getPublishedAt() {
        return publishedAt;
    }

    // ── Equality ──────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VideoModel)) return false;
        return getVideoId().equals(((VideoModel) o).getVideoId());
    }

    @Override
    public int hashCode() {
        return getVideoId().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "VideoModel{videoId='" + videoId
                + "', title='" + title
                + "', artist='" + artist + "'}";
    }
}
