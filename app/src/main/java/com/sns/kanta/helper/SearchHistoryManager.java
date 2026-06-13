package com.sns.kanta.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class SearchHistoryManager {
    private static final String PREF_NAME = "search_history_prefs";
    private static final String KEY_HISTORY = "recent_searches";
    private static final int MAX_HISTORY = 10;

    private static volatile SearchHistoryManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    private SearchHistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
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

    public void addSearchQuery(@NonNull String query) {
        if (query.trim().isEmpty()) return;
        List<String> history = getHistory();
        history.remove(query); // Remove if exists to move to top
        history.add(0, query);
        if (history.size() > MAX_HISTORY) {
            history = history.subList(0, MAX_HISTORY);
        }
        saveHistory(history);
    }

    @NonNull
    public List<String> getHistory() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<String>>() {
        }.getType();
        List<String> history = gson.fromJson(json, type);
        return history != null ? history : new ArrayList<>();
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    private void saveHistory(List<String> history) {
        prefs.edit().putString(KEY_HISTORY, gson.toJson(history)).apply();
    }
}
