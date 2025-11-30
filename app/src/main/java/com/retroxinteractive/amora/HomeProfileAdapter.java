package com.retroxinteractive.amora;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
        String str = profile.getName() + ", " + profile.getAge();
        holder.tvName.setText(str);

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
            str = profile.getMatchPercent() + "%";
            holder.tvMatchPercent.setText(str);
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
                    .placeholder(R.drawable.ic_profile)
                    .centerCrop()
                    .into(holder.imgProfile);
        } else {
            holder.imgProfile.setImageResource(R.drawable.ic_profile);
        }

        holder.btnLike.setOnClickListener(v -> {
            handleLikeClick(context, profile.getUid());
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

    private void handleLikeClick(Context context, String otherId) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(context, "You must be logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (otherId == null || otherId.equals(user.getUid())) {
            Toast.makeText(context, "Invalid profile to like", Toast.LENGTH_SHORT).show();
            return;
        }

        String myId = user.getUid();

        DatabaseReference likesRef = FirebaseDatabase.getInstance()
                .getReference("likes");

        // Save: I liked them
        likesRef.child(myId).child(otherId)
                .setValue(true)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(context, "You liked this profile", Toast.LENGTH_SHORT).show();

                    // Check if they already liked me back
                    likesRef.child(otherId).child(myId)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot.exists()) {
                                    // MATCH!!!
                                    DatabaseReference matchRef = FirebaseDatabase.getInstance()
                                            .getReference("matches");

                                    String matchId = matchRef.push().getKey();
                                    if (matchId != null) {
                                        matchRef.child(matchId).setValue(true);
                                    }

                                    Toast.makeText(context, "It's a Match!", Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context,
                            "Failed to like profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
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