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
import com.sns.kanta.helper.TextFormatter;
import com.sns.kanta.model.VideoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {

    private final Context context;
    private final OnFavoriteClickListener clickListener;
    private final OnFavoriteLongClickListener longClickListener;
    private final TextFormatter formatter = TextFormatter.getInstance();
    private List<VideoModel> favorites = new ArrayList<>();

    public FavoritesAdapter(@NonNull Context context,
                            @NonNull OnFavoriteClickListener clickListener,
                            @NonNull OnFavoriteLongClickListener longClickListener) {
        this.context = context;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setFavorites(@NonNull List<VideoModel> newFavorites) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return favorites.size();
            }

            @Override
            public int getNewListSize() {
                return newFavorites.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return Objects.equals(favorites.get(oldPos).getVideoId(), newFavorites.get(newPos).getVideoId());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return Objects.equals(favorites.get(oldPos), newFavorites.get(newPos));
            }
        });
        this.favorites = new ArrayList<>(newFavorites);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_related_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel video = favorites.get(position);
        holder.title.setText(formatter.formatSongTitle(video.getTitle()));
        holder.artist.setText(video.getArtistOrChannel());

        Glide.with(context)
                .load(video.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .error(R.drawable.ic_thumbnail_placeholder)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> clickListener.onFavoriteClick(video));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onFavoriteLongClick(video);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return favorites.size();
    }

    public interface OnFavoriteClickListener {
        void onFavoriteClick(VideoModel video);
    }

    public interface OnFavoriteLongClickListener {
        void onFavoriteLongClick(VideoModel video);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title;
        TextView artist;

        ViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            artist = itemView.findViewById(R.id.artist);
        }
    }
}
