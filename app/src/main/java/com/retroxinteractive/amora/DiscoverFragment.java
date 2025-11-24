package com.retroxinteractive.amora;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Discover screen showing mini profile cards in a grid.
 * Layout: fragment_discover.xml
 * Item:   item_mini_profile_card.xml
 */
public class DiscoverFragment extends Fragment {

    private RecyclerView rvProfiles;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    private DiscoverAdapter adapter;
    private final List<UserProfile> profiles = new ArrayList<>();

    // current logged-in user's coords (from DB)
    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private boolean hasCurrentLocation = false;
    private final List<String> myInterests = new ArrayList<>();


    public DiscoverFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate fragment_discover.xml
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        rvProfiles = view.findViewById(R.id.rv_discover_profiles);
        rvProfiles.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvProfiles.setHasFixedSize(true);

        adapter = new DiscoverAdapter(profiles);
        rvProfiles.setAdapter(adapter);

        loadCurrentUserAndThenUsers();
    }

    /**
     * First load current user's coordinates from /users/<uid>,
     * then load all other users for Discover.
     */
    private void loadCurrentUserAndThenUsers() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(),
                    "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Double lat = snapshot.child("latitude").getValue(Double.class);
                    Double lng = snapshot.child("longitude").getValue(Double.class);
                    if (lat != null && lng != null) {
                        currentLat = lat;
                        currentLng = lng;
                        hasCurrentLocation = true;
                    }

                    // Read my interests for matching
                    myInterests.clear();
                    DataSnapshot myInterestsSnap = snapshot.child("interests");
                    for (DataSnapshot iSnap : myInterestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (interest != null && !interest.trim().isEmpty()) {
                            myInterests.add(interest);
                        }
                    }
                }
                loadAllProfiles();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hasCurrentLocation = false;
                loadAllProfiles();
            }
        });
    }

    /**
     * Load all users from /users and populate RecyclerView.
     */
    private void loadAllProfiles() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String myUid = user.getUid();

        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                profiles.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.getKey();
                    if (uid == null) continue;

                    // Skip self in discover list
                    if (uid.equals(myUid)) continue;

                    // Optional: only show completed profiles
                    Boolean completed = child.child("profileCompleted").getValue(Boolean.class);
                    if (completed != null && !completed) {
                        continue;
                    }

                    String name = child.child("name").getValue(String.class);
                    String bio = child.child("bio").getValue(String.class);
                    String imageUrl = child.child("profileImageUrl").getValue(String.class);
                    Boolean verified = child.child("verified").getValue(Boolean.class);

                    Double lat = child.child("latitude").getValue(Double.class);
                    Double lng = child.child("longitude").getValue(Double.class);

                    // Age can be stored as number or string in Firebase, so read it generically
                    int age = 0;
                    DataSnapshot ageSnap = child.child("age");
                    if (ageSnap.exists()) {
                        try {
                            String ageStr = String.valueOf(ageSnap.getValue());  // works for "23" or 23
                            if (!TextUtils.isEmpty(ageStr)) {
                                age = Integer.parseInt(ageStr);
                            }
                        } catch (NumberFormatException e) {
                            age = 0; // fallback if malformed
                        }
                    }

                    // Other user's interests
                    List<String> otherInterests = new ArrayList<>();
                    DataSnapshot interestsSnap = child.child("interests");
                    for (DataSnapshot iSnap : interestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (interest != null && !interest.trim().isEmpty()) {
                            otherInterests.add(interest);
                        }
                    }

                    boolean isVerified = verified != null && verified;

                    double distanceKm = -1.0;
                    if (hasCurrentLocation && lat != null && lng != null) {
                        distanceKm = calculateDistanceKm(currentLat, currentLng, lat, lng);
                    }

                    // NEW: compute match percent using helper
                    int matchPercent = calculateMatchPercent(myInterests, otherInterests);

                    UserProfile profile = new UserProfile();
                    profile.uid = uid;
                    profile.name = name != null ? name : "";
                    profile.age = age;
                    profile.bio = bio != null ? bio : "";
                    profile.profileImageUrl = imageUrl != null ? imageUrl : "";
                    profile.verified = isVerified;
                    profile.distanceKm = distanceKm;
                    profile.matchPercent = matchPercent;

                    profiles.add(profile);
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded() || getContext() == null) {
                    return; // Stop execution if the fragment is dead
                }

                Toast.makeText(requireContext(),
                        "Failed to load users: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Haversine formula – distance in km between two lat/lon pairs.
     */
    private double calculateDistanceKm(double lat1, double lon1,
                                       double lat2, double lon2) {
        double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Returns how many of myInterests are shared with otherInterests, as a %.
     * Example: my = ["Art","Music","Travel"], other = ["Music","Cooking"]
     * common = 1 → 1/3 ≈ 33%.
     */
    private int calculateMatchPercent(List<String> myInterests, List<String> otherInterests) {
        if (myInterests == null || myInterests.isEmpty()
                || otherInterests == null || otherInterests.isEmpty()) {
            return 0;
        }

        // count only non-empty items in myInterests
        int baseCount = 0;
        for (String s : myInterests) {
            if (s != null && !s.trim().isEmpty()) {
                baseCount++;
            }
        }
        if (baseCount == 0) return 0;

        int common = 0;
        for (String mine : myInterests) {
            if (mine == null || mine.trim().isEmpty()) continue;
            String mineNorm = mine.trim().toLowerCase();

            // see if other user has this interest (case-insensitive)
            for (String other : otherInterests) {
                if (other == null || other.trim().isEmpty()) continue;
                if (mineNorm.equals(other.trim().toLowerCase())) {
                    common++;
                    break; // avoid double-counting
                }
            }
        }

        return (int) Math.round(common * 100.0 / baseCount);
    }

    // ───────────────────── MODEL ─────────────────────

    static class UserProfile {
        String uid;
        String name;
        int age;
        String bio;
        String profileImageUrl;
        boolean verified;
        double distanceKm;   // -1 if unknown
        int matchPercent;    // 0–100
        List<String> interests;
    }

    // ───────────────────── ADAPTER ─────────────────────

    private class DiscoverAdapter extends RecyclerView.Adapter<DiscoverAdapter.ProfileViewHolder> {

        private final List<UserProfile> items;
        private final DecimalFormat distanceFormat = new DecimalFormat("#.#");

        DiscoverAdapter(List<UserProfile> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mini_profile_card, parent, false);
            return new ProfileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
            UserProfile profile = items.get(position);

            // Name + (optional age)
            if (profile.age > 0) {
                String str = profile.name + ", " + profile.age;
                holder.tvName.setText(str);
            } else {
                holder.tvName.setText(profile.name);
            }

            // Verified badge visibility
            holder.imgVerified.setVisibility(profile.verified ? View.VISIBLE : View.GONE);

            // Distance
            if (profile.distanceKm >= 0) {
                String text = distanceFormat.format(profile.distanceKm) + " km away";
                holder.tvDistance.setText(text);
            } else {
                holder.tvDistance.setText("—");
            }

            // Match %
            if (profile.matchPercent > 0) {
                String str = profile.matchPercent + "%";
                holder.tvMatchPercent.setText(str);
            } else {
                holder.tvMatchPercent.setText("0%");
            }

            // Load top image
            if (!TextUtils.isEmpty(profile.profileImageUrl)) {
                Glide.with(holder.itemView.getContext())
                        .load(profile.profileImageUrl)
                        .centerCrop()
                        .into(holder.imgTopArea);
            } else {
                holder.imgTopArea.setImageResource(0); // or a placeholder
            }

            // Click listener – later you can open full details / chat
            holder.itemView.setOnClickListener(v -> {
                // TODO: open details screen for profile.uid
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ProfileViewHolder extends RecyclerView.ViewHolder {

            ImageView imgCardBg;
            ImageView imgTopArea;
            ImageView imgVerified;
            TextView tvName;
            TextView tvDistance;
            TextView tvMatchPercent;

            ProfileViewHolder(@NonNull View itemView) {
                super(itemView);
                imgCardBg = itemView.findViewById(R.id.img_card_bg);
                imgTopArea = itemView.findViewById(R.id.img_top_area);
                imgVerified = itemView.findViewById(R.id.img_verified);
                tvName = itemView.findViewById(R.id.tv_name);
                tvDistance = itemView.findViewById(R.id.tv_distance);
                tvMatchPercent = itemView.findViewById(R.id.tv_match_percent);
            }
        }
    }
}
