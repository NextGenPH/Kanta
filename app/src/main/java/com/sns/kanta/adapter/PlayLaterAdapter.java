package com.sns.kanta.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sns.kanta.R;
import com.sns.kanta.model.VideoModel;

import java.util.Objects;

public final class PlayLaterAdapter extends ListAdapter<VideoModel, PlayLaterAdapter.ViewHolder> {

    private final Context context;
    private final OnItemClickListener clickListener;
    private final OnMenuClickListener menuClickListener;

    public PlayLaterAdapter(@NonNull Context context,
                            @NonNull OnItemClickListener clickListener,
                            @NonNull OnMenuClickListener menuClickListener) {
        super(new DiffUtil.ItemCallback<VideoModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull VideoModel oldItem, @NonNull VideoModel newItem) {
                return Objects.equals(oldItem.getVideoId(), newItem.getVideoId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull VideoModel oldItem, @NonNull VideoModel newItem) {
                return Objects.equals(oldItem, newItem);
            }
        });
        this.context = context;
        this.clickListener = clickListener;
        this.menuClickListener = menuClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_play_later_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoModel video = getItem(position);

        holder.title.setText(video.getTitle());
        holder.tvChannelName.setText(video.getArtistOrChannel());

        Glide.with(context)
                .load(video.getThumbnail())
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .error(R.drawable.ic_thumbnail_placeholder)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> clickListener.onItemClick(video));
        holder.btnMenu.setOnClickListener(v -> menuClickListener.onMenuClick(v, video));
    }

    public interface OnItemClickListener {
        void onItemClick(VideoModel video);
    }

    public interface OnMenuClickListener {
        void onMenuClick(View anchorView, VideoModel video);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView tvChannelName;
        final View btnMenu;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            tvChannelName = itemView.findViewById(R.id.tvChannelName);
            btnMenu = itemView.findViewById(R.id.btnMenu);
        }
    }
}
