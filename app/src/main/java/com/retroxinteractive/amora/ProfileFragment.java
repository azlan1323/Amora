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

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfileFragment extends Fragment {

    private TextView tvNameAge;
    private TextView tvBio;
    private TextView tvDistance;

    private ImageView imgPhoto1;
    private ImageView imgPhoto2;

    // interest “pills” in your XML
    private TextView tvInterest1;
    private TextView tvInterest2;
    private TextView tvInterest3;
    private TextView tvInterest4;
    private TextView tvInterest5;
    private TextView tvInterest6;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // --- bind views from your XML ---
        tvNameAge   = view.findViewById(R.id.tv_name_age);
        tvBio       = view.findViewById(R.id.tv_bio_description);
        tvDistance  = view.findViewById(R.id.tv_distance);

        imgPhoto1   = view.findViewById(R.id.img_photo_1);
        imgPhoto2   = view.findViewById(R.id.img_photo_2);

        tvInterest1 = view.findViewById(R.id.tv_interest_actress);
        tvInterest2 = view.findViewById(R.id.tv_interest_modeling);
        tvInterest3 = view.findViewById(R.id.tv_interest_art);
        tvInterest4 = view.findViewById(R.id.tv_interest_travel);
        tvInterest5 = view.findViewById(R.id.tv_interest_music);
        tvInterest6 = view.findViewById(R.id.tv_interest_painting);

        loadUserProfile();

        return view;
    }

    private void loadUserProfile() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = firebaseUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(getContext(),
                            "Profile not found in database",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String name = snapshot.child("name").getValue(String.class);
                String bio = snapshot.child("bio").getValue(String.class);
                String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                // We only have name (no age yet), so show just name
                if (!TextUtils.isEmpty(name)) {
                    tvNameAge.setText(name); // e.g. "Azlan Shahid"
                }

                if (!TextUtils.isEmpty(bio)) {
                    tvBio.setText(bio);
                }

                // Distance – keep XML default for now (0.7 km),
                // you can replace here once you calculate real distance.
                // tvDistance.setText("0.7 km");

                // Load the profile image into the photos section
                if (!TextUtils.isEmpty(profileImageUrl) && getContext() != null) {
                    Glide.with(ProfileFragment.this)
                            .load(profileImageUrl)
                            .into(imgPhoto1);

                    // For now reuse for second photo as well
                    Glide.with(ProfileFragment.this)
                            .load(profileImageUrl)
                            .into(imgPhoto2);
                }

                // ---- Interests from DB -> 6 text views ----
                List<String> interests = new ArrayList<>();
                DataSnapshot interestsSnap = snapshot.child("interests");
                for (DataSnapshot child : interestsSnap.getChildren()) {
                    String interest = child.getValue(String.class);
                    if (!TextUtils.isEmpty(interest)) {
                        interests.add(interest);
                    }
                }

                applyInterestsToViews(interests);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(),
                        "Failed to load profile: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Fills the six interest pills with the user’s interests.
     * If fewer than 6 interests, extra pills are hidden.
     */
    private void applyInterestsToViews(@NonNull List<String> interests) {
        List<TextView> pills = Arrays.asList(
                tvInterest1, tvInterest2, tvInterest3,
                tvInterest4, tvInterest5, tvInterest6
        );

        for (int i = 0; i < pills.size(); i++) {
            TextView pill = pills.get(i);
            if (pill == null) continue;

            if (i < interests.size()) {
                pill.setText(interests.get(i));
                pill.setVisibility(View.VISIBLE);
            } else {
                pill.setVisibility(View.GONE);
            }
        }
    }
}
