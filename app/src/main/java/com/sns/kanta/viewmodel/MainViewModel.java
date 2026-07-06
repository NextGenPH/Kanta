package com.sns.kanta.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.sns.kanta.core.Resource;
import com.sns.kanta.model.VideoModel;
import com.sns.kanta.server.VideoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainViewModel extends ViewModel {

    private final VideoRepository repository = VideoRepository.getInstance();

    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    public final LiveData<Resource<List<VideoModel>>> searchResults = Transformations.switchMap(searchQuery, query ->
            repository.searchVideos(query, 0)
    );
    private final MutableLiveData<String> filter = new MutableLiveData<>("Trending");
    private final MutableLiveData<List<VideoModel>> _relatedSongs = new MutableLiveData<>();
    public final LiveData<List<VideoModel>> relatedSongs = _relatedSongs;

    private final MutableLiveData<Boolean> _isLoadingRelated = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoadingRelated = _isLoadingRelated;

    private final MutableLiveData<String> _relatedError = new MutableLiveData<>();
    public final LiveData<String> relatedError = _relatedError;

    private final MutableLiveData<Boolean> _isFullscreen = new MutableLiveData<>(false);
    public final LiveData<Boolean> isFullscreen = _isFullscreen;

    private final MutableLiveData<Integer> _dominantColor = new MutableLiveData<>(0);
    public final LiveData<Integer> dominantColor = _dominantColor;

    private final MutableLiveData<List<VideoModel>> _trendingSongs = new MutableLiveData<>();
    public final LiveData<List<VideoModel>> trendingSongs = _trendingSongs;

    private final MutableLiveData<String> _trendingError = new MutableLiveData<>();
    public final LiveData<String> trendingError = _trendingError;

    private final MutableLiveData<Boolean> _isLoadingFeed = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoadingFeed = _isLoadingFeed;

    private final MutableLiveData<Boolean> _isLoadingMore = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoadingMore = _isLoadingMore;

    private final MutableLiveData<VideoModel> _currentVideoDetails = new MutableLiveData<>();
    public final LiveData<VideoModel> currentVideoDetails = _currentVideoDetails;

    private final java.util.Set<String> sessionHistory = new java.util.HashSet<>();
    private int currentOffset = 0;
    private boolean hasMore = true;

    public void loadVideoDetails(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        repository.fetchVideoDetails(videoId, new VideoRepository.VideoDetailsCallback() {
            @Override
            public void onSuccess(VideoModel video) {
                _currentVideoDetails.postValue(video);
            }

            @Override
            public void onError(String message) {
                _currentVideoDetails.postValue(null);
            }
        });
    }

    public void setFilter(String filterValue) {
        if (Objects.equals(filter.getValue(), filterValue)) return;
        filter.setValue(filterValue);
        currentOffset = 0;
        hasMore = true;
        _trendingSongs.setValue(new ArrayList<>());
        loadFeed(filterValue, false);
    }

    public String getCurrentFilter() {
        return filter.getValue();
    }

    public void loadNextPage() {
        if (Boolean.TRUE.equals(_isLoadingFeed.getValue()) || Boolean.TRUE.equals(_isLoadingMore.getValue()) || !hasMore)
            return;
        currentOffset += VideoRepository.PAGE_SIZE;
        loadFeed(filter.getValue(), true);
    }

    public void loadFeed(String filterValue, boolean isLoadMore) {
        if (isLoadMore) {
            if (Boolean.TRUE.equals(_isLoadingMore.getValue()) || !hasMore) return;
            _isLoadingMore.setValue(true);
        } else {
            _isLoadingFeed.setValue(true);
            currentOffset = 0;
            hasMore = true;
        }

        VideoRepository.PageCallback callback = new VideoRepository.PageCallback() {
            @Override
            public void onSuccess(List<VideoModel> videos, boolean more) {
                if (isLoadMore) _isLoadingMore.postValue(false);
                else _isLoadingFeed.postValue(false);

                hasMore = more;
                List<VideoModel> currentList = _trendingSongs.getValue();

                if (currentList == null || !isLoadMore) {
                    currentList = new ArrayList<>(videos);
                } else {
                    currentList = new ArrayList<>(currentList);
                    // Prevent duplicates
                    for (VideoModel v : videos) {
                        if (!currentList.contains(v)) {
                            currentList.add(v);
                        }
                    }
                }

                _trendingSongs.postValue(currentList);
                if (!isLoadMore && videos.isEmpty()) {
                    _trendingError.postValue("empty_feed");
                } else {
                    _trendingError.postValue(null);
                }
            }

            @Override
            public void onError(String message) {
                if (isLoadMore) _isLoadingMore.postValue(false);
                else _isLoadingFeed.postValue(false);
                _trendingError.postValue(message);
            }
        };

        if ("Trending".equals(filterValue)) {
            // Hits the trending_songs VIEW — sorted by play_count desc, last 7 days
            repository.fetchTrendingSongs(currentOffset, callback);
        } else if ("Popular".equals(filterValue)) {
            // All-time most played — order by play_count desc from trending_songs view
            repository.fetchTrendingByPopularity(currentOffset, callback);
        } else if ("New Releases".equals(filterValue)) {
            // Songs ordered by their actual YouTube published_at date
            repository.fetchOrderedSongs("published_at.desc", currentOffset, callback);
        } else {
            // Artist-name filter chips: Bruno Mars, Taylor Swift, BINI, etc.
            repository.fetchArtistSongs(filterValue, currentOffset, callback);
        }
    }

    public void loadTrendingSongs() {
        currentOffset = 0;
        loadFeed("Trending", false);
    }

    public void setDominantColor(Integer color) {
        _dominantColor.setValue(color != null ? color : 0);
    }

    public void setFullscreen(boolean fullscreen) {
        _isFullscreen.setValue(fullscreen);
    }

    public void runSearch(String query) {
        searchQuery.setValue(query);
    }

    public void loadRelatedSongs(String artist, String currentVideoId) {
        if (artist == null || artist.isEmpty()) return;
        if (currentVideoId != null) sessionHistory.add(currentVideoId);

        _isLoadingRelated.setValue(true);
        repository.fetchRelatedSongs(artist, new ArrayList<>(sessionHistory), new VideoRepository.PageCallback() {
            @Override
            public void onSuccess(List<VideoModel> videos, boolean hasMore) {
                _isLoadingRelated.postValue(false);
                _relatedSongs.postValue(videos);
            }

            @Override
            public void onError(String message) {
                _isLoadingRelated.postValue(false);
                _relatedError.postValue(message);
            }
        });
    }

    public void recordPlay(String videoId, VideoRepository.RecordPlayCallback callback) {
        if (videoId != null && !videoId.isEmpty()) {
            repository.recordSongPlay(videoId, callback);
        } else {
            callback.onProcessed();
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cancelActive();
        repository.cancelArtistSearch();
    }
}