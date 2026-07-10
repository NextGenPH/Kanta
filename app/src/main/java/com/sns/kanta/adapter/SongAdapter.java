package com.sns.kanta.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.card.MaterialCardView;
import com.sns.kanta.R;
import com.sns.kanta.model.VideoModel;

import java.util.List;
import java.util.Objects;

public final class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private final Context context;
    private final Style style;
    private final OnSongClickListener clickListener;
    private final OnMenuClickListener menuClickListener;
    private final AsyncListDiffer<VideoModel> differ = new AsyncListDiffer<>(this, new DiffUtil.ItemCallback<VideoModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull VideoModel oldItem, @NonNull VideoModel newItem) {
            return Objects.equals(oldItem.getVideoId(), newItem.getVideoId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull VideoModel oldItem, @NonNull VideoModel newItem) {
            return Objects.equals(oldItem, newItem);
        }
    });

    public SongAdapter(@NonNull Context context,
                       @NonNull Style style,
                       @NonNull OnSongClickListener clickListener,
                       @Nullable OnMenuClickListener menuClickListener) {
        this.context = context;
        this.style = style;
        this.clickListener = clickListener;
        this.menuClickListener = menuClickListener;
    }

    public void setSongs(@NonNull List<VideoModel> songs) {
        differ.submitList(songs);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        switch (style) {
            case VERTICAL_FEED:
                layoutId = R.layout.item_song_feed;
                break;
            case HORIZONTAL_LIST:
                layoutId = R.layout.item_song_list;
                break;
            case PLAY_LATER_CARD:
                layoutId = R.layout.item_song_play_later;
                break;
            case FULLSCREEN_CARD:
                layoutId = R.layout.item_song_fullscreen;
                break;
            default:
                layoutId = R.layout.item_song_feed;
                break;
        }
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel song = differ.getCurrentList().get(position);
        holder.title.setText(song.getTitle());

        if (holder.tvChannelName != null) {
            holder.tvChannelName.setText(song.getArtistOrChannel());
        }

        if (style != Style.FULLSCREEN_CARD && style != Style.PLAY_LATER_CARD) {
            Long playCount = song.getPlayCount();
            if (holder.tvPlayCount != null) {
                if (playCount != null && playCount > 0) {
                    holder.tvPlayCount.setText(context.getString(R.string.plays_count_format, formatPlayCount(playCount)));
                } else {
                    holder.tvPlayCount.setText(context.getString(R.string.zero_plays));
                }
            }

            String rawDate = song.getPublishedAt();
            if (rawDate == null || rawDate.isEmpty()) {
                rawDate = song.getCreatedAt();
            }

            boolean hasDate = rawDate != null && !rawDate.isEmpty();
            if (hasDate && holder.tvPublishedDate != null) {
                holder.tvPublishedDate.setText(com.sns.kanta.helper.TimeUtils.getRelativeTime(rawDate));
                holder.tvPublishedDate.setVisibility(View.VISIBLE);
                if (holder.ivCalendar != null) holder.ivCalendar.setVisibility(View.VISIBLE);
            } else {
                if (holder.tvPublishedDate != null) holder.tvPublishedDate.setVisibility(View.GONE);
                if (holder.ivCalendar != null) holder.ivCalendar.setVisibility(View.GONE);
            }
        }

        Glide.with(context)
                .load(song.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .error(R.drawable.ic_thumbnail_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> clickListener.onSongClick(song));

        if (holder.btnMenu != null) {
            holder.btnMenu.setOnClickListener(v -> {
                if (menuClickListener != null) {
                    menuClickListener.onMenuClick(v, song);
                } else {
                    com.sns.kanta.helper.MenuUtils.showMediaItemMenu(context, v, song);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    private String formatPlayCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format(java.util.Locale.US, "%.1fK", count / 1000.0);
        return String.format(java.util.Locale.US, "%.1fM", count / 1000000.0);
    }

    public enum Style {
        VERTICAL_FEED,     // Large card (Home feed, related songs list)
        HORIZONTAL_LIST,   // Horizontal list item (Search results list)
        PLAY_LATER_CARD,   // Small horizontal-scroll card (160dp)
        FULLSCREEN_CARD    // Dark horizontal-scroll card (140dp)
    }

    public interface OnSongClickListener {
        void onSongClick(VideoModel video);
    }

    public interface OnMenuClickListener {
        void onMenuClick(View anchorView, VideoModel video);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardRoot;
        final ConstraintLayout contentLayout;
        final ImageView thumbnail;
        final TextView title;
        final TextView tvChannelName;
        final TextView tvPlayCount;
        final TextView tvPublishedDate;
        final View ivPlayCount;
        final View ivCalendar;
        final View btnMenu;
        final View metaLayout;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            contentLayout = itemView.findViewById(R.id.contentLayout);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            tvChannelName = itemView.findViewById(R.id.tvChannelName);
            tvPlayCount = itemView.findViewById(R.id.tvPlayCount);
            tvPublishedDate = itemView.findViewById(R.id.tvPublishedDate);
            ivPlayCount = itemView.findViewById(R.id.ivPlayCount);
            ivCalendar = itemView.findViewById(R.id.ivCalendar);
            btnMenu = itemView.findViewById(R.id.btnMenu);
            metaLayout = itemView.findViewById(R.id.metaLayout);
        }
    }
}
