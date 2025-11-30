package com.retroxinteractive.amora;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import android.view.Menu;
import android.widget.PopupMenu;
import androidx.core.content.ContextCompat;

public class DiscoverFragment extends Fragment {

    private ImageView filterBtnTop;
    private HorizontalScrollView filterScroll;
    private LinearLayout filterContainer;

    // Data
    private final List<UserProfile> allProfiles = new ArrayList<>();
    private final List<UserProfile> filteredProfiles = new ArrayList<>();

    // Filters
    private final Set<String> selectedFilters = new LinkedHashSet<>();
    private final Set<String> availableFilters = new LinkedHashSet<>();

    // Firebase
    private FirebaseUser currentUser;
    private DatabaseReference usersRef;
    private ValueEventListener usersListener;

    // Adapter (CHANGE name if your adapter is called something else)
    private DiscoverAdapter adapter;

    public DiscoverFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_discover, container, false);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        filterBtnTop = root.findViewById(R.id.filter_btn_top);
        filterScroll = root.findViewById(R.id.filter_scroll);
        filterContainer = root.findViewById(R.id.filter_container);
        RecyclerView rvProfiles = root.findViewById(R.id.rv_discover_profiles);

        int spacing = 16; // dp
        int spacingPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                spacing,
                getResources().getDisplayMetrics()
        );

        // RecyclerView setup: 2-column grid
        rvProfiles.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new DiscoverAdapter(filteredProfiles);
        rvProfiles.setAdapter(adapter);

        // Hide filter bar initially
        filterScroll.setVisibility(View.GONE);

        // Filter button click → open popup menu with interests
        filterBtnTop.setOnClickListener(v -> showFilterMenu());

        // Add hardcoded defaults
        availableFilters.add("Art");
        availableFilters.add("Music");
        availableFilters.add("Gaming");
        availableFilters.add("Traveling");
        availableFilters.add("Cooking");

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        attachUsersListener();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (usersListener != null) {
            usersRef.removeEventListener(usersListener);
            usersListener = null;
        }
    }

    private void attachUsersListener() {
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        final String currentUid = currentUser.getUid();

        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allProfiles.clear();
                filteredProfiles.clear();
                availableFilters.clear();

                // ───── 1) Get current user's location + interests (same idea as HomepageFragment) ─────
                double myLat = 0.0;
                double myLng = 0.0;
                boolean haveMyLocation = false;
                List<String> myInterests = new ArrayList<>();

                DataSnapshot meSnap = snapshot.child(currentUid);
                if (meSnap.exists()) {
                    // Location
                    Double lat = meSnap.child("latitude").getValue(Double.class);
                    Double lng = meSnap.child("longitude").getValue(Double.class);
                    if (lat != null && lng != null) {
                        myLat = lat;
                        myLng = lng;
                        haveMyLocation = true;
                    }

                    // My interests
                    DataSnapshot myInterestsSnap = meSnap.child("interests");
                    for (DataSnapshot iSnap : myInterestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (interest != null && !interest.trim().isEmpty()) {
                            myInterests.add(interest.trim());
                        }
                    }
                }

                // ───── 2) Build profiles for everyone else ─────
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.getKey();
                    if (uid == null || uid.equals(currentUid)) continue;

                    Boolean profileCompleted =
                            child.child("profileCompleted").getValue(Boolean.class);
                    if (profileCompleted != null && !profileCompleted) continue;

                    String name = child.child("name").getValue(String.class);
                    String bio = child.child("bio").getValue(String.class);
                    String imageUrl = child.child("profileImageUrl").getValue(String.class);
                    Boolean verified = child.child("verified").getValue(Boolean.class);

                    // Optional age field (if present in DB)
                    long ageLong = Long.parseLong(Objects.requireNonNull(child.child("age").getValue(String.class)));
                    int age = (int) ageLong;

                    // Interests list
                    List<String> interests = new ArrayList<>();
                    DataSnapshot interestsSnap = child.child("interests");
                    for (DataSnapshot iSnap : interestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (interest != null && !interest.trim().isEmpty()) {
                            interests.add(interest.trim());
                            availableFilters.add(interest.trim());
                        }
                    }

                    // Distance
                    double distanceKm = -1;
                    if (haveMyLocation) {
                        Double lat = child.child("latitude").getValue(Double.class);
                        Double lng = child.child("longitude").getValue(Double.class);
                        if (lat != null && lng != null) {
                            distanceKm = calculateDistanceKm(myLat, myLng, lat, lng);
                        }
                    }

                    // Match %
                    int matchPercent = calculateMatchPercent(myInterests, interests);

                    // Build profile
                    UserProfile profile = new UserProfile();
                    profile.uid = uid;
                    profile.name = name;
                    profile.age = age;
                    profile.bio = bio;
                    profile.profileImageUrl = imageUrl;
                    profile.verified = (verified != null && verified);
                    profile.distanceKm = distanceKm;      // -1 means unknown
                    profile.matchPercent = matchPercent;  // 0–100
                    profile.interests = interests;

                    allProfiles.add(profile);
                }

                // Once we have data → apply filters (or show all)
                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                Toast.makeText(
                        requireContext(),
                        "Failed to load discover profiles: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        usersRef.addValueEventListener(usersListener);
    }

    /**
     * Haversine distance in km between 2 lat/lng pairs.
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

        int common = getCommon(myInterests, otherInterests);

        return (int) Math.round(common * 100.0 / baseCount);
    }

    private int getCommon(List<String> myInterests, List<String> otherInterests) {
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
        return common;
    }

    // ─────────────────────────────────────────────
    // Popup menu for picking filters (interests)
    // ─────────────────────────────────────────────
    private void showFilterMenu() {
        if (availableFilters.isEmpty()) {
            Toast.makeText(requireContext(),
                    "No filters available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        PopupMenu menu = new PopupMenu(requireContext(), filterBtnTop);
        int id = 0;
        for (String interest : availableFilters) {
            // Avoid adding filters that are already selected
            if (!selectedFilters.contains(interest)) {
                menu.getMenu().add(Menu.NONE, id++, Menu.NONE, interest);
            }
        }

        // If every available filter is already selected, nothing to show
        if (menu.getMenu().size() == 0) {
            Toast.makeText(requireContext(),
                    "All filters already selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        menu.setOnMenuItemClickListener(item -> {
            String chosen = Objects.requireNonNull(item.getTitle()).toString();
            if (!TextUtils.isEmpty(chosen) && !selectedFilters.contains(chosen)) {
                selectedFilters.add(chosen);
                refreshFilterChips();
                applyFilters();
            }
            return true;
        });

        menu.show();
    }

    // ─────────────────────────────────────────────
    // Show selected filters as chips
    // ─────────────────────────────────────────────
    private void refreshFilterChips() {
        filterContainer.removeAllViews();

        if (selectedFilters.isEmpty()) {
            filterScroll.setVisibility(View.GONE);
            return;
        }

        filterScroll.setVisibility(View.VISIBLE);

        for (String filter : selectedFilters) {
            TextView chip = createFilterChip(filter);
            filterContainer.addView(chip);
        }
    }

    private TextView createFilterChip(String label) {
        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(14f);
        tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        tv.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.filters_btn));
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        tv.setLayoutParams(lp);

        // Clicking a chip removes that filter
        tv.setOnClickListener(v -> {
            selectedFilters.remove(label);
            refreshFilterChips();
            applyFilters();
        });

        return tv;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }

    // ─────────────────────────────────────────────
    // Apply filters to profiles
    // ─────────────────────────────────────────────
    private void applyFilters() {
        filteredProfiles.clear();

        if (selectedFilters.isEmpty()) {
            filteredProfiles.addAll(allProfiles);
        } else {
            for (UserProfile profile : allProfiles) {
                List<String> interests = profile.interests;
                if (interests == null) interests = new ArrayList<>();

                if (interestsContainAll(interests, selectedFilters)) {
                    filteredProfiles.add(profile);
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private boolean interestsContainAll(List<String> interests, Set<String> filters) {
        // Every filter must be present in profile's interests
        for (String f : filters) {
            if (!interests.contains(f)) return false;
        }
        return true;
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

    /**
     * Open full profile screen for the selected user.
     * Currently passes the user's uid as param1 to ProfileFragment.
     */
    private void openUserProfile(@NonNull UserProfile profile) {
        // Hide bottom nav while viewing someone else's profile
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisible(false);
        }

        ProfileFragment fragment = ProfileFragment.newInstance(profile.uid, null);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .addToBackStack("discover_to_profile")
                .commit();
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

            // Click: open this user's full profile
            holder.itemView.setOnClickListener(v -> openUserProfile(profile));
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
