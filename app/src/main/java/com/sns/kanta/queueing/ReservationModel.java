package com.sns.kanta.queueing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable data carrier representing one reserved song in the queue.
 * Gson deserializes via reflection so no-arg constructor is kept package-private.
 */
public final class ReservationModel {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final String videoId;
    private final String title;
    private final String channel;
    private final String thumbnail; // nullable — not all entries have one
    private final String reserverName;
    private final long timestamp;

    // ── No-arg constructor for Gson ───────────────────────────────────────────
    @SuppressWarnings("unused")
    ReservationModel() {
        this.videoId = "";
        this.title = "";
        this.channel = "";
        this.thumbnail = null;
        this.reserverName = "";
        this.timestamp = 0L;
    }

    // ── Constructor without thumbnail ─────────────────────────────────────────
    public ReservationModel(
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String channel,
            @NonNull String reserverName) {
        this(videoId, title, channel, null, reserverName);
    }

    // ── Full constructor ──────────────────────────────────────────────────────
    public ReservationModel(
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String channel,
            @Nullable String thumbnail,
            @NonNull String reserverName) {
        this.videoId = videoId;
        this.title = title;
        this.channel = channel;
        this.thumbnail = thumbnail;
        this.reserverName = reserverName;
        this.timestamp = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    @NonNull
    public String getVideoId() {
        return videoId != null ? videoId : "";
    }

    @NonNull
    public String getTitle() {
        return title != null ? title : "";
    }

    @NonNull
    public String getChannel() {
        return channel != null ? channel : "";
    }

    @Nullable
    public String getThumbnail() {
        return thumbnail;
    }

    @NonNull
    public String getReserverName() {
        return reserverName != null ? reserverName : "";
    }

    public long getTimestamp() {
        return timestamp;
    }

    // ── Equality — based on videoId only ──────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationModel)) return false;
        return getVideoId().equals(((ReservationModel) o).getVideoId());
    }

    @Override
    public int hashCode() {
        return getVideoId().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "ReservationModel{"
                + "videoId='" + videoId + '\''
                + ", title='" + title + '\''
                + ", reserver='" + reserverName + '\''
                + '}';
    }
}