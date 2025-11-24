package com.retroxinteractive.amora;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private static final String ARG_USER_ID = "user_id";

    private boolean openedFromMainProfile = false;
    private String viewedUserId;    // Target profile being viewed
    private String currentUid;      // UID used in DB query

    private TextView tvNameAge, tvBio, tvDistance;
    private ImageView imgProfilePhoto;
    private View containerDistance, bottomActionBar;
    private ImageButton btnAddPhoto;
    private LinearLayout photosContainer;

    private TextView tvInterest1, tvInterest2, tvInterest3,
            tvInterest4, tvInterest5, tvInterest6;

    private DatabaseReference userRef;
    private ActivityResultLauncher<String> pickImagesLauncher;

    public ProfileFragment() { }

    /** Load own profile */
    public static ProfileFragment newInstance(boolean fromMainProfile) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_FROM_MAIN_PROFILE, fromMainProfile);
        fragment.setArguments(args);
        return fragment;
    }

    /** Load someone else's profile */
    public static ProfileFragment newInstanceForUser(@NonNull String userId) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_FROM_MAIN_PROFILE, false);
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            openedFromMainProfile = getArguments().getBoolean(ARG_FROM_MAIN_PROFILE, false);
            viewedUserId = getArguments().getString(ARG_USER_ID);
        }

        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) uploadSelectedImages(uris);
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvNameAge = view.findViewById(R.id.tv_name_age);
        tvBio = view.findViewById(R.id.tv_bio_description);
        tvDistance = view.findViewById(R.id.tv_distance);

        imgProfilePhoto = view.findViewById(R.id.img_profile_photo);

        containerDistance = view.findViewById(R.id.container_distance);
        bottomActionBar = view.findViewById(R.id.bottom_bar);
        btnAddPhoto = view.findViewById(R.id.btn_add_photo);

        photosContainer = view.findViewById(R.id.photos_container);

        tvInterest1 = view.findViewById(R.id.tv_interest_actress);
        tvInterest2 = view.findViewById(R.id.tv_interest_modeling);
        tvInterest3 = view.findViewById(R.id.tv_interest_art);
        tvInterest4 = view.findViewById(R.id.tv_interest_travel);
        tvInterest5 = view.findViewById(R.id.tv_interest_music);
        tvInterest6 = view.findViewById(R.id.tv_interest_painting);

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed()
        );

        // Behavior for own vs other profile
        if (openedFromMainProfile) {
            containerDistance.setVisibility(View.GONE);
            bottomActionBar.setVisibility(View.GONE);

            btnAddPhoto.setVisibility(View.VISIBLE);
            btnAddPhoto.setOnClickListener(v -> pickImagesLauncher.launch("image/*"));
        } else {
            containerDistance.setVisibility(View.VISIBLE);
            bottomActionBar.setVisibility(View.VISIBLE);
            btnAddPhoto.setVisibility(View.GONE);
        }

        loadUserProfile();
        return view;
    }

    // ---------------- LOAD PROFILE ------------------
    private void loadUserProfile() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        String authUid = firebaseUser.getUid();

        // Determine whose profile to load
        if (openedFromMainProfile || TextUtils.isEmpty(viewedUserId)) {
            currentUid = authUid;
        } else {
            currentUid = viewedUserId;
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (!snapshot.exists()) return;

                String name = snapshot.child("name").getValue(String.class);
                String bio = snapshot.child("bio").getValue(String.class);
                String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                if (!TextUtils.isEmpty(name)) tvNameAge.setText(name);
                if (!TextUtils.isEmpty(bio)) tvBio.setText(bio);

                // full header image
                if (!TextUtils.isEmpty(profileImageUrl)) {
                    Glide.with(ProfileFragment.this)
                            .load(profileImageUrl)
                            .centerCrop()
                            .into(imgProfilePhoto);
                }

                // interests
                List<String> interests = new ArrayList<>();
                for (DataSnapshot s : snapshot.child("interests").getChildren()) {
                    String i = s.getValue(String.class);
                    if (!TextUtils.isEmpty(i)) interests.add(i);
                }
                applyInterestsToViews(interests);

                // photos
                List<String> photos = new ArrayList<>();
                for (DataSnapshot s : snapshot.child("photos").getChildren()) {
                    String url = s.getValue(String.class);
                    if (!TextUtils.isEmpty(url)) photos.add(url);
                }

                applyPhotosToViews(profileImageUrl, photos);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void applyInterestsToViews(List<String> interests) {
        List<TextView> pills = Arrays.asList(
                tvInterest1, tvInterest2, tvInterest3,
                tvInterest4, tvInterest5, tvInterest6
        );

        for (int i = 0; i < pills.size(); i++) {
            if (i < interests.size()) {
                pills.get(i).setText(interests.get(i));
                pills.get(i).setVisibility(View.VISIBLE);
            } else {
                pills.get(i).setVisibility(View.GONE);
            }
        }
    }

    // ---------------- DYNAMIC PHOTOS GRID ------------------
    private void applyPhotosToViews(String profileImageUrl, List<String> photos) {
        if (photosContainer == null || getContext() == null) return;

        List<String> all = new ArrayList<>();
        if (!TextUtils.isEmpty(profileImageUrl)) all.add(profileImageUrl);
        all.addAll(photos);

        photosContainer.removeAllViews();
        if (all.isEmpty()) return;

        Context ctx = getContext();
        int imageH = dpToPx(140), margin = dpToPx(8);

        for (int i = 0; i < all.size(); i += 2) {

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            if (i > 0) rowParams.topMargin = margin;
            row.setLayoutParams(rowParams);

            // left image
            ImageView iv1 = createPhotoImage(ctx, imageH, margin, true);
            Glide.with(this).load(all.get(i)).centerCrop().into(iv1);
            row.addView(iv1);

            // right image
            if (i + 1 < all.size()) {
                ImageView iv2 = createPhotoImage(ctx, imageH, margin, false);
                Glide.with(this).load(all.get(i + 1)).centerCrop().into(iv2);
                row.addView(iv2);
            } else {
                // spacer
                View space = new View(ctx);
                LinearLayout.LayoutParams sp =
                        new LinearLayout.LayoutParams(0, imageH, 1f);
                sp.leftMargin = margin;
                space.setLayoutParams(sp);
                row.addView(space);
            }

            photosContainer.addView(row);
        }
    }

    private ImageView createPhotoImage(Context ctx, int height, int margin, boolean isLeft) {
        ImageView iv = new ImageView(ctx);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, height, 1f);

        if (isLeft) lp.rightMargin = margin;
        else lp.leftMargin = margin;

        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundResource(R.drawable.profile_bg);
        return iv;
    }

    // ---------------- UPLOAD EXTRA PHOTOS ------------------
    private void uploadSelectedImages(List<Uri> uris) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        DatabaseReference userRefLocal =
                FirebaseDatabase.getInstance().getReference("users").child(uid);

        StorageReference storageRoot =
                FirebaseStorage.getInstance().getReference("user_photos").child(uid);

        for (Uri uri : uris) {
            if (uri == null) continue;

            String name = System.currentTimeMillis() + ".jpg";
            StorageReference fileRef = storageRoot.child(name);

            fileRef.putFile(uri)
                    .addOnSuccessListener(t ->
                            fileRef.getDownloadUrl().addOnSuccessListener(url -> {
                                userRefLocal.child("photos").push().setValue(url.toString());
                                loadUserProfile();
                            })
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Upload failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show()
                    );
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
