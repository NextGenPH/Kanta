package com.sns.kanta.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sns.kanta.R;
import com.sns.kanta.model.VideoModel;

import java.util.List;
import java.util.Objects;

public class HomeFeedAdapter extends RecyclerView.Adapter<HomeFeedAdapter.ViewHolder> {

    private final Context context;
    private final OnSongClickListener listener;

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

    public HomeFeedAdapter(Context context, OnSongClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setSongs(List<VideoModel> newSongs) {
        differ.submitList(newSongs);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_video_feed, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel song = differ.getCurrentList().get(position);
        holder.tvTitle.setText(song.getTitle());
        holder.tvChannelName.setText(song.getArtistOrChannel());

        Long playCount = song.getPlayCount();
        if (playCount != null && playCount > 0) {
            holder.tvPlayCount.setText(formatPlayCount(playCount) + " plays");
            holder.ivPlayCount.setVisibility(View.VISIBLE);
            holder.tvPlayCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvPlayCount.setText("0 plays");
            holder.ivPlayCount.setVisibility(View.VISIBLE);
            holder.tvPlayCount.setVisibility(View.VISIBLE);
        }

        String rawDate = song.getPublishedAt();
        if (rawDate == null || rawDate.isEmpty()) {
            rawDate = song.getCreatedAt();
        }
        if (rawDate != null && !rawDate.isEmpty()) {
            holder.tvPublishedDate.setText(com.sns.kanta.helper.TimeUtils.getRelativeTime(rawDate));
            holder.ivCalendar.setVisibility(View.VISIBLE);
            holder.tvPublishedDate.setVisibility(View.VISIBLE);
        } else {
            holder.ivCalendar.setVisibility(View.GONE);
            holder.tvPublishedDate.setVisibility(View.GONE);
        }

        Glide.with(context)
                .load(song.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .centerCrop()
                .into(holder.ivThumbnail);

        // Use artist/channel initials for avatar if no specific avatar url
        holder.ivChannelAvatar.setImageResource(R.drawable.ic_profile);
        holder.ivChannelAvatar.setImageTintList(android.content.res.ColorStateList.valueOf(
                context.getResources().getColor(R.color.text_secondary, context.getTheme())
        ));

        holder.itemView.setOnClickListener(v -> listener.onSongClick(song));
        if (holder.btnMenu != null) {
            holder.btnMenu.setOnClickListener(v -> com.sns.kanta.helper.MenuUtils.showMediaItemMenu(context, v, song));
        }
    }

    private String formatPlayCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format(java.util.Locale.US, "%.1fK", count / 1000.0);
        return String.format(java.util.Locale.US, "%.1fM", count / 1000000.0);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    public interface OnSongClickListener {
        void onSongClick(VideoModel video);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        ImageView ivChannelAvatar;
        TextView tvTitle;
        TextView tvChannelName;
        TextView tvPlayCount;
        TextView tvPublishedDate;
        View ivPlayCount;
        View ivCalendar;
        View btnMenu;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            ivChannelAvatar = itemView.findViewById(R.id.ivChannelAvatar);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvChannelName = itemView.findViewById(R.id.tvChannelName);
            tvPlayCount = itemView.findViewById(R.id.tvPlayCount);
            tvPublishedDate = itemView.findViewById(R.id.tvPublishedDate);
            ivPlayCount = itemView.findViewById(R.id.ivPlayCount);
            ivCalendar = itemView.findViewById(R.id.ivCalendar);
            btnMenu = itemView.findViewById(R.id.btnMenu);
        }
    }
}