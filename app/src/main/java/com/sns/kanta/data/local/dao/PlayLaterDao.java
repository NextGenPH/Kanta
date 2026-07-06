package com.sns.kanta.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.sns.kanta.data.local.entity.PlayLaterEntity;

import java.util.List;

@Dao
public interface PlayLaterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PlayLaterEntity song);

    @Query("DELETE FROM play_later_songs WHERE video_id = :videoId")
    void deleteById(String videoId);

    @Query("SELECT * FROM play_later_songs ORDER BY timestamp DESC")
    List<PlayLaterEntity> getAllPlayLaterSongs();

    @Query("SELECT * FROM play_later_songs WHERE video_id = :videoId LIMIT 1")
    PlayLaterEntity getById(String videoId);

    @Query("SELECT COUNT(*) FROM play_later_songs")
    int getCount();
}
