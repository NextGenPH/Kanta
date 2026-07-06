package com.sns.kanta.model;

import com.google.gson.annotations.SerializedName;

public final class ReportRequest {

    @SerializedName("media_id")
    public final String mediaId;

    @SerializedName("reason")
    public final String reason;

    public ReportRequest(String mediaId, String reason) {
        this.mediaId = mediaId;
        this.reason = reason;
    }
}
