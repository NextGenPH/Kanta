package com.sns.kanta.helper;

import androidx.annotation.NonNull;

/**
 * Parses karaoke song titles into artist and song name components.
 * <p>
 * Supported formats:
 * "Song Name - Artist (HD Karaoke)"
 * "Song Name - Artist (KARAOKE VERSION)"
 * "Song Name - Artist (Karaoke Version)"
 * "Song Name - Artist feat. Other | Karaoke"
 * "Song Name - Artist/FEMALEKEY"
 * "Song Name - Artist"
 */
public final class SongParser {

    // All known suffixes to strip before parsing
    private static final String[] SUFFIXES = {
            " (HD Karaoke)",
            " (KARAOKE VERSION)",
            " (Karaoke Version)",
            " (Karaoke)",
            " (karaoke)",
            " | Karaoke",
            " - Karaoke",
            " [Karaoke]",
            " [HD Karaoke]",
    };

    private SongParser() {
    }

    /**
     * Extract the artist from a karaoke title.
     * Returns empty string if no artist separator found.
     */
    @NonNull
    public static String extractArtist(@NonNull String title) {
        String cleaned = stripSuffixes(title);
        int dashIndex = cleaned.indexOf(" - ");
        if (dashIndex < 0) return "";

        // Artist is the part AFTER the first " - "
        String artist = cleaned.substring(dashIndex + 3).trim();

        // Strip feat./ft. collaborators — they're noise for related matching
        artist = stripFeaturing(artist);

        // Take only the primary artist when "/" separates keys/versions
        int slashIndex = artist.indexOf('/');
        if (slashIndex > 0) {
            artist = artist.substring(0, slashIndex).trim();
        }

        return artist;
    }

    /**
     * Extract the song name (part before the first " - ").
     * Falls back to the full cleaned title if no separator found.
     */
    @NonNull
    public static String extractSongName(@NonNull String title) {
        String cleaned = stripSuffixes(title);
        int dashIndex = cleaned.indexOf(" - ");
        if (dashIndex < 0) return cleaned.trim();
        return cleaned.substring(0, dashIndex).trim();
    }

    /**
     * Strip karaoke suffixes and return a clean title for display.
     */
    @NonNull
    public static String cleanTitle(@NonNull String title) {
        return stripSuffixes(title).trim();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @NonNull
    private static String stripSuffixes(@NonNull String title) {
        String result = title;
        // Case-insensitive suffix stripping
        for (String suffix : SUFFIXES) {
            int idx = result.toLowerCase().lastIndexOf(suffix.toLowerCase());
            if (idx >= 0) {
                result = result.substring(0, idx);
            }
        }
        return result;
    }

    @NonNull
    private static String stripFeaturing(@NonNull String artist) {
        // "Artist feat. Other" → "Artist"
        // "Artist ft. Other"   → "Artist"
        // "Artist (feat. Other)" → "Artist"
        return artist
                .replaceAll("(?i)\\s*\\(feat\\..*?\\)", "")
                .replaceAll("(?i)\\s+feat\\..*$", "")
                .replaceAll("(?i)\\s+ft\\..*$", "")
                .trim();
    }
}