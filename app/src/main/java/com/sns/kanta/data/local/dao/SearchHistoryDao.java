package com.sns.kanta.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.sns.kanta.data.local.entity.SearchHistoryEntity;

import java.util.List;

@Dao
public interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHistory(SearchHistoryEntity entity);

    @Query("SELECT `query` FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    List<String> getRecentHistory(int limit);

    @Query("DELETE FROM search_history")
    void clearHistory();

    @Query("DELETE FROM search_history WHERE `query` = :query")
    void deleteHistory(String query);

    @Query("DELETE FROM search_history WHERE `query` NOT IN (SELECT `query` FROM search_history ORDER BY timestamp DESC LIMIT :limit)")
    void trimHistory(int limit);
}
