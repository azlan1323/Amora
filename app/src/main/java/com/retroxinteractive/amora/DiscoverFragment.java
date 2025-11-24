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

    public DiscoverFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
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

        adapter = new DiscoverAdapter(profiles, clickedUser -> {
            openUserProfile(clickedUser.getUid());
        });

        rvProfiles.setAdapter(adapter);

        loadCurrentUserAndThenUsers();
    }

    private void loadCurrentUserAndThenUsers() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show();
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

                    myInterests.clear();
                    DataSnapshot myInterestsSnap = snapshot.child("interests");
                    for (DataSnapshot iSnap : myInterestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (!TextUtils.isEmpty(interest)) {
                            myInterests.add(interest);
                        }
                    }
                }

                loadAllUsers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void loadAllUsers() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String myUid = currentUser.getUid();

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                profiles.clear();

                for (DataSnapshot snap : snapshot.getChildren()) {

                    if (!snap.exists()) continue;

                    String uid = snap.getKey();
                    if (uid == null || uid.equals(myUid)) continue;

                    String name = snap.child("name").getValue(String.class);
                    String profileUrl = snap.child("profileImageUrl").getValue(String.class);

                    Double lat = snap.child("latitude").getValue(Double.class);
                    Double lng = snap.child("longitude").getValue(Double.class);

                    long matchScore = calculateMatchScore(snap);
                    String distanceStr = calculateDistanceString(lat, lng);

                    profiles.add(new UserProfile(uid, name, profileUrl, distanceStr, matchScore));
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private String calculateDistanceString(Double lat, Double lng) {
        if (!hasCurrentLocation || lat == null || lng == null) return "-- km";

        double earthRadiusKm = 6371.0;

        double dLat = Math.toRadians(lat - currentLat);
        double dLng = Math.toRadians(lng - currentLng);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(currentLat)) *
                        Math.cos(Math.toRadians(lat)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double distanceKm = earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        DecimalFormat df = new DecimalFormat("#.#");
        return df.format(distanceKm) + " km";
    }

    private long calculateMatchScore(DataSnapshot userSnap) {
        long score = 0;
        DataSnapshot interestsSnap = userSnap.child("interests");
        for (DataSnapshot iSnap : interestsSnap.getChildren()) {
            String interest = iSnap.getValue(String.class);
            if (!TextUtils.isEmpty(interest) && myInterests.contains(interest)) {
                score++;
            }
        }
        return score;
    }

    private void openUserProfile(@NonNull String uid) {
        ProfileFragment fragment = ProfileFragment.newInstanceForUser(uid);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .addToBackStack("discover_to_profile")
                .commit();
    }



}

