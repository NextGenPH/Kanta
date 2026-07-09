package com.sns.kanta.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sns.kanta.BuildConfig;
import com.sns.kanta.data.local.dao.SearchHistoryDao;
import com.sns.kanta.data.local.db.KantaDatabase;
import com.sns.kanta.data.local.entity.SearchHistoryEntity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SearchHistoryManager {
    private static final String TAG = "SearchHistoryManager";
    private static final String PREF_NAME = "search_history_prefs";
    private static final String KEY_HISTORY = "recent_searches";
    private static final int MAX_HISTORY = 10;

    private static volatile SearchHistoryManager instance;
    private final SearchHistoryDao historyDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Cache for quick synchronous access in UI
    private final List<String> historyCache = new ArrayList<>();
    private final List<HistoryListener> listeners = new ArrayList<>();

    private SearchHistoryManager(Context context) {
        KantaDatabase db = KantaDatabase.getInstance(context);
        historyDao = db.historyDao();

        loadFromDb();
        migrateLegacyIfNeeded(context);
    }

    public static SearchHistoryManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SearchHistoryManager.class) {
                if (instance == null) {
                    instance = new SearchHistoryManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private void loadFromDb() {
        executor.execute(() -> {
            try {
                List<String> recent = historyDao.getRecentHistory(MAX_HISTORY);
                synchronized (historyCache) {
                    historyCache.clear();
                    historyCache.addAll(recent);
                }
                notifyListeners();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load history from DB", e);
            }
        });
    }

    private void migrateLegacyIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return;

        executor.execute(() -> {
            try {
                Type type = new TypeToken<List<String>>() {
                }.getType();
                List<String> saved = new Gson().fromJson(json, type);
                if (saved != null) {
                    for (String query : saved) {
                        historyDao.insertHistory(new SearchHistoryEntity(query));
                    }
                    historyDao.trimHistory(MAX_HISTORY);
                }
            } catch (Exception e) {
                Log.w(TAG, "Legacy history corrupted", e);
            }
            prefs.edit().remove(KEY_HISTORY).apply();
            loadFromDb();
        });
    }

    public void addSearchQuery(@NonNull String query) {
        if (query.trim().isEmpty()) return;

        // Update Cache
        synchronized (historyCache) {
            historyCache.remove(query);
            historyCache.add(0, query);
            if (historyCache.size() > MAX_HISTORY) {
                historyCache.remove(historyCache.size() - 1);
            }
        }
        notifyListeners();

        // Update DB
        executor.execute(() -> {
            historyDao.insertHistory(new SearchHistoryEntity(query));
            historyDao.trimHistory(MAX_HISTORY);
        });
    }

    @NonNull
    public List<String> getHistory() {
        synchronized (historyCache) {
            return new ArrayList<>(historyCache);
        }
    }

    public void clearHistory() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Clearing all search history");
        }
        synchronized (historyCache) {
            historyCache.clear();
        }
        notifyListeners();

        executor.execute(() -> {
            historyDao.clearHistory();
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "History cleared from database");
            }
        });
    }

    public void removeQuery(@NonNull String query) {
        synchronized (historyCache) {
            historyCache.remove(query);
        }
        notifyListeners();

        executor.execute(() -> {
            historyDao.deleteHistory(query);
        });
    }

    public void addListener(@NonNull HistoryListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
        // Send current cache state immediately
        listener.onHistoryChanged(getHistory());
    }

    public void removeListener(@NonNull HistoryListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        List<String> current = getHistory();
        synchronized (listeners) {
            for (HistoryListener listener : listeners) {
                mainHandler.post(() -> listener.onHistoryChanged(current));
            }
        }
    }

    public interface HistoryListener {
        void onHistoryChanged(@NonNull List<String> history);
    }
}
