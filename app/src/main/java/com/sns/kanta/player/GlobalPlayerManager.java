package com.sns.kanta.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.sns.kanta.model.ReservationModel;

/**
 * Singleton to manage global playback state across activities.
 */
public final class GlobalPlayerManager {

    private static GlobalPlayerManager instance;

    private final MutableLiveData<ReservationModel> _currentSong = new MutableLiveData<>();
    public final LiveData<ReservationModel> currentSong = _currentSong;

    private final MutableLiveData<PlayerConstants.PlayerState> _playerState = new MutableLiveData<>(PlayerConstants.PlayerState.UNKNOWN);
    public final LiveData<PlayerConstants.PlayerState> playerState = _playerState;

    private final MutableLiveData<Float> _currentTime = new MutableLiveData<>(0f);
    public final LiveData<Float> currentTime = _currentTime;

    private final MutableLiveData<Float> _duration = new MutableLiveData<>(0f);
    public final LiveData<Float> duration = _duration;

    private final MutableLiveData<Boolean> _isMinimized = new MutableLiveData<>(false);
    public final LiveData<Boolean> isMinimized = _isMinimized;

    private GlobalPlayerManager() {
    }

    public static synchronized GlobalPlayerManager getInstance() {
        if (instance == null) instance = new GlobalPlayerManager();
        return instance;
    }

    public void updateSong(@Nullable ReservationModel song) {
        _currentSong.setValue(song);
        if (song == null) {
            _playerState.setValue(PlayerConstants.PlayerState.UNKNOWN);
            _currentTime.setValue(0f);
            _duration.setValue(0f);
        }
    }

    public void updateState(@NonNull PlayerConstants.PlayerState state) {
        _playerState.setValue(state);
    }

    public void updateTime(float time) {
        _currentTime.setValue(time);
    }

    public void updateDuration(float duration) {
        _duration.setValue(duration);
    }

    public void setMinimized(boolean minimized) {
        _isMinimized.setValue(minimized);
    }

    public void stop() {
        updateSong(null);
    }

    public boolean isPlaying() {
        return _currentSong.getValue() != null;
    }
}
