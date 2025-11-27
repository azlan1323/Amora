package com.retroxinteractive.amora;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatThreadAdapter extends RecyclerView.Adapter<ChatThreadAdapter.ChatThreadViewHolder> {

    public interface OnThreadClickListener {
        void onThreadClick(ChatThread thread);
    }

    private List<ChatThread> threads;
    private OnThreadClickListener listener;

    public ChatThreadAdapter(List<ChatThread> threads, OnThreadClickListener listener) {
        this.threads = threads;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatThreadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_thread, parent, false);
        return new ChatThreadViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatThreadViewHolder holder, int position) {
        ChatThread thread = threads.get(position);

        holder.txtName.setText(thread.otherUserName != null ? thread.otherUserName : "User");
        holder.txtLastMessage.setText(thread.lastMessage != null ? thread.lastMessage : "");

        if (thread.lastTimestamp > 0) {
            String time = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(new Date(thread.lastTimestamp));
            holder.txtTime.setText(time);
        } else {
            holder.txtTime.setText("");
        }

        if (thread.unreadCount > 0) {
            holder.badgeUnread.setVisibility(View.VISIBLE);
            holder.badgeUnread.setText(String.valueOf(thread.unreadCount));
        } else {
            holder.badgeUnread.setVisibility(View.GONE);
        }

        if (thread.otherUserPhotoUrl != null && !thread.otherUserPhotoUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(thread.otherUserPhotoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .into(holder.imgAvatar);
        } else {
            holder.imgAvatar.setImageResource(R.drawable.ic_profile);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onThreadClick(thread);
        });
    }

    @Override
    public int getItemCount() {
        return threads != null ? threads.size() : 0;
    }

    static class ChatThreadViewHolder extends RecyclerView.ViewHolder {
        CircleImageView imgAvatar;
        TextView txtName, txtLastMessage, txtTime, badgeUnread;

        ChatThreadViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            txtName = itemView.findViewById(R.id.txtName);
            txtLastMessage = itemView.findViewById(R.id.txtLastMessage);
            txtTime = itemView.findViewById(R.id.txtTime);
            badgeUnread = itemView.findViewById(R.id.badgeUnread);
        }
    }
}