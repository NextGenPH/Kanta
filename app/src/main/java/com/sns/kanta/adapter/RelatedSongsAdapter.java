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

public final class RelatedSongsAdapter extends RecyclerView.Adapter<RelatedSongsAdapter.ViewHolder> {

    private final Context context;
    private final OnAddClickListener addListener;

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

    public RelatedSongsAdapter(@NonNull Context context,
                               @NonNull OnAddClickListener addListener) {
        this.context = context;
        this.addListener = addListener;
    }

    public void setSongs(@NonNull List<VideoModel> newSongs) {
        differ.submitList(newSongs);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_related_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel video = differ.getCurrentList().get(position);
        holder.title.setText(video.getTitle());
        holder.tvChannelName.setText(video.getArtistOrChannel());

        Long playCount = video.getPlayCount();
        if (playCount != null && playCount > 0) {
            holder.tvPlayCount.setText(formatPlayCount(playCount) + " plays");
            holder.ivPlayCount.setVisibility(View.VISIBLE);
            holder.tvPlayCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvPlayCount.setText("0 plays");
            holder.ivPlayCount.setVisibility(View.VISIBLE);
            holder.tvPlayCount.setVisibility(View.VISIBLE);
        }

        String rawDate = video.getPublishedAt();
        if (rawDate == null || rawDate.isEmpty()) {
            rawDate = video.getCreatedAt();
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
                .load(video.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .error(R.drawable.ic_thumbnail_placeholder)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> addListener.onAddClick(video));
        if (holder.btnMenu != null) {
            holder.btnMenu.setOnClickListener(v -> com.sns.kanta.helper.MenuUtils.showMediaItemMenu(context, v, video));
        }
    }

    private String formatPlayCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format(java.util.Locale.US, "%.1fK", count / 1000.0);
        return String.format(java.util.Locale.US, "%.1fM", count / 1000000.0);
    }

    public interface OnAddClickListener {
        void onAddClick(@NonNull VideoModel video);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView tvChannelName;
        final TextView tvPlayCount;
        final TextView tvPublishedDate;
        final View ivPlayCount;
        final View ivCalendar;
        final View btnMenu;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            tvChannelName = itemView.findViewById(R.id.tvChannelName);
            tvPlayCount = itemView.findViewById(R.id.tvPlayCount);
            tvPublishedDate = itemView.findViewById(R.id.tvPublishedDate);
            ivPlayCount = itemView.findViewById(R.id.ivPlayCount);
            ivCalendar = itemView.findViewById(R.id.ivCalendar);
            btnMenu = itemView.findViewById(R.id.btnMenu);
        }
    }
}
