package com.sns.kanta.helper;

import android.text.format.DateUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {
    private static final String TAG = "TimeUtils";

    public static String getRelativeTime(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) return "just now";

        try {
            // Standard Supabase ISO 8601 format: 2023-06-23T11:46:16.219+00:00 or 2023-06-23T11:46:16.219Z
            // Or simple date: 2018-10-15
            String cleanTimestamp = isoTimestamp.replace("Z", "+00:00");

            SimpleDateFormat sdf;
            if (cleanTimestamp.contains("T")) {
                if (cleanTimestamp.contains(".")) {
                    cleanTimestamp = cleanTimestamp.substring(0, cleanTimestamp.lastIndexOf("."));
                }
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            } else {
                sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            }

            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(cleanTimestamp);

            if (date != null) {
                long now = System.currentTimeMillis();
                long diff = now - date.getTime();

                if (diff < 0) {
                    return "just now";
                }

                long seconds = diff / 1000;
                long minutes = seconds / 60;
                long hours = minutes / 60;
                long days = hours / 24;
                long weeks = days / 7;
                long months = days / 30;
                long years = days / 365;

                if (years > 0) {
                    return years == 1 ? "1 year ago" : years + " years ago";
                } else if (months > 0) {
                    return months == 1 ? "1 month ago" : months + " months ago";
                } else if (weeks > 0) {
                    return weeks == 1 ? "1 week ago" : weeks + " weeks ago";
                } else if (days > 0) {
                    return days == 1 ? "1 day ago" : days + " days ago";
                } else if (hours > 0) {
                    return hours == 1 ? "1 hour ago" : hours + " hours ago";
                } else if (minutes > 0) {
                    return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
                } else {
                    return "just now";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + isoTimestamp, e);
        }
        return "just now";
    }

    public static String formatDate(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) return "";
        try {
            String cleanTimestamp = isoTimestamp.replace("Z", "+00:00");
            SimpleDateFormat sdf;
            if (cleanTimestamp.contains("T")) {
                if (cleanTimestamp.contains(".")) {
                    cleanTimestamp = cleanTimestamp.substring(0, cleanTimestamp.lastIndexOf("."));
                }
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            } else {
                sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            }
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(cleanTimestamp);
            if (date != null) {
                SimpleDateFormat outFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                return outFormat.format(date);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting date: " + isoTimestamp, e);
        }
        return isoTimestamp;
    }
}
