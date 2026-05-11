package com.sns.kanta.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.sns.kanta.R;
import com.sns.kanta.helper.TextFormatter;
import com.sns.kanta.queueing.QueueManager;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private final Context context;
    private final OnReserveClickListener reserveClickListener;
    private final QueueManager queueManager;
    private final TextFormatter textFormatter = TextFormatter.getInstance();
    private List<VideoModel> videoList;

    public SearchResultAdapter(Context context, OnReserveClickListener listener) {
        this.context = context;
        this.videoList = new ArrayList<>();
        this.reserveClickListener = listener;
        this.queueManager = QueueManager.getInstance(context);
    }

    public void updateResults(List<VideoModel> results) {
        this.videoList = results;
        notifyDataSetChanged();
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

        String formattedTitle = textFormatter.formatSongTitle(video.getTitle());
        holder.title.setText(formattedTitle);
        holder.channel.setText(video.getChannel());

        // Load thumbnail
        Glide.with(context)
                .load(video.getThumbnail())
                .apply(new RequestOptions()
                        .placeholder(R.drawable.ic_thumbnail_placeholder)
                        .error(R.drawable.ic_thumbnail_placeholder)
                        .transform(new RoundedCorners(8)))
                .into(holder.thumbnail);

        // Check if already in queue
        if (queueManager.isInQueue(video.getVideoId())) {
            holder.btnReserve.setText("Queued");
            holder.btnReserve.setEnabled(false);
            holder.btnReserve.setAlpha(0.5f);
        } else {
            holder.btnReserve.setText("Reserve");
            holder.btnReserve.setEnabled(true);
            holder.btnReserve.setAlpha(1f);
        }

        holder.btnReserve.setOnClickListener(v -> {
            if (reserveClickListener != null) {
                reserveClickListener.onReserveClick(video);
            }
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public interface OnReserveClickListener {
        void onReserveClick(VideoModel video);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title;
        TextView channel;
        MaterialButton btnReserve;

        ViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            channel = itemView.findViewById(R.id.channel);
            btnReserve = itemView.findViewById(R.id.btnReserve);
        }
    }
}