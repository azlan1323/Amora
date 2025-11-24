package com.retroxinteractive.amora;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class HomepageFragment extends Fragment {

    private RecyclerView rvProfiles;
    private HomeProfileAdapter adapter;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private ValueEventListener usersListener;

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

        rvProfiles = view.findViewById(R.id.rv_profiles);

        // Horizontal carousel
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext(),
                        LinearLayoutManager.HORIZONTAL,
                        false);
        rvProfiles.setLayoutManager(layoutManager);
        rvProfiles.setHasFixedSize(true);

        adapter = new HomeProfileAdapter(requireContext());
        rvProfiles.setAdapter(adapter);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // Not logged in, nothing to show
            return;
        }

        attachUsersListener(currentUser.getUid());
    }

    private void attachUsersListener(String currentUid) {
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                // Get current user's location (for distance calculation)
                double myLat = 0.0;
                double myLng = 0.0;
                boolean haveMyLocation = false;

                DataSnapshot meSnap = snapshot.child(currentUid);
                if (meSnap.exists()) {
                    Double lat = meSnap.child("latitude").getValue(Double.class);
                    Double lng = meSnap.child("longitude").getValue(Double.class);
                    if (lat != null && lng != null) {
                        myLat = lat;
                        myLng = lng;
                        haveMyLocation = true;
                    }
                }

                List<UserProfile> result = new ArrayList<>();

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
                    // This is how it is saved in your DB
                    String imageUrl = child.child("profileImageUrl").getValue(String.class);
                    Boolean verified = child.child("verified").getValue(Boolean.class);

                    // Interests (0,1,2,...)
                    List<String> interests = new ArrayList<>();
                    DataSnapshot interestsSnap = child.child("interests");
                    for (DataSnapshot iSnap : interestsSnap.getChildren()) {
                        String interest = iSnap.getValue(String.class);
                        if (interest != null && !interest.isEmpty()) {
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

                    // IMPORTANT: this is what HomeProfileAdapter uses
                    profile.setPhotoUrl(imageUrl);

                    profile.setVerified(verified != null && verified);
                    profile.setDistanceKm(distance);
                    profile.setInterests(interests);
                    // You can set matchPercent later if you compute it
                    // profile.setMatchPercent(...);

                    result.add(profile);
                }

                adapter.setProfiles(result);
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
}
