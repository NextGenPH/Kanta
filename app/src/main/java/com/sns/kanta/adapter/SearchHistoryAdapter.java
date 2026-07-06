package com.sns.kanta.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.sns.kanta.R;

import java.util.List;

public final class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    private final OnHistoryClickListener clickListener;

    private final AsyncListDiffer<String> differ = new AsyncListDiffer<>(this, new DiffUtil.ItemCallback<String>() {
        @Override
        public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }
    });

    public SearchHistoryAdapter(OnHistoryClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setHistoryItems(List<String> items) {
        differ.submitList(items);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_search_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String query = differ.getCurrentList().get(position);
        holder.txtQuery.setText(query);
        holder.itemView.setOnClickListener(v -> clickListener.onHistoryClick(query));
        holder.btnRemove.setOnClickListener(v -> clickListener.onRemoveHistory(query));
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    public interface OnHistoryClickListener {
        void onHistoryClick(String query);

        void onRemoveHistory(String query);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtQuery;
        View btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            txtQuery = itemView.findViewById(R.id.txtHistoryQuery);
            btnRemove = itemView.findViewById(R.id.btnRemoveHistory);
        }
    }
}
