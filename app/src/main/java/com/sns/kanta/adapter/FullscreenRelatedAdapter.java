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

public final class FullscreenRelatedAdapter extends RecyclerView.Adapter<FullscreenRelatedAdapter.ViewHolder> {

    private final Context context;
    private final OnSongClickListener clickListener;

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

    public FullscreenRelatedAdapter(@NonNull Context context, @NonNull OnSongClickListener listener) {
        this.context = context;
        this.clickListener = listener;
    }

    public void setSongs(@NonNull List<VideoModel> songs) {
        differ.submitList(songs);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_fullscreen_song, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel video = differ.getCurrentList().get(position);
        holder.title.setText(video.getTitle());

        Glide.with(context)
                .load(video.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> clickListener.onSongClick(video));
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    public interface OnSongClickListener {
        void onSongClick(VideoModel video);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
        }
    }
}
