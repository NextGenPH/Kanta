package com.sns.kanta.helper;

import java.util.Locale;

public class TextFormatter {

    private static TextFormatter instance;

    private TextFormatter() {
    }

    public static synchronized TextFormatter getInstance() {
        if (instance == null) {
            instance = new TextFormatter();
        }
        return instance;
    }

    /**
     * Remove music-related emojis including microphone
     */
    public String removeMusicEmojis(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("🎤", "")    // Microphone
                .replace("🎙️", "")   // Studio Microphone
                .replace("🎵", "")    // Musical Note
                .replace("🎶", "")    // Multiple Musical Notes
                .replace("🎧", "")    // Headphone
                .replace("📀", "")    // DVD
                .replace("🎼", "")    // Musical Score
                .replace("🎹", "")    // Musical Keyboard
                .replace("🎺", "")    // Trumpet
                .replace("🎸", "")    // Guitar
                .replace("🎷", "")    // Saxophone
                .trim();
    }

    /**
     * Remove KARAOKE-related text tags (case-insensitive)
     */
    public String removeKaraokeTags(String text) {
        if (text == null || text.isEmpty()) return "";

        // Case-insensitive removal using regex
        String result = text;

        // Remove [KARAOKE] variations
        result = result.replaceAll("(?i)\\[\\s*KARAOKE\\s*\\]", "");

        // Remove (HD KARAOKE) variations
        result = result.replaceAll("(?i)\\(\\s*HD\\s*KARAOKE\\s*\\)", "");

        // Remove (KARAOKE) variations
        result = result.replaceAll("(?i)\\(\\s*KARAOKE\\s*\\)", "");

        // Remove (KARAOKE VERSION) variations (case-insensitive)
        result = result.replaceAll("(?i)\\(\\s*KARAOKE\\s+VERSION\\s*\\)", "");

        // Remove (KARAOKE Version) variations
        result = result.replaceAll("(?i)\\(\\s*KARAOKE\\s+Version\\s*\\)", "");

        // Remove (Karaoke Version) variations
        result = result.replaceAll("(?i)\\(\\s*Karaoke\\s+Version\\s*\\)", "");

        // Remove (VERSION) variations
        result = result.replaceAll("(?i)\\(\\s*VERSION\\s*\\)", "");

        // Remove standalone KARAOKE word
        result = result.replaceAll("(?i)\\s+KARAOKE\\s+", " ");
        result = result.replaceAll("(?i)^KARAOKE\\s+", "");
        result = result.replaceAll("(?i)\\s+KARAOKE$", "");

        return result.trim();
    }

    /**
     * Clean text by removing emojis and karaoke tags
     */
    public String cleanText(String text) {
        if (text == null || text.isEmpty()) return "";
        String cleaned = removeMusicEmojis(text);
        cleaned = removeKaraokeTags(cleaned);
        return cleaned.trim();
    }

    /**
     * Convert text to sentence case
     */
    public String toSentenceCase(String text) {
        if (text == null || text.isEmpty()) return "";
        text = text.toLowerCase(Locale.getDefault());
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    /**
     * Format song title - removes emojis, karaoke tags, and HD Karaoke suffix
     */
    public String formatSongTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isEmpty()) return "";

        // Remove emojis first
        String noEmojis = removeMusicEmojis(rawTitle);

        // Remove all karaoke-related tags (case-insensitive)
        String cleaned = removeKaraokeTags(noEmojis);

        // Additional cleanup for any leftover patterns
        cleaned = cleaned.replace(" (HD Karaoke)", "")
                .replace("(HD Karaoke)", "")
                .replace(" HD Karaoke", "")
                .replace(" - HD Karaoke", "")
                .trim();

        // Handle songs with " - " separator
        if (cleaned.contains(" - ")) {
            String[] parts = cleaned.split(" - ");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) result.append(" - ");
                result.append(toSentenceCase(parts[i].trim()));
            }
            return result.toString();
        }

        // Handle songs with "–" separator
        if (cleaned.contains(" – ")) {
            String[] parts = cleaned.split(" – ");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) result.append(" – ");
                result.append(toSentenceCase(parts[i].trim()));
            }
            return result.toString();
        }

        return toSentenceCase(cleaned);
    }

    /**
     * Format channel name
     */
    public String formatChannelName(String channel) {
        if (channel == null || channel.isEmpty()) return "";
        String cleaned = removeMusicEmojis(channel);
        cleaned = removeKaraokeTags(cleaned);
        return toSentenceCase(cleaned);
    }
}