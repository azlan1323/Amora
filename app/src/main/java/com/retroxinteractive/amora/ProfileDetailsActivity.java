package com.retroxinteractive.amora;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileDetailsActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;   // NEW

    private EditText etName, etAddress, etBio;
    private ChipGroup chipGroupInterests;
    private ImageView imgProfile;
    private MaterialButton btnSave;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    // storage + image handling
    private StorageReference storageRef;
    private Uri selectedImageUri = null;
    private String existingImageUrl = null;

    // location
    private FusedLocationProviderClient fusedLocationClient;       // NEW

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_details);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("profile_images");

        // NEW: location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // image picker
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgProfile.setImageURI(uri); // preview
                    }
                }
        );

        initViews();
        loadExistingProfile();   // prefill form if data exists
    }

    private void initViews() {
        etName = findViewById(R.id.etName);
        etAddress = findViewById(R.id.etAddress);  // still bound, but no longer used
        etBio = findViewById(R.id.etBio);
        chipGroupInterests = findViewById(R.id.chipGroupInterests);
        imgProfile = findViewById(R.id.imgProfile);
        btnSave = findViewById(R.id.btnSaveProfile);

        imgProfile.setOnClickListener(v ->
                imagePickerLauncher.launch("image/*")
        );
        btnSave.setOnClickListener(v -> saveProfile());
    }

    // load any existing data (partial or full) and prefill
    private void loadExistingProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        usersRef.child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        String name = snapshot.child("name").getValue(String.class);
                        // String address = snapshot.child("address").getValue(String.class); // REMOVED
                        String bio = snapshot.child("bio").getValue(String.class);
                        existingImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                        if (name != null) etName.setText(name);
                        // if (address != null) etAddress.setText(address); // REMOVED
                        if (bio != null) etBio.setText(bio);

                        // Interests (stored as a list of strings)
                        List<String> interests = new ArrayList<>();
                        for (DataSnapshot child : snapshot.child("interests").getChildren()) {
                            String val = child.getValue(String.class);
                            if (val != null) interests.add(val);
                        }
                        if (!interests.isEmpty()) {
                            for (int i = 0; i < chipGroupInterests.getChildCount(); i++) {
                                Chip chip = (Chip) chipGroupInterests.getChildAt(i);
                                if (interests.contains(chip.getText().toString())) {
                                    chip.setChecked(true);
                                }
                            }
                        }

                        // Load image if present
                        if (existingImageUrl != null && !existingImageUrl.isEmpty()) {
                            Glide.with(ProfileDetailsActivity.this)
                                    .load(existingImageUrl)
                                    .into(imgProfile);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Optional: show a toast/log
                    }
                });
    }

    private void saveProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not signed in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName.getText().toString().trim();
        // String address = etAddress.getText().toString().trim(); // REMOVED
        String bio = etBio.getText().toString().trim();

        // VALIDATION
        if (TextUtils.isEmpty(name)) {
            etName.setError("Name required");
            etName.requestFocus();
            return;
        }

        // REMOVED: address validation (we now use GPS)
        // if (TextUtils.isEmpty(address)) { ... }

        if (TextUtils.isEmpty(bio)) {
            etBio.setError("Bio required");
            etBio.requestFocus();
            return;
        }

        List<String> interests = new ArrayList<>();
        for (int chipId : chipGroupInterests.getCheckedChipIds()) {
            Chip chip = chipGroupInterests.findViewById(chipId);
            if (chip != null) interests.add(chip.getText().toString());
        }

        if (interests.isEmpty()) {
            Toast.makeText(this, "Select at least one interest.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Require at least one image (new or existing)
        if (selectedImageUri == null &&
                (existingImageUrl == null || existingImageUrl.isEmpty())) {
            Toast.makeText(this, "Please choose a profile picture.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);

        // NEW: get location first, then upload/save profile
        getLocationAndSaveProfile(user, name, bio, interests);
    }

    /**
     * Fetch device location and then continue with image upload + DB write.
     */
    private void getLocationAndSaveProfile(FirebaseUser user,
                                           String name,
                                           String bio,
                                           List<String> interests) {

        // Permission check
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST
            );

            Toast.makeText(this,
                    "Please grant location permission and tap Save again.",
                    Toast.LENGTH_LONG).show();

            btnSave.setEnabled(true);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    double lat = 0.0;
                    double lng = 0.0;

                    if (location != null) {
                        lat = location.getLatitude();
                        lng = location.getLongitude();
                    } else {
                        Toast.makeText(this,
                                "Could not get location, using 0,0.",
                                Toast.LENGTH_SHORT).show();
                    }

                    // Now we have (lat,lng) -> continue with image upload / DB
                    proceedWithImageAndDatabase(user, name, bio, interests, lat, lng);
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            "Failed to get location: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * After we have coordinates, handle image upload and DB write.
     */
    private void proceedWithImageAndDatabase(FirebaseUser user,
                                             String name,
                                             String bio,
                                             List<String> interests,
                                             double lat,
                                             double lng) {

        if (selectedImageUri != null) {
            uploadImageAndSaveProfile(user, name, bio, interests, lat, lng);
        } else {
            writeProfileToDatabase(user, name, bio, interests, lat, lng, existingImageUrl);
        }
    }

    // upload selected image to Firebase Storage
    private void uploadImageAndSaveProfile(FirebaseUser user,
                                           String name,
                                           String bio,
                                           List<String> interests,
                                           double lat,
                                           double lng) {

        // path: profile_images/<uid>/profile.jpg
        StorageReference userImageRef = storageRef
                .child(user.getUid())
                .child("profile.jpg");

        UploadTask uploadTask = userImageRef.putFile(selectedImageUri);
        uploadTask
                .addOnSuccessListener(taskSnapshot ->
                        userImageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String downloadUrl = uri.toString();
                            writeProfileToDatabase(user, name, bio, interests, lat, lng, downloadUrl);
                        }))
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(ProfileDetailsActivity.this,
                            "Image upload failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // write all data (including image URL & coordinates) to Realtime DB
    private void writeProfileToDatabase(FirebaseUser user,
                                        String name,
                                        String bio,
                                        List<String> interests,
                                        double latitude,
                                        double longitude,
                                        String imageUrl) {

        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put("name", name);
        // profileMap.put("address", address); // REMOVED
        profileMap.put("bio", bio);
        profileMap.put("interests", interests);
        profileMap.put("profileCompleted", true);
        profileMap.put("profileImageUrl", imageUrl == null ? "" : imageUrl);

        // NEW: store coordinates
        profileMap.put("latitude", latitude);
        profileMap.put("longitude", longitude);

        usersRef.child(user.getUid())
                .updateChildren(profileMap)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(ProfileDetailsActivity.this,
                            "Profile saved!", Toast.LENGTH_SHORT).show();
                    goToMainActivity();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(ProfileDetailsActivity.this,
                            "Failed to save profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void goToMainActivity() {
        Intent intent = new Intent(ProfileDetailsActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    // Optional: you can handle permission result if you want auto-retry,
    // but right now user just taps Save again after granting permission.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // No need to do anything here for the simple flow we implemented.
    }
}
