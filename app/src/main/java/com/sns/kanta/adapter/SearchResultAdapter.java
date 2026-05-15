package com.sns.kanta.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sns.kanta.R;
import com.sns.kanta.model.VideoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private final Context context;
    private final OnPlayClickListener playClickListener;
    private final OnFavoriteLongClickListener favoriteLongClickListener;

    private List<VideoModel> videoList;

    public SearchResultAdapter(@NonNull Context context,
                               @NonNull OnPlayClickListener playListener,
                               @NonNull OnFavoriteLongClickListener favoriteListener) {
        this.context = context;
        this.videoList = new ArrayList<>();
        this.playClickListener = playListener;
        this.favoriteLongClickListener = favoriteListener;
    }

    public void updateResults(@NonNull List<VideoModel> newVideos) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return videoList.size();
            }

            @Override
            public int getNewListSize() {
                return newVideos.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return Objects.equals(videoList.get(oldPos).getVideoId(), newVideos.get(newPos).getVideoId());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return Objects.equals(videoList.get(oldPos).getVideoId(), newVideos.get(newPos).getVideoId());
            }
        });
        this.videoList = new ArrayList<>(newVideos);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel video = videoList.get(position);
        holder.title.setText(video.getTitle());
        holder.channel.setText(video.getChannel());

        Glide.with(context)
                .load(video.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .error(R.drawable.ic_thumbnail_placeholder)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> playClickListener.onPlayClick(video));

        holder.itemView.setOnLongClickListener(v -> {
            favoriteLongClickListener.onFavoriteLongClick(video);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public interface OnPlayClickListener {
        void onPlayClick(VideoModel video);
    }

    public interface OnFavoriteLongClickListener {
        void onFavoriteLongClick(VideoModel video);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title;
        TextView channel;

        ViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            channel = itemView.findViewById(R.id.channel);
        }
    }
}
