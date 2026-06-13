package com.sns.kanta.helper;

/**
 * Singleton utility for cleaning up and formatting song titles.
 * Removes common YouTube suffixes like "(Official Video)", "[Lyrics]", etc.
 */
public final class TextFormatter {

    private static volatile TextFormatter instance;

    private TextFormatter() {
    }

    public static TextFormatter getInstance() {
        if (instance == null) {
            synchronized (TextFormatter.class) {
                if (instance == null) {
                    instance = new TextFormatter();
                }
            }
        }
        return instance;
    }

    /**
     * Cleans up a song title by removing common YouTube metadata suffixes.
     * Example: "Song Title (Official Music Video)" -> "Song Title"
     */
    public String formatSongTitle(String title) {
        if (title == null || title.isEmpty()) return "";

        String cleaned = title;

        // 1. Remove common bracketed/parenthesized suffixes (case-insensitive)
        cleaned = cleaned.replaceAll("(?i)\\s*[\\[(]official\\s*(video|audio|music\\s*video|lyric\\s*video)?[])]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*[\\[(]lyric\\s*video[])]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*[\\[(]lyrics?[])]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*[\\[(]hd[])]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*[\\[(]4k[])]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*[\\[(]high\\s*quality[])]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*[\\[(]720p|1080p[])]", "");

        // 2. Remove trailing separators and "Official Video" without brackets
        cleaned = cleaned.replaceAll("(?i)\\s*\\|?\\s*official\\s*(video|audio|music\\s*video)$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*music\\s*video$", "");

        // 3. Normalize whitespace
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();

        return cleaned;
    }
}
