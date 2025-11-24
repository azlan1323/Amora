package com.retroxinteractive.amora;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfileFragment extends Fragment {

    private static final String ARG_FROM_MAIN_PROFILE = "from_main_profile";

    private boolean openedFromMainProfile = false;

    private TextView tvNameAge;
    private TextView tvBio;
    private TextView tvDistance;

    private ImageView imgProfilePhoto;
    private View containerDistance;
    private ImageButton btnAddPhoto;

    private ImageView imgPhoto1;
    private ImageView imgPhoto2;

    // interest pills
    private TextView tvInterest1, tvInterest2, tvInterest3,
            tvInterest4, tvInterest5, tvInterest6;

    private DatabaseReference userRef;
    private String currentUid;

    private ActivityResultLauncher<String> pickImagesLauncher;

    public ProfileFragment() { }

    public static ProfileFragment newInstance(boolean fromMainProfile) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_FROM_MAIN_PROFILE, fromMainProfile);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            openedFromMainProfile = getArguments().getBoolean(ARG_FROM_MAIN_PROFILE, false);
        }

        // launcher for multiple images
        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;
                    uploadSelectedImages(uris);
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvNameAge   = view.findViewById(R.id.tv_name_age);
        tvBio       = view.findViewById(R.id.tv_bio_description);
        tvDistance  = view.findViewById(R.id.tv_distance);

        imgProfilePhoto = view.findViewById(R.id.img_profile_photo);
        containerDistance = view.findViewById(R.id.container_distance);
        btnAddPhoto = view.findViewById(R.id.btn_add_photo);

        imgPhoto1   = view.findViewById(R.id.img_photo_1);
        imgPhoto2   = view.findViewById(R.id.img_photo_2);

        tvInterest1 = view.findViewById(R.id.tv_interest_actress);
        tvInterest2 = view.findViewById(R.id.tv_interest_modeling);
        tvInterest3 = view.findViewById(R.id.tv_interest_art);
        tvInterest4 = view.findViewById(R.id.tv_interest_travel);
        tvInterest5 = view.findViewById(R.id.tv_interest_music);
        tvInterest6 = view.findViewById(R.id.tv_interest_painting);

        // behaviour when opened from main profile icon
        if (openedFromMainProfile) {
            if (containerDistance != null) containerDistance.setVisibility(View.GONE);
            if (btnAddPhoto != null) {
                btnAddPhoto.setVisibility(View.VISIBLE);
                btnAddPhoto.setOnClickListener(v -> {
                    if (pickImagesLauncher != null) {
                        pickImagesLauncher.launch("image/*");
                    }
                });
            }
        } else {
            if (btnAddPhoto != null) btnAddPhoto.setVisibility(View.GONE);
        }

        loadUserProfile();
        return view;
    }

    // ------------------ LOAD PROFILE ------------------

    private void loadUserProfile() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        currentUid = firebaseUser.getUid();
        userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(),
                                "Profile not found in database",
                                Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                String name = snapshot.child("name").getValue(String.class);
                String bio = snapshot.child("bio").getValue(String.class);
                String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                if (!TextUtils.isEmpty(name)) {
                    tvNameAge.setText(name);
                }
                if (!TextUtils.isEmpty(bio)) {
                    tvBio.setText(bio);
                }

                // header avatar + first photo from profileImageUrl
                if (!TextUtils.isEmpty(profileImageUrl) && getContext() != null) {
                    Glide.with(ProfileFragment.this)
                            .load(profileImageUrl)
                            .into(imgProfilePhoto);

                    Glide.with(ProfileFragment.this)
                            .load(profileImageUrl)
                            .into(imgPhoto1);
                }

                // interests
                List<String> interests = new ArrayList<>();
                DataSnapshot interestsSnap = snapshot.child("interests");
                for (DataSnapshot child : interestsSnap.getChildren()) {
                    String interest = child.getValue(String.class);
                    if (!TextUtils.isEmpty(interest)) {
                        interests.add(interest);
                    }
                }
                applyInterestsToViews(interests);

                // extra photos under users/{uid}/photos
                List<String> photos = new ArrayList<>();
                DataSnapshot photosSnap = snapshot.child("photos");
                for (DataSnapshot child : photosSnap.getChildren()) {
                    String url = child.getValue(String.class);
                    if (!TextUtils.isEmpty(url)) {
                        photos.add(url);
                    }
                }
                applyPhotosToViews(profileImageUrl, photos);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "Failed to load profile: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

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

    private void applyPhotosToViews(@Nullable String profileImageUrl,
                                    @NonNull List<String> photos) {

        if (getContext() == null) return;

        List<String> all = new ArrayList<>();
        if (!TextUtils.isEmpty(profileImageUrl)) {
            all.add(profileImageUrl);
        }
        all.addAll(photos);

        if (all.size() > 0) {
            Glide.with(this).load(all.get(0)).into(imgPhoto1);
        }
        if (all.size() > 1) {
            Glide.with(this).load(all.get(1)).into(imgPhoto2);
        }
    }

    // ------------------ UPLOAD PHOTOS ------------------

    private void uploadSelectedImages(@NonNull List<Uri> uris) {
        if (getContext() == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        DatabaseReference userRefLocal = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);

        StorageReference storageRoot = FirebaseStorage.getInstance()
                .getReference()
                .child("user_photos")
                .child(uid);

        for (Uri uri : uris) {
            if (uri == null) continue;

            String fileName = System.currentTimeMillis() + "_" +
                    (uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "photo.jpg");

            StorageReference photoRef = storageRoot.child(fileName);

            photoRef.putFile(uri)
                    .addOnSuccessListener(taskSnapshot ->
                            photoRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                                userRefLocal.child("photos").push()
                                        .setValue(downloadUri.toString());
                                loadUserProfile(); // refresh
                            }))
                    .addOnFailureListener(e -> {
                        if (getContext() != null) {
                            Toast.makeText(getContext(),
                                    "Failed to upload photo: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }
}
