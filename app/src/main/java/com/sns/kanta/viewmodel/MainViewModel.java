package com.sns.kanta.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sns.kanta.BuildConfig;
import com.sns.kanta.model.UpdateModel;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.server.VideoRepository;

import java.util.List;

public class MainViewModel extends ViewModel {

    private final VideoRepository repository = VideoRepository.getInstance();

    private final MutableLiveData<List<VideoModel>> _searchResults = new MutableLiveData<>();
    public final LiveData<List<VideoModel>> searchResults = _searchResults;

    private final MutableLiveData<List<VideoModel>> _relatedSongs = new MutableLiveData<>();
    public final LiveData<List<VideoModel>> relatedSongs = _relatedSongs;

    private final MutableLiveData<Boolean> _isSearching = new MutableLiveData<>(false);
    public final LiveData<Boolean> isSearching = _isSearching;

    private final MutableLiveData<String> _searchError = new MutableLiveData<>();
    public final LiveData<String> searchError = _searchError;

    private final MutableLiveData<String> _relatedError = new MutableLiveData<>();
    public final LiveData<String> relatedError = _relatedError;

    private final MutableLiveData<Boolean> _isFullscreen = new MutableLiveData<>(false);
    public final LiveData<Boolean> isFullscreen = _isFullscreen;

    private final MutableLiveData<UpdateModel> _updateInfo = new MutableLiveData<>();
    public final LiveData<UpdateModel> updateInfo = _updateInfo;

    private final MutableLiveData<Boolean> _isForceUpdate = new MutableLiveData<>(false);
    public final LiveData<Boolean> isForceUpdate = _isForceUpdate;

    public void setSearchResults(List<VideoModel> results) {
        _searchResults.setValue(results);
    }

    public void setFullscreen(boolean fullscreen) {
        _isFullscreen.setValue(fullscreen);
    }

    public void runSearch(String query) {
        _isSearching.setValue(true);
        repository.fetchPage(0, query, new VideoRepository.PageCallback() {
            @Override
            public void onSuccess(List<VideoModel> videos, boolean hasMore) {
                _isSearching.postValue(false);
                _searchResults.postValue(videos);
            }

            @Override
            public void onError(String message) {
                _isSearching.postValue(false);
                _searchError.postValue(message);
            }
        });
    }

    public void loadRelatedSongs(String artist, String excludeVideoId) {
        if (artist == null || artist.isEmpty()) return;
        repository.fetchRelatedSongs(artist, excludeVideoId, new VideoRepository.PageCallback() {
            @Override
            public void onSuccess(List<VideoModel> videos, boolean hasMore) {
                _relatedSongs.postValue(videos);
            }

            @Override
            public void onError(String message) {
                _relatedError.postValue(message);
            }
        });
    }

    public void checkForUpdates() {
        repository.fetchLatestVersion(new VideoRepository.UpdateCallback() {
            @Override
            public void onSuccess(UpdateModel update) {
                int currentVersion = BuildConfig.VERSION_CODE;
                if (update.getVersionCode() > currentVersion) {
                    boolean force = update.isForceUpdate() || currentVersion < update.getMinSupportedVersion();
                    _isForceUpdate.postValue(force);
                    _updateInfo.postValue(update);
                }
            }

            @Override
            public void onError(String message) {
                // Silently fail or log for background checks
            }
        });
    }
}
