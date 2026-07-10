package com.sns.kanta.model;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public final class ArtistModel {
    @SerializedName("artist")
    private final String name;

    @SerializedName("total_plays")
    private final long totalPlays;

    @SerializedName("unique_songs_count")
    private final int songCount;

    public ArtistModel(String name, long totalPlays, int songCount) {
        this.name = name;
        this.totalPlays = totalPlays;
        this.songCount = songCount;
    }

    public String getName() {
        return name != null ? name : "Unknown Artist";
    }

    public long getTotalPlays() {
        return totalPlays;
    }

    public int getSongCount() {
        return songCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtistModel that = (ArtistModel) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
