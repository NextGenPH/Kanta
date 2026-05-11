package com.sns.kanta.adapter;

import com.google.gson.annotations.SerializedName;

public class VideoModel {

    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private String title;

    @SerializedName("video_id")
    private String videoId;

    @SerializedName("thumbnail")
    private String thumbnail;

    @SerializedName("channel")
    private String channel;

    @SerializedName("created_at")
    private String createdAt;

    // Constructor
    public VideoModel(int id, String title, String videoId, String thumbnail, String channel, String createdAt) {
        this.id = id;
        this.title = title;
        this.videoId = videoId;
        this.thumbnail = thumbnail;
        this.channel = channel;
        this.createdAt = createdAt;
    }

    // Getters
    public int getId() {
        return id;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}