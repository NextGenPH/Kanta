package com.sns.kanta.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sns.kanta.R;
import com.sns.kanta.model.ArtistModel;

import java.util.ArrayList;
import java.util.List;

public final class PopularArtistsAdapter extends RecyclerView.Adapter<PopularArtistsAdapter.ViewHolder> {

    private final List<ArtistModel> items = new ArrayList<>();
    private final OnArtistClickListener listener;

    public PopularArtistsAdapter(OnArtistClickListener listener) {
        this.listener = listener;
    }

    public void setArtists(List<ArtistModel> newArtists) {
        final List<ArtistModel> oldArtists = new ArrayList<>(items);
        items.clear();
        if (newArtists != null) items.addAll(newArtists);

        androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldArtists.size();
            }

            @Override
            public int getNewListSize() {
                return items.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldArtists.get(oldItemPosition).getName().equals(items.get(newItemPosition).getName());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ArtistModel oldItem = oldArtists.get(oldItemPosition);
                ArtistModel newItem = items.get(newItemPosition);
                return oldItem.getSongCount() == newItem.getSongCount() && oldItem.getTotalPlays() == newItem.getTotalPlays();
            }
        }).dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_popular_artist, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArtistModel artist = items.get(position);
        holder.tvName.setText(artist.getName());
        holder.tvSongs.setText(artist.getSongCount() + " songs");

        holder.itemView.setOnClickListener(v -> listener.onArtistClick(artist.getName()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnArtistClickListener {
        void onArtistClick(String artistName);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvSongs;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvArtistName);
            tvSongs = itemView.findViewById(R.id.tvSongCount);
        }
    }
}
