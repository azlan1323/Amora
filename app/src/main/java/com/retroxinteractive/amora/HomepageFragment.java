package com.retroxinteractive.amora;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import com.retroxinteractive.amora.R;

import java.util.ArrayList;
import java.util.List;

public class HomepageFragment extends Fragment {

    private RecyclerView rvProfiles;
    private HomeProfileAdapter adapter;

    private DatabaseReference usersRef;
    private ValueEventListener usersListener;
    private FirebaseUser currentUser;

    private final List<UserProfile> profiles = new ArrayList<>();

    public HomepageFragment() {
        // Required empty public constructor
    }

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

        adapter = new HomeProfileAdapter(requireContext());
        rvProfiles.setAdapter(adapter);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        attachUsersListener();
    }

    private void attachUsersListener() {
        if (usersListener != null) {
            // already attached
            return;
        }

        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                profiles.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    UserProfile profile = child.getValue(UserProfile.class);
                    if (profile == null) continue;

                    // Make sure uid is set even if not in JSON
                    if (profile.getUid() == null) {
                        profile.setUid(child.getKey());
                    }

                    // Skip current logged-in user in the list (optional)
                    if (currentUser != null && profile.getUid() != null &&
                            profile.getUid().equals(currentUser.getUid())) {
                        continue;
                    }

                    profiles.add(profile);
                }

                adapter.setProfiles(profiles);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // You can log or show a Toast here
                // Log.e("HomepageFragment", "Failed to load profiles", error.toException());
            }
        };

        usersRef.addValueEventListener(usersListener);
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