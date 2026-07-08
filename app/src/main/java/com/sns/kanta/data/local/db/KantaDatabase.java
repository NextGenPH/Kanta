package com.sns.kanta.data.local.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.sns.kanta.data.local.dao.PlayLaterDao;
import com.sns.kanta.data.local.dao.RecentSongsDao;
import com.sns.kanta.data.local.dao.SearchHistoryDao;
import com.sns.kanta.data.local.entity.PlayLaterEntity;
import com.sns.kanta.data.local.entity.RecentSongEntity;
import com.sns.kanta.data.local.entity.SearchHistoryEntity;

@Database(
        entities = {SearchHistoryEntity.class, RecentSongEntity.class, PlayLaterEntity.class},
        version = 4,
        exportSchema = false
)
public abstract class KantaDatabase extends RoomDatabase {

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Migration logic for version 1 to 2
            database.execSQL("CREATE TABLE IF NOT EXISTS `recent_songs` (`video_id` TEXT NOT NULL, `title` TEXT, `channel` TEXT, `thumbnail` TEXT, `artist` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`video_id`))");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Just drop the tables we removed from the entities list
            database.execSQL("DROP TABLE IF EXISTS `favorite_songs`");
            database.execSQL("DROP TABLE IF EXISTS `folders`");
        }
    };
    private static volatile KantaDatabase instance;

    public static KantaDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (KantaDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    KantaDatabase.class,
                                    "kanta_db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    public abstract SearchHistoryDao historyDao();

    public abstract RecentSongsDao recentSongsDao();

    public abstract PlayLaterDao playLaterDao();
}
