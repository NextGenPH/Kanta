package com.sns.kanta.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.sns.kanta.data.local.entity.RecentSongEntity;

import java.util.List;

@Dao
public interface RecentSongsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecent(RecentSongEntity song);

    @Query("SELECT * FROM recent_songs ORDER BY timestamp DESC LIMIT :limit")
    List<RecentSongEntity> getRecentSongs(int limit);

    @Query("DELETE FROM recent_songs")
    void clearRecent();

    @Query("SELECT COUNT(*) FROM recent_songs")
    int getTotalSongsSung();
}
