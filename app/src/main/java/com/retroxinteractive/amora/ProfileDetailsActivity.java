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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileDetailsActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private EditText etName, etAge, etBio, etAddress;
    private ChipGroup chipGroupInterests;
    private ImageView imgProfile;
    private MaterialButton btnSave;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    private StorageReference storageRef;
    private Uri selectedImageUri = null;
    private String existingImageUrl = null;

    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private boolean hasLocation = false;
    String cordsText;

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
        storageRef = FirebaseStorage.getInstance().getReference().child("profile_images");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgProfile.setImageURI(uri);
                    }
                }
        );

        initViews();
        loadExistingProfile();
    }


    private void initViews() {
        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        etAddress = findViewById(R.id.etAddress);
        etBio = findViewById(R.id.etBio);
        chipGroupInterests = findViewById(R.id.chipGroupInterests);
        imgProfile = findViewById(R.id.imgProfile);
        btnSave = findViewById(R.id.btnSaveProfile);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);

        imgProfile.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        etAddress.setOnClickListener(v -> fetchLocationForAddressField());

        btnSave.setOnClickListener(v -> saveProfile());
        btnCancel.setOnClickListener((v)-> finish());
    }


    private void loadExistingProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        usersRef.child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        String name = snapshot.child("name").getValue(String.class);
                        String age = snapshot.child("age").getValue(String.class);
                        String bio = snapshot.child("bio").getValue(String.class);
                        String address = snapshot.child("address").getValue(String.class);
                        existingImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                        if (name != null) etName.setText(name);
                        if (age != null) etAge.setText(age);
                        if (bio != null) etBio.setText(bio);
                        if (address != null) etAddress.setText(address);

                        List<String> interests = new ArrayList<>();
                        for (DataSnapshot child : snapshot.child("interests").getChildren()) {
                            String val = child.getValue(String.class);
                            if (val != null) interests.add(val);
                        }

                        for (int i = 0; i < chipGroupInterests.getChildCount(); i++) {
                            Chip chip = (Chip) chipGroupInterests.getChildAt(i);
                            if (interests.contains(chip.getText().toString())) {
                                chip.setChecked(true);
                            }
                        }

                        if (existingImageUrl != null && !existingImageUrl.isEmpty()) {
                            Glide.with(ProfileDetailsActivity.this)
                                    .load(existingImageUrl)
                                    .into(imgProfile);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }


    private void saveProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not signed in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName.getText().toString().trim();
        String age = etAge.getText().toString().trim();
        String bio = etBio.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (TextUtils.isEmpty(name)) { etName.setError("Name required"); return; }
        if (TextUtils.isEmpty(age)) { etAge.setError("Age required"); return; }
        if (TextUtils.isEmpty(bio)) { etBio.setError("Bio required"); return; }

        List<String> interests = new ArrayList<>();
        for (int chipId : chipGroupInterests.getCheckedChipIds()) {
            Chip chip = chipGroupInterests.findViewById(chipId);
            if (chip != null) interests.add(chip.getText().toString());
        }

        if (interests.isEmpty()) {
            Toast.makeText(this, "Select at least one interest.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(address)) {
            etAddress.setError("Address required");
            return;
        }

        if (selectedImageUri == null &&
                (existingImageUrl == null || existingImageUrl.isEmpty())) {
            Toast.makeText(this, "Please choose a profile picture.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);

        getLocationAndSaveProfile(user, name, age, bio, address, interests);
    }


    private void fetchLocationForAddressField() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST
            );
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        hasLocation = true;
                        cordsText = currentLatitude + ", " + currentLongitude;
                    } else {
                        Toast.makeText(this,
                                "Could not get location. Turn on GPS.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Failed to get location: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }


    private void getLocationAndSaveProfile(FirebaseUser user,
                                           String name,
                                           String age,
                                           String bio,
                                           String address,
                                           List<String> interests) {

        if (!hasLocation) {
            Toast.makeText(this,
                    "Tap the Address field first to fetch your GPS location.",
                    Toast.LENGTH_SHORT).show();
            btnSave.setEnabled(true);
            return;
        }

        proceedWithImageAndDatabase(
                user, name, age, bio, address, interests,
                currentLatitude, currentLongitude
        );
    }


    private void proceedWithImageAndDatabase(FirebaseUser user,
                                             String name,
                                             String age,
                                             String bio,
                                             String address,
                                             List<String> interests,
                                             double lat,
                                             double lng) {

        if (selectedImageUri != null) {
            uploadImageAndSaveProfile(user, name, age, bio, address, interests, lat, lng);
        } else {
            writeProfileToDatabase(user, name, age, bio, address, interests, lat, lng, existingImageUrl);
        }
    }


    private void uploadImageAndSaveProfile(FirebaseUser user,
                                           String name,
                                           String age,
                                           String bio,
                                           String address,
                                           List<String> interests,
                                           double lat,
                                           double lng) {

        StorageReference userImageRef = storageRef
                .child(user.getUid())
                .child("profile.jpg");

        userImageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot ->
                        userImageRef.getDownloadUrl().addOnSuccessListener(uri ->
                                writeProfileToDatabase(
                                        user, name, age, bio, address,
                                        interests, lat, lng, uri.toString()
                                )))
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            "Image upload failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }


    private void writeProfileToDatabase(FirebaseUser user,
                                        String name,
                                        String age,
                                        String bio,
                                        String address,
                                        List<String> interests,
                                        double latitude,
                                        double longitude,
                                        String imageUrl) {

        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put("name", name);
        profileMap.put("age", age);
        profileMap.put("address", address);
        profileMap.put("bio", bio);
        profileMap.put("interests", interests);
        profileMap.put("profileCompleted", true);
        profileMap.put("profileImageUrl", imageUrl == null ? "" : imageUrl);
        profileMap.put("latitude", latitude);
        profileMap.put("longitude", longitude);

        usersRef.child(user.getUid())
                .updateChildren(profileMap)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this,
                            "Profile saved!", Toast.LENGTH_SHORT).show();

                    goToMainActivity();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            "Failed to save profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }


    private void goToMainActivity() {
        Intent intent = new Intent(ProfileDetailsActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fetchLocationForAddressField();
            } else {
                Toast.makeText(this,
                        "Location permission required for coordinates.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
