package com.sns.kanta.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public final class VideoModel {

    @SerializedName("video_id")
    private final String videoId;
    @SerializedName("thumbnail")
    private final String thumbnail;
    @SerializedName("channel")
    private final String channel;
    @SerializedName("artist")
    private final String artist;
    @SerializedName("title")
    private final String title;
    @SerializedName("published_at")
    private final String publishedAt;
    @SerializedName("created_at")
    private final String createdAt;
    @SerializedName("play_count")
    private final Long playCount;

    // Gson no-arg constructor
    @SuppressWarnings("unused")
    VideoModel() {
        this.title = "";
        this.videoId = "";
        this.thumbnail = null;
        this.channel = "";
        this.artist = null;
        this.publishedAt = null;
        this.createdAt = null;
        this.playCount = null;
    }

    public VideoModel(String videoId, String title, String channel, String thumbnail, String artist) {
        this(videoId, title, channel, thumbnail, artist, null, null, null);
    }

    public VideoModel(String videoId, String title, String channel, String thumbnail, String artist, String publishedAt, String createdAt, Long playCount) {
        this.videoId = videoId;
        this.title = title;
        this.channel = channel;
        this.thumbnail = thumbnail;
        this.artist = artist;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.playCount = playCount;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    @NonNull
    public String getTitle() {
        return Objects.requireNonNullElse(title, "");
    }

    @NonNull
    public String getVideoId() {
        return Objects.requireNonNullElse(videoId, "");
    }

    @Nullable
    public String getThumbnail() {
        return thumbnail;
    }

    @NonNull
    public String getChannel() {
        return Objects.requireNonNullElse(channel, "");
    }

    @Nullable
    public String getArtist() {
        return artist;
    }

    @Nullable
    public String getPublishedAt() {
        return publishedAt;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Long getPlayCount() {
        return playCount;
    }

    @NonNull
    public String getArtistOrChannel() {
        if (artist != null && !artist.isEmpty()) return artist;
        return Objects.requireNonNullElse(channel, "");
    }

    public String getFormattedMetadata() {
        StringBuilder sb = new StringBuilder(getArtistOrChannel());

        if (playCount != null && playCount > 0) {
            sb.append(" • ").append(formatCount(playCount)).append(" plays");
        }

        String time = publishedAt != null ? publishedAt : createdAt;
        if (time != null) {
            sb.append(" • ").append(com.sns.kanta.helper.TimeUtils.getRelativeTime(time));
        }

        return sb.toString();
    }

    private String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format(java.util.Locale.US, "%.1fK", count / 1000.0);
        return String.format(java.util.Locale.US, "%.1fM", count / 1000000.0);
    }

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
