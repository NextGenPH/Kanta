package com.sns.kanta.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.sns.kanta.model.VideoModel;

@Entity(tableName = "play_later_songs")
public class PlayLaterEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "video_id")
    public String videoId;

    public String title;
    public String channel;
    public String thumbnail;
    public String artist;

    @ColumnInfo(name = "published_at")
    public String publishedAt;

    @ColumnInfo(name = "created_at")
    public String createdAt;

    @ColumnInfo(name = "play_count")
    public Long playCount;

    public long timestamp;

    public PlayLaterEntity() {
        this.videoId = "";
    }

    @Ignore
    public PlayLaterEntity(@NonNull String videoId, String title, String channel,
                           @Nullable String thumbnail, @Nullable String artist,
                           @Nullable String publishedAt, @Nullable String createdAt,
                           @Nullable Long playCount) {
        this.videoId = videoId;
        this.title = title;
        this.channel = channel;
        this.thumbnail = thumbnail;
        this.artist = artist;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.playCount = playCount;
        this.timestamp = System.currentTimeMillis();
    }

    public static PlayLaterEntity fromVideoModel(VideoModel model) {
        return new PlayLaterEntity(
                model.getVideoId(),
                model.getTitle(),
                model.getChannel(),
                model.getThumbnail(),
                model.getArtist(),
                model.getPublishedAt(),
                model.getCreatedAt(),
                model.getPlayCount()
        );
    }

    public VideoModel toVideoModel() {
        return new VideoModel(
                videoId,
                title,
                channel,
                thumbnail,
                artist,
                publishedAt,
                createdAt,
                playCount
        );
    }
}
