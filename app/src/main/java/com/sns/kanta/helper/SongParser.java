package com.sns.kanta.helper;

import androidx.annotation.NonNull;

/**
 * Utility for parsing artist information from song titles.
 */
public final class SongParser {

    /**
     * Extracts the artist name from a song title string.
     * Expects standard formats like "Artist - Title", "Artist – Title", "Artist | Title", etc.
     *
     * @param title The raw song title.
     * @return The extracted artist name, or an empty string if no separator is found.
     */
    @NonNull
    public static String extractArtist(String title) {
        if (title == null || title.isEmpty()) return "";

        // Common separators used in YouTube titles
        String[] separators = {" - ", " – ", " — ", " | ", " : "};

        for (String sep : separators) {
            if (title.contains(sep)) {
                String artist = title.split(sep)[0].trim();

                // Further clean up if the artist part has "feat." or similar
                // Example: "Artist ft. OtherArtist" -> "Artist"
                artist = artist.split("(?i)\\s+(ft\\.|feat\\.?)\\s+")[0].trim();

                return artist;
            }
        }

        return "";
    }
}
