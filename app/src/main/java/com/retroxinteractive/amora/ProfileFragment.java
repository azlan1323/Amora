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

    // true = opened from MainActivity (own profile)
    private boolean openedFromMainProfile = false;
    // uid of user whose profile we are viewing (null = current user)
    private String viewedUserId;

    private TextView tvNameAge, tvBio, tvDistance;
    private ImageView imgProfilePhoto;
    private View containerDistance, bottomActionBar;
    private ImageButton btnAddPhoto;
    private LinearLayout photosContainer;

    // interest pills
    private TextView tvInterest1, tvInterest2, tvInterest3,
            tvInterest4, tvInterest5, tvInterest6;

    private DatabaseReference userRef;
    private String currentUid;

    private ActivityResultLauncher<String> pickImagesLauncher;

    public ProfileFragment() {
        // Required empty public constructor
    }

    // Used for own profile from MainActivity tab
    public static ProfileFragment newInstance(boolean fromMainProfile) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_FROM_MAIN_PROFILE, fromMainProfile);
        fragment.setArguments(args);
        return fragment;
    }

    // Used from DiscoverFragment: newInstance(profile.uid, null)
    public static ProfileFragment newInstance(@NonNull String userId, @Nullable String ignored) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        // viewing other user, not main profile
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

        // image picker for multiple photos (only used for own profile)
        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        uploadSelectedImages(uris);
                    }
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // If this was an "other user's" profile, restore the bottom nav
        if (!openedFromMainProfile && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisible(true);
        }
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

        // Back button in top bar
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        // UI behaviour based on whether this is my profile or someone else's
        if (openedFromMainProfile) {
            // Own profile
            if (containerDistance != null) {
                containerDistance.setVisibility(View.GONE);
            }
            if (btnAddPhoto != null) {
                btnAddPhoto.setVisibility(View.VISIBLE);
                btnAddPhoto.setOnClickListener(v -> {
                    if (pickImagesLauncher != null) {
                        pickImagesLauncher.launch("image/*");
                    }
                });
            }
            if (bottomActionBar != null) {
                bottomActionBar.setVisibility(View.GONE);
            }
        } else {
            // Viewing someone else
            if (containerDistance != null) {
                containerDistance.setVisibility(View.VISIBLE);
            }
            if (btnAddPhoto != null) {
                btnAddPhoto.setVisibility(View.GONE);
            }
            if (bottomActionBar != null) {
                bottomActionBar.setVisibility(View.VISIBLE);
            }
        }

        loadUserProfile();
        return view;
    }

    // ------------------ LOAD PROFILE ------------------
    private void loadUserProfile() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        String authUid = firebaseUser.getUid();

        // Decide whose profile to show
        if (openedFromMainProfile || TextUtils.isEmpty(viewedUserId)) {
            currentUid = authUid;
        } else {
            currentUid = viewedUserId;
        }

        userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (!snapshot.exists()) return;

                String name = snapshot.child("name").getValue(String.class);
                String bio = snapshot.child("bio").getValue(String.class);
                String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                if (!TextUtils.isEmpty(name)) {
                    tvNameAge.setText(name);
                }
                if (!TextUtils.isEmpty(bio)) {
                    tvBio.setText(bio);
                }

                // fullscreen header image
                if (!TextUtils.isEmpty(profileImageUrl) && getContext() != null) {
                    Glide.with(ProfileFragment.this)
                            .load(profileImageUrl)
                            .centerCrop()
                            .into(imgProfilePhoto);
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

                // extra photos
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
            if (i < interests.size()) {
                pill.setText(interests.get(i));
                pill.setVisibility(View.VISIBLE);
            } else {
                pill.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Creates rows of 2 images each and fills them with all available photo URLs.
     * First we include profileImageUrl (if present), then all extra photos.
     */
    private void applyPhotosToViews(@Nullable String profileImageUrl,
                                    @NonNull List<String> photos) {
        if (photosContainer == null || getContext() == null) return;

        List<String> all = new ArrayList<>();
        if (!TextUtils.isEmpty(profileImageUrl)) {
            all.add(profileImageUrl);
        }
        all.addAll(photos);

        photosContainer.removeAllViews();
        if (all.isEmpty()) return;

        Context ctx = getContext();
        int imageHeight = dpToPx(140);
        int imageMargin = dpToPx(8);

        for (int i = 0; i < all.size(); i += 2) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            if (i > 0) {
                rowParams.topMargin = imageMargin;
            }
            row.setLayoutParams(rowParams);

            // first image in row
            String url1 = all.get(i);
            ImageView iv1 = createPhotoImageView(ctx, imageHeight, imageMargin, true);
            Glide.with(this).load(url1).centerCrop().into(iv1);
            row.addView(iv1);

            // second image in row (if exists)
            if (i + 1 < all.size()) {
                String url2 = all.get(i + 1);
                ImageView iv2 = createPhotoImageView(ctx, imageHeight, imageMargin, false);
                Glide.with(this).load(url2).centerCrop().into(iv2);
                row.addView(iv2);
            } else {
                // spacer for alignment
                View spacer = new View(ctx);
                LinearLayout.LayoutParams spacerParams =
                        new LinearLayout.LayoutParams(0, imageHeight, 1f);
                spacerParams.leftMargin = imageMargin;
                spacer.setLayoutParams(spacerParams);
                row.addView(spacer);
            }

            photosContainer.addView(row);
        }
    }

    private ImageView createPhotoImageView(Context ctx, int heightPx,
                                           int marginPx, boolean isLeft) {
        ImageView iv = new ImageView(ctx);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, heightPx, 1f);
        if (isLeft) {
            lp.rightMargin = marginPx;
        } else {
            lp.leftMargin = marginPx;
        }
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundResource(R.drawable.profile_bg);
        return iv;
    }

    // ------------------ UPLOAD PHOTOS (only for own profile) ------------------
    private void uploadSelectedImages(@NonNull List<Uri> uris) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || getContext() == null) return;

        String uid = user.getUid();
        DatabaseReference userRefLocal =
                FirebaseDatabase.getInstance().getReference("users").child(uid);

        StorageReference storageRoot =
                FirebaseStorage.getInstance().getReference("user_photos").child(uid);

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
                                loadUserProfile(); // refresh grid
                            }))
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Failed to upload photo: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        }
    }

    // ------------------ UTIL ------------------
    private int dpToPx(int dp) {
        if (getResources() == null) return dp;
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
