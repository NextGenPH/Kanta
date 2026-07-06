package com.sns.kanta.helper;

import java.util.regex.Pattern;

/**
 * Utility for cleaning up and formatting song titles.
 * Removes common YouTube suffixes like "(Official Video)", "[Lyrics]", etc.
 */
public final class TextFormatter {

    private static final Pattern PATTERN_OFFICIAL = Pattern.compile("(?i)\\s*[\\[(]official\\s*(video|audio|music\\s*video|lyric\\s*video)?[])]");
    private static final Pattern PATTERN_LYRIC_VIDEO = Pattern.compile("(?i)\\s*[\\[(]lyric\\s*video[])]");
    private static final Pattern PATTERN_LYRICS = Pattern.compile("(?i)\\s*[\\[(]lyrics?[])]");
    private static final Pattern PATTERN_HD = Pattern.compile("(?i)\\s*[\\[(]hd[])]");
    private static final Pattern PATTERN_4K = Pattern.compile("(?i)\\s*[\\[(]4k[])]");
    private static final Pattern PATTERN_HQ = Pattern.compile("(?i)\\s*[\\[(]high\\s*quality[])]");
    private static final Pattern PATTERN_RESOLUTION = Pattern.compile("(?i)\\s*[\\[(](?:720p|1080p)[])]");
    private static final Pattern PATTERN_OFFICIAL_NO_BRACKET = Pattern.compile("(?i)\\s*\\|?\\s*official\\s*(video|audio|music\\s*video)$");
    private static final Pattern PATTERN_MUSIC_VIDEO_NO_BRACKET = Pattern.compile("(?i)\\s*music\\s*video$");
    private static final Pattern PATTERN_WHITESPACE = Pattern.compile("\\s{2,}");

    private TextFormatter() {
        // Prevent instantiation
    }

    /**
     * Cleans up a song title by removing common YouTube metadata suffixes.
     * Example: "Song Title (Official Music Video)" -> "Song Title"
     */
    public static String formatSongTitle(String title) {
        if (title == null || title.isEmpty()) return "";

        String cleaned = title;

        // 1. Remove common bracketed/parenthesized suffixes (case-insensitive)
        cleaned = PATTERN_OFFICIAL.matcher(cleaned).replaceAll("");
        cleaned = PATTERN_LYRIC_VIDEO.matcher(cleaned).replaceAll("");
        cleaned = PATTERN_LYRICS.matcher(cleaned).replaceAll("");
        cleaned = PATTERN_HD.matcher(cleaned).replaceAll("");
        cleaned = PATTERN_4K.matcher(cleaned).replaceAll("");
        cleaned = PATTERN_HQ.matcher(cleaned).replaceAll("");
        cleaned = PATTERN_RESOLUTION.matcher(cleaned).replaceAll("");

        // 2. Remove trailing separators and "Official Video" without brackets
        cleaned = PATTERN_OFFICIAL_NO_BRACKET.matcher(cleaned).replaceAll("");
        cleaned = PATTERN_MUSIC_VIDEO_NO_BRACKET.matcher(cleaned).replaceAll("");

        // 3. Normalize whitespace
        cleaned = PATTERN_WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        return cleaned;
    }
}
