package com.sns.kanta.adapter;

import com.google.gson.annotations.SerializedName;

public class UpdateModel {

    @SerializedName("id")
    private int id;

    @SerializedName("version_code")
    private int versionCode;

    @SerializedName("version_name")
    private String versionName;

    @SerializedName("download_url")
    private String downloadUrl;

    @SerializedName("release_notes")
    private String releaseNotes;

    @SerializedName("is_force_update")
    private boolean isForceUpdate;

    @SerializedName("is_active")
    private boolean isActive;

    @SerializedName("min_supported_version")
    private int minSupportedVersion;

    // Getters
    public int getId() {
        return id;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public boolean isForceUpdate() {
        return isForceUpdate;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getMinSupportedVersion() {
        return minSupportedVersion;
    }
}