package com.retroxinteractive.amora;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.retroxinteractive.amora.R;

import java.util.ArrayList;
import java.util.List;

public class HomeProfileAdapter extends RecyclerView.Adapter<HomeProfileAdapter.ProfileViewHolder> {

    private final Context context;
    private final List<UserProfile> profiles = new ArrayList<>();

    public HomeProfileAdapter(Context context) {
        this.context = context;
    }

    public void setProfiles(List<UserProfile> newProfiles) {
        profiles.clear();
        if (newProfiles != null) {
            profiles.addAll(newProfiles);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.profile_card_homepage, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        UserProfile profile = profiles.get(position);

        // Name
        holder.tvName.setText(profile.getName() != null ? profile.getName() : "Unknown");

        // Bio
        if (profile.getBio() != null && !profile.getBio().isEmpty()) {
            holder.tvBio.setText(profile.getBio());
            holder.tvBio.setVisibility(View.VISIBLE);
        } else {
            holder.tvBio.setText("");
            holder.tvBio.setVisibility(View.GONE);
        }

        // Distance
        if (profile.getDistanceKm() != null) {
            String text = String.format("%.1f km", profile.getDistanceKm());
            holder.tvDistance.setText(text);
            holder.layoutDistance.setVisibility(View.VISIBLE);
        } else {
            holder.layoutDistance.setVisibility(View.GONE);
        }

        // Match %
        if (profile.getMatchPercent() != null) {
            holder.tvMatchPercent.setText(profile.getMatchPercent() + "%");
            holder.layoutMatch.setVisibility(View.VISIBLE);
        } else {
            holder.layoutMatch.setVisibility(View.GONE);
        }

        // Interests – show up to 3 in the 3 chips
        List<String> interests = profile.getInterests();
        if (interests != null && !interests.isEmpty()) {
            setChip(holder.chip1, interests, 0);
            setChip(holder.chip2, interests, 1);
            setChip(holder.chip3, interests, 2);
        } else {
            holder.chip1.setVisibility(View.GONE);
            holder.chip2.setVisibility(View.GONE);
            holder.chip3.setVisibility(View.GONE);
        }

        // Profile image
        if (profile.getPhotoUrl() != null && !profile.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(profile.getPhotoUrl())
                    .placeholder(R.drawable.ic_nav_profile)
                    .centerCrop()
                    .into(holder.imgProfile);
        } else {
            holder.imgProfile.setImageResource(R.drawable.ic_nav_profile);
        }

        // Like button – (logic can be implemented later, e.g., saving to favorites)
        holder.btnLike.setOnClickListener(v -> {
            // TODO: implement like / unlike behavior
        });

        // Chat button – open ChatFragment through MainActivity
        holder.btnChat.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;
                activity.openChatWith(
                        profile.getUid(),          // make sure UserProfile has getUid()
                        profile.getName(),
                        profile.getPhotoUrl()
                );
            }
        });
    }

    private void setChip(TextView chip, List<String> interests, int index) {
        if (interests.size() > index) {
            chip.setText(interests.get(index));
            chip.setVisibility(View.VISIBLE);
        } else {
            chip.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {

        ImageView imgProfile;
        LinearLayout layoutDistance, layoutMatch;
        TextView tvDistance, tvMatchPercent;
        TextView tvName, tvBio;
        TextView chip1, chip2, chip3;
        ImageView btnLike, btnChat;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);

            imgProfile      = itemView.findViewById(R.id.img_profile);
            layoutDistance  = itemView.findViewById(R.id.layout_distance);
            layoutMatch     = itemView.findViewById(R.id.layout_match);
            tvDistance      = itemView.findViewById(R.id.tv_distance);
            tvMatchPercent  = itemView.findViewById(R.id.tv_match_percent);
            tvName          = itemView.findViewById(R.id.tv_name);
            tvBio           = itemView.findViewById(R.id.tv_bio);
            chip1           = itemView.findViewById(R.id.chip_interest_1);
            chip2           = itemView.findViewById(R.id.chip_interest_2);
            chip3           = itemView.findViewById(R.id.chip_interest_3);
            btnLike         = itemView.findViewById(R.id.btn_like);
            btnChat         = itemView.findViewById(R.id.btn_chat);
        }
    }
}