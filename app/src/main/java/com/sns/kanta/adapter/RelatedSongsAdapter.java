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

public final class RelatedSongsAdapter extends RecyclerView.Adapter<RelatedSongsAdapter.ViewHolder> {

    private final Context context;
    private final OnAddClickListener addListener;
    private final OnFavoriteLongClickListener favoriteLongClickListener;
    private List<VideoModel> songs = new ArrayList<>();

    public RelatedSongsAdapter(@NonNull Context context,
                               @NonNull OnAddClickListener addListener,
                               @NonNull OnFavoriteLongClickListener favoriteListener) {
        this.context = context;
        this.addListener = addListener;
        this.favoriteLongClickListener = favoriteListener;
    }

    public void setSongs(@NonNull List<VideoModel> newSongs) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return songs.size();
            }

            @Override
            public int getNewListSize() {
                return newSongs.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return Objects.equals(songs.get(oldPos).getVideoId(), newSongs.get(newPos).getVideoId());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return Objects.equals(songs.get(oldPos).getVideoId(), newSongs.get(newPos).getVideoId());
            }
        });
        songs = new ArrayList<>(newSongs);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_related_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel video = songs.get(position);
        holder.title.setText(video.getTitle());
        holder.artist.setText(video.getArtistOrChannel());

        Glide.with(context)
                .load(video.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .error(R.drawable.ic_thumbnail_placeholder)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> addListener.onAddClick(video));
        holder.itemView.setOnLongClickListener(v -> {
            favoriteLongClickListener.onFavoriteLongClick(video);
            return true;
        });
    }

    public interface OnAddClickListener {
        void onAddClick(@NonNull VideoModel video);
    }

    public interface OnFavoriteLongClickListener {
        void onFavoriteLongClick(VideoModel video);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView artist;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            artist = itemView.findViewById(R.id.artist);
        }
    }
}
