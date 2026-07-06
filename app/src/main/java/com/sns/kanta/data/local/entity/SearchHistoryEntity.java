package com.sns.kanta.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history")
public class SearchHistoryEntity {
    @PrimaryKey
    @NonNull
    public String query;

    public long timestamp;

    public SearchHistoryEntity(@NonNull String query) {
        this.query = query;
        this.timestamp = System.currentTimeMillis();
    }
}
