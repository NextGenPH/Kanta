package com.sns.kanta.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sns.kanta.R;

import java.util.ArrayList;
import java.util.List;

public final class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    private final OnHistoryClickListener clickListener;
    private List<String> historyItems = new ArrayList<>();

    public SearchHistoryAdapter(OnHistoryClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setHistoryItems(List<String> items) {
        this.historyItems = items;
        notifyDataSetChanged();
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
        String query = historyItems.get(position);
        holder.txtQuery.setText(query);
        holder.itemView.setOnClickListener(v -> clickListener.onHistoryClick(query));
    }

    @Override
    public int getItemCount() {
        return historyItems.size();
    }

    public interface OnHistoryClickListener {
        void onHistoryClick(String query);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtQuery;

        ViewHolder(View itemView) {
            super(itemView);
            txtQuery = itemView.findViewById(R.id.txtHistoryQuery);
        }
    }
}
