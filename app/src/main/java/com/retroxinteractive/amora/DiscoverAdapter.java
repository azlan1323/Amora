package com.retroxinteractive.amora;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class DiscoverAdapter extends RecyclerView.Adapter<DiscoverAdapter.ViewHolder> {

    public interface OnUserClickListener {
        void onUserClick(UserProfile user);
    }

    private List<UserProfile> list;
    private OnUserClickListener listener;

    public DiscoverAdapter(List<UserProfile> list, OnUserClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mini_profile_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int i) {
        UserProfile u = list.get(i);

        h.name.setText(u.getName());
        h.distance.setText(u.getDistance());
        Glide.with(h.itemView.getContext())
                .load(u.getProfileUrl())
                .into(h.photo);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onUserClick(u);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView photo;
        TextView name, distance;

        ViewHolder(@NonNull View v) {
            super(v);
            photo = v.findViewById(R.id.img_profile);
            name = v.findViewById(R.id.tv_name);
            distance = v.findViewById(R.id.tv_distance);
        }
    }
}
