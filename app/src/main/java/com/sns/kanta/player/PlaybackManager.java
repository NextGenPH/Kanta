package com.sns.kanta.player;

import androidx.annotation.NonNull;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker;
import com.sns.kanta.model.ReservationModel;
import com.sns.kanta.queueing.QueueManager;

public final class PlaybackManager {

    private final QueueManager queueManager;
    private final PlaybackCallback callback;
    private final YouTubePlayerTracker playerTracker;
    private YouTubePlayer activePlayer;
    private AbstractYouTubePlayerListener activeListener;
    private ReservationModel pendingSong;
    private float pendingStartSeconds = 0f;
    private String currentVideoId = null;
    private boolean isPlayerReady = false;
    private boolean isPlayingFallback = false;
    private boolean isEndingSoonAlertShown = false;

    public PlaybackManager(@NonNull QueueManager queueManager, @NonNull PlaybackCallback callback) {
        this.queueManager = queueManager;
        this.callback = callback;
        this.playerTracker = new YouTubePlayerTracker();
    }

    public void attachPlayer(@NonNull YouTubePlayer player) {
        if (this.activePlayer == player && isPlayerReady) {
            // Already attached and ready
            return;
        }

        // Clean up listeners from old player if exists
        if (this.activePlayer != null) {
            try {
                this.activePlayer.removeListener(playerTracker);
                if (activeListener != null) {
                    this.activePlayer.removeListener(activeListener);
                }
            } catch (Exception ignored) {
            }
        }

        this.activePlayer = player;
        this.activePlayer.addListener(playerTracker);
        this.isPlayerReady = true;

        if (pendingSong != null) {
            loadSong(pendingSong, pendingStartSeconds);
            pendingSong = null;
            pendingStartSeconds = 0f;
        }

        callback.onPlayerReady();

        // Create and add new listener only once
        if (activeListener != null) {
            // In case we want to reuse it? Usually better to recreate if player changed
        }

        this.activeListener = new AbstractYouTubePlayerListener() {
            @Override
            public void onStateChange(@NonNull YouTubePlayer player, @NonNull PlayerConstants.PlayerState state) {
                if (isPlayerReady) {
                    callback.onStateChanged(state);
                }
            }

            @Override
            public void onCurrentSecond(@NonNull YouTubePlayer player, float second) {
                if (isPlayerReady) callback.onCurrentSecond(second);
            }

            @Override
            public void onVideoDuration(@NonNull YouTubePlayer player, float duration) {
                if (isPlayerReady) callback.onVideoDuration(duration);
            }
        };
        this.activePlayer.addListener(activeListener);
    }

    public void loadSong(@NonNull ReservationModel song) {
        loadSong(song, 0f);
    }

    public void loadSong(@NonNull ReservationModel song, float startSeconds) {
        if (!isPlayerReady) {
            pendingSong = song;
            pendingStartSeconds = startSeconds;
            return;
        }

        if (activePlayer != null) {
            // Prevent redundant loading if already playing/loaded the same video at 0
            if (song.getVideoId().equals(currentVideoId) && startSeconds == 0f) {
                activePlayer.play();
                callback.onSongLoaded(song);
                return;
            }

            currentVideoId = song.getVideoId();
            isPlayingFallback = false;
            isEndingSoonAlertShown = false;
            queueManager.add(song);
            activePlayer.loadVideo(song.getVideoId(), startSeconds);
            callback.onSongLoaded(song);
        }
    }

    public void playFallback(@NonNull String videoId) {
        if (activePlayer != null) {
            if (videoId.equals(currentVideoId)) {
                activePlayer.play();
                return;
            }
            currentVideoId = videoId;
            isPlayingFallback = true;
            isEndingSoonAlertShown = false;
            activePlayer.loadVideo(videoId, 0f);
        }
    }

    public void seekTo(float time) {
        if (activePlayer != null) {
            activePlayer.seekTo(time);
        }
    }

    public void pause() {
        if (activePlayer != null) activePlayer.pause();
    }

    public void play() {
        if (activePlayer != null) activePlayer.play();
    }

    public void playPrevious() {
        if (queueManager.moveToPrevious()) {
            ReservationModel prev = queueManager.getNowPlaying();
            if (prev != null) loadSong(prev);
        }
    }

    public float getCurrentSecond() {
        return playerTracker.getCurrentSecond();
    }

    public float getVideoDuration() {
        return playerTracker.getVideoDuration();
    }

    public boolean isPlayingFallback() {
        return isPlayingFallback;
    }

    public boolean isEndingSoonAlertShown() {
        return isEndingSoonAlertShown;
    }

    public void setEndingSoonAlertShown(boolean shown) {
        isEndingSoonAlertShown = shown;
    }

    public YouTubePlayer getActivePlayer() {
        return activePlayer;
    }

    public boolean isReady() {
        return isPlayerReady;
    }

    public PlayerConstants.PlayerState getPlayerState() {
        return playerTracker.getState();
    }

    public interface PlaybackCallback {
        void onPlayerReady();

        void onStateChanged(@NonNull PlayerConstants.PlayerState state);

        void onCurrentSecond(float second);

        void onVideoDuration(float duration);

        void onSongLoaded(@NonNull ReservationModel song);
    }
}
