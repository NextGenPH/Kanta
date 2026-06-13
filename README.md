# Kanta

Kanta is an Android music and video player application designed for discovering, queuing, and enjoying songs, with a strong focus on karaoke and seamless YouTube integration.

## Description

Kanta allows users to search for songs, add them to a playback queue, mark them as favorites, and discover related music. It leverages YouTube as the primary source for video playback, offering features like:

*   **YouTube Video Playback:** Stream music and videos directly from YouTube.
*   **Music Queue Management:** Add songs to a queue, play them in order, and see what's coming up next.
*   **Favorites:** Save your favorite songs for quick access.
*   **Song Discovery:** Find related songs based on the artist or channel of the currently playing track.
*   **Search Functionality:** Search for songs using text input or voice commands.
*   **Playback Resumption:** Automatically saves and allows resuming your last played song, including the playback position.
*   **Fullscreen Mode:** Enjoy an immersive viewing experience with fullscreen playback.

The application ensures a smooth user experience by checking for network connectivity during startup and providing a dedicated splash screen.

## Features

*   **YouTube Integration:** Seamlessly plays videos from YouTube.
*   **Queueing System:** Add, manage, and reorder songs in your personal playback queue.
*   **Favorites Management:** Keep track of your most-loved tracks.
*   **Related Song Recommendations:** Discover more music from your favorite artists.
*   **Advanced Search:** Find songs via text or voice input.
*   **Persistent Playback State:** Resumes your last session automatically.
*   **Interactive UI:** Features a bottom navigation bar, song progress display, and interactive controls.
*   **Permissions:** Requires microphone access for voice search and internet access for streaming and search.

## Setup

Kanta is an Android application. To build and run it:

1.  Clone this repository.
2.  Open the project in Android Studio.
3.  Ensure you have a stable internet connection.
4.  Build the project and run it on an emulator or a physical Android device.

## Usage

1.  **Launch:** Upon launching the app, a splash screen will appear while it checks for internet connectivity and performs initial setup.
2.  **Main Screen:** The main screen displays the currently playing song, playback controls, a list of upcoming songs in the queue, and related song suggestions.
3.  **Navigation:** Use the bottom navigation bar to access different sections:
    *   **Home:** Returns to the main player view.
    *   **Queue:** View and manage your song queue.
    *   **Search:** Find new songs.
    *   **Favorites:** Access your saved favorite songs.
    *   **Settings/Help:** View app information and help resources.
4.  **Playing Music:**
    *   Search for a song, tap on it to add it to the queue.
    *   The first song in the queue will start playing automatically.
    *   Use controls to play, pause, stop, or skip to the next song.
    *   Tap the fullscreen icon to enter/exit fullscreen playback.
5.  **Voice Search:** Tap the microphone icon in the search dialog to use voice commands for finding songs.

## Permissions

The application requests the following permissions:
*   `RECORD_AUDIO`: For voice search functionality.
*   `INTERNET`: For streaming music, searching for songs, and fetching related content.

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request for any features, bug fixes, or improvements.

## License

(To be added)