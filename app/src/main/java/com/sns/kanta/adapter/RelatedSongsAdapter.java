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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.sns.kanta.R;
import com.sns.kanta.helper.SongParser;
import com.sns.kanta.queueing.QueueManager;

import java.util.ArrayList;
import java.util.List;

public final class RelatedSongsAdapter
        extends RecyclerView.Adapter<RelatedSongsAdapter.ViewHolder> {

    // ── Callback ──────────────────────────────────────────────────────────────

    private final Context context;

    // ── Fields ────────────────────────────────────────────────────────────────
    private final QueueManager queueManager;
    private final OnAddClickListener addListener;
    private List<VideoModel> songs = new ArrayList<>();

    public RelatedSongsAdapter(@NonNull Context context,
                               @NonNull OnAddClickListener addListener) {
        this.context = context;
        this.queueManager = QueueManager.getInstance(context);
        this.addListener = addListener;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Replace the dataset using DiffUtil so only changed rows are redrawn.
     */
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
                return songs.get(oldPos).getVideoId()
                        .equals(newSongs.get(newPos).getVideoId());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                VideoModel o = songs.get(oldPos);
                VideoModel n = newSongs.get(newPos);
                // Re-bind if queue status may have changed
                return o.getVideoId().equals(n.getVideoId())
                        && queueManager.isInQueue(o.getVideoId())
                        == queueManager.isInQueue(n.getVideoId());
            }
        });

        songs = new ArrayList<>(newSongs);
        diff.dispatchUpdatesTo(this);
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /**
     * Notify that queue state changed so "Add" / "Queued" badges update.
     * Call this after adding or removing items from QueueManager.
     */
    public void notifyQueueChanged() {
        notifyItemRangeChanged(0, songs.size(), /* payload */ "queue_changed");
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.list_item_related_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bind(holder, songs.get(position));
    }

    /**
     * Partial bind — only refresh the queue badge, skip Glide reload.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && "queue_changed".equals(payloads.get(0))) {
            refreshQueueBadge(holder, songs.get(position));
        } else {
            bind(holder, songs.get(position));
        }
    }

    private void bind(@NonNull ViewHolder holder, @NonNull VideoModel video) {
        holder.title.setText(SongParser.extractSongName(video.getTitle()));
        holder.artist.setText(SongParser.extractArtist(video.getTitle()));

        Glide.with(context)
                .load(video.getThumbnail())
                .apply(new RequestOptions()
                        .placeholder(R.drawable.ic_thumbnail_placeholder)
                        .error(R.drawable.ic_thumbnail_placeholder)
                        .transform(new RoundedCorners(8)))
                .into(holder.thumbnail);

        refreshQueueBadge(holder, video);

        holder.btnAdd.setOnClickListener(v -> {
            if (!queueManager.isInQueue(video.getVideoId())) {
                addListener.onAddClick(video);
                // Optimistically update this row immediately
                refreshQueueBadge(holder, video);
            }
        });

        // Row tap — delegate to the activity via the same listener
        // Activity decides whether to play immediately or just add
        holder.itemView.setOnClickListener(v -> addListener.onAddClick(video));
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private void refreshQueueBadge(@NonNull ViewHolder holder,
                                   @NonNull VideoModel video) {
        boolean queued = queueManager.isInQueue(video.getVideoId());
        holder.btnAdd.setText(queued
                ? context.getString(R.string.label_queued)
                : context.getString(R.string.btn_reserve));
        holder.btnAdd.setEnabled(!queued);
        holder.btnAdd.setAlpha(queued ? 0.5f : 1f);
    }

    /**
     * Called when the user taps "Add" on a related song.
     * The activity/fragment decides what to do (add to queue, play immediately).
     */
    public interface OnAddClickListener {
        void onAddClick(@NonNull VideoModel video);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView artist;
        final MaterialButton btnAdd;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.title);
            artist = itemView.findViewById(R.id.artist);
            btnAdd = itemView.findViewById(R.id.btnAddToQueue);
        }
    }
}