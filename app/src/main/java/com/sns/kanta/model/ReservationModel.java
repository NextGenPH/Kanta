package com.sns.kanta.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sns.kanta.helper.SongParser;

/**
 * Immutable data carrier for one queued song.
 * <p>
 * Artist resolution priority:
 * 1. Explicit artist field (from VideoModel.getArtist())
 * 2. SongParser extraction from title (fallback for legacy entries)
 * 3. Channel name (last resort)
 */
public final class ReservationModel {

    private final String videoId;
    private final String title;
    private final String channel;
    private final String thumbnail;
    private final String reserverName;
    private final String artist;     // nullable — from DB artist column
    private final long timestamp;

    // ── Gson no-arg ───────────────────────────────────────────────────────────
    @SuppressWarnings("unused")
    ReservationModel() {
        this.videoId = "";
        this.title = "";
        this.channel = "";
        this.thumbnail = null;
        this.reserverName = "";
        this.artist = null;
        this.timestamp = 0L;
    }

    // ── Without thumbnail ─────────────────────────────────────────────────────
    public ReservationModel(
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String channel,
            @NonNull String reserverName) {
        this(videoId, title, channel, null, null, reserverName);
    }

    // ── With thumbnail, no artist ─────────────────────────────────────────────
    public ReservationModel(
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String channel,
            @Nullable String thumbnail,
            @NonNull String reserverName) {
        this(videoId, title, channel, thumbnail, null, reserverName);
    }

    // ── Full constructor ──────────────────────────────────────────────────────
    public ReservationModel(
            @NonNull String videoId,
            @NonNull String title,
            @NonNull String channel,
            @Nullable String thumbnail,
            @Nullable String artist,
            @NonNull String reserverName) {
        this.videoId = videoId;
        this.title = title;
        this.channel = channel;
        this.thumbnail = thumbnail;
        this.artist = artist;
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

    @Nullable
    public String getArtist() {
        return artist;
    }

    @NonNull
    public String getReserverName() {
        return reserverName != null ? reserverName : "";
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the best available artist string for related-song lookup:
     * 1. DB artist column
     * 2. Extracted from title via SongParser
     * 3. Channel as last resort
     * Never returns null — safe for direct use in repository queries.
     */
    @NonNull
    public String getArtistSafe() {
        // 1. DB column
        if (artist != null && !artist.isEmpty()) return artist;
        // 2. Parse from title
        String parsed = SongParser.extractArtist(getTitle());
        if (!parsed.isEmpty()) return parsed;
        // 3. Channel fallback
        return getChannel();
    }

    // ── Equality ──────────────────────────────────────────────────────────────

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
        return "ReservationModel{videoId='" + videoId
                + "', artist='" + artist
                + "', reserver='" + reserverName + "'}";
    }
}
