package com.sns.kanta.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recent_songs")
public class RecentSongEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "video_id")
    public String videoId;

    public String title;
    public String channel;
    public String thumbnail;
    public String artist;

    public long timestamp;

    public RecentSongEntity(@NonNull String videoId, String title, String channel,
                            @Nullable String thumbnail, @Nullable String artist) {
        this.videoId = videoId;
        this.title = title;
        this.channel = channel;
        this.thumbnail = thumbnail;
        this.artist = artist;
        this.timestamp = System.currentTimeMillis();
    }
}
