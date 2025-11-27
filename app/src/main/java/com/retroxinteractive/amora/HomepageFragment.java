package com.retroxinteractive.amora;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HomepageFragment extends Fragment {

    private HomeProfileAdapter adapter;

    private DatabaseReference usersRef;
    private ValueEventListener usersListener;
    private TextView tvLocation;
    private ImageView img_avatar;
    FirebaseUser currentUser;

    private TextView tabForYou, tabNearby;

    // Data
    private final List<UserProfile> allProfiles = new ArrayList<>();
    private final List<UserProfile> nearbyProfiles = new ArrayList<>();

    // State: which tab is active
    private boolean showingNearby = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_homepage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rvProfiles = view.findViewById(R.id.rv_profiles);

        // Horizontal carousel
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext(),
                        LinearLayoutManager.HORIZONTAL,
                        false);
        rvProfiles.setLayoutManager(layoutManager);
        rvProfiles.setHasFixedSize(true);

        adapter = new HomeProfileAdapter(requireContext());
        rvProfiles.setAdapter(adapter);

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        tvLocation = view.findViewById(R.id.tv_location);
        img_avatar = view.findViewById(R.id.img_avatar);

        currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // Not logged in, nothing to show
            return;
        }

        setFields();
        attachUsersListener(currentUser.getUid());

        tabForYou = view.findViewById(R.id.tab_for_you);
        tabNearby = view.findViewById(R.id.tab_nearby);

        // Tab clicks
        tabForYou.setOnClickListener(v -> {
            showingNearby = false;
            updateTabStyles();
            applyCurrentFilter();
        });

        tabNearby.setOnClickListener(v -> {
            showingNearby = true;
            updateTabStyles();
            applyCurrentFilter();
        });

        // initial style (For You selected)
        updateTabStyles();
    }

    private void setFields() {
        String uid = currentUser.getUid();

        DatabaseReference addressRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("address");

        addressRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String address = snapshot.getValue(String.class);
                if (address != null) {
                    // use address
                    Log.d("USER_ADDRESS", "Address: " + address);
                    tvLocation.setText(address);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        DatabaseReference pictureRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("profileImageUrl");

        pictureRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String picture = snapshot.getValue(String.class);
                if (picture != null) {
                    // use address
                    Log.d("Profile Picture URL: ", "Picture" + picture);
                    Glide.with(requireContext())
                            .load(picture)
                            .placeholder(R.drawable.ic_profile)
                            .centerCrop()
                            .into(img_avatar);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void attachUsersListener(String currentUid) {
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                // Get current user's location (for distance calculation)
                double myLat = 0.0;
                double myLng = 0.0;
                boolean haveMyLocation = false;

                // Interests of the current user
                List<String> myInterests = new ArrayList<>();

                DataSnapshot meSnap = snapshot.child(currentUid);
                if (meSnap.exists()) {
                    Double lat = meSnap.child("latitude").getValue(Double.class);
                    Double lng = meSnap.child("longitude").getValue(Double.class);
                    if (lat != null && lng != null) {
                        myLat = lat;
                        myLng = lng;
                        haveMyLocation = true;
                    }

                    // read my interests
                    DataSnapshot myInterestsSnap = meSnap.child("interests");
                    for (DataSnapshot iSnap : myInterestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (interest != null && !interest.trim().isEmpty()) {
                            myInterests.add(interest);
                        }
                    }
                }

                List<UserProfile> result = new ArrayList<>();

                allProfiles.clear();
                nearbyProfiles.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.getKey();
                    if (uid == null || uid.equals(currentUid)) {
                        // Skip self or invalid key
                        continue;
                    }

                    Boolean profileCompleted =
                            child.child("profileCompleted").getValue(Boolean.class);
                    if (profileCompleted != null && !profileCompleted) {
                        // Skip incomplete profiles
                        continue;
                    }

                    String name = child.child("name").getValue(String.class);
                    String bio = child.child("bio").getValue(String.class);
                    String address = child.child("address").getValue(String.class);
                    String age = child.child("age").getValue(String.class);

                    // This is how it is saved in your DB
                    String imageUrl = child.child("profileImageUrl").getValue(String.class);
                    Boolean verified = child.child("verified").getValue(Boolean.class);

                    // Interests (0,1,2,...)
                    List<String> interests = new ArrayList<>();
                    DataSnapshot interestsSnap = child.child("interests");
                    for (DataSnapshot iSnap : interestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (interest != null && !interest.trim().isEmpty()) {
                            interests.add(interest);
                        }
                    }

                    // Distance
                    Double distance = null;
                    if (haveMyLocation) {
                        Double lat = child.child("latitude").getValue(Double.class);
                        Double lng = child.child("longitude").getValue(Double.class);
                        if (lat != null && lng != null) {
                            distance = calculateDistanceKm(myLat, myLng, lat, lng);
                        }
                    }

                    UserProfile profile = new UserProfile();
                    profile.setUid(uid);
                    profile.setName(name);
                    profile.setBio(bio);
                    profile.setAge(age);
                    profile.setAddress(address);
                    profile.setPhotoUrl(imageUrl);
                    profile.setVerified(verified != null && verified);
                    profile.setDistanceKm(distance);
                    profile.setInterests(interests);

                    // Match percent
                    int matchPercent = calculateMatchPercent(myInterests, interests);
                    profile.setMatchPercent(matchPercent);

                    result.add(profile);

                    allProfiles.add(profile);

                    // Nearby: distance < 5km
                    if (distance != null && distance < 5.0) {
                        nearbyProfiles.add(profile);
                    }
                }

                // After the loop:
                updateCountsAndApplyCurrentFilter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                Toast.makeText(
                        requireContext(),
                        "Failed to load profiles: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        usersRef.addValueEventListener(usersListener);
    }

    private void updateCountsAndApplyCurrentFilter() {
        // Update the labels like "For You (10)" and "Nearby (3)"
        String str = "For You (" + allProfiles.size() + ")";
        tabForYou.setText(str);
        str = "Nearby (" + nearbyProfiles.size() + ")";
        tabNearby.setText(str);

        applyCurrentFilter();
    }

    private void applyCurrentFilter() {
        if (adapter == null) return;

        if (showingNearby) {
            adapter.setProfiles(nearbyProfiles);
        } else {
            adapter.setProfiles(allProfiles);
        }
    }

    private void updateTabStyles() {
        // Highlight selected tab with white text, dim the other
        if (showingNearby) {
            tabNearby.setTextColor(0xFFFFFFFF);       // white
            tabForYou.setTextColor(0x80FFFFFF);       // 50% white
        } else {
            tabForYou.setTextColor(0xFFFFFFFF);
            tabNearby.setTextColor(0x80FFFFFF);
        }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (usersListener != null) {
            usersRef.removeEventListener(usersListener);
            usersListener = null;
        }
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
}
