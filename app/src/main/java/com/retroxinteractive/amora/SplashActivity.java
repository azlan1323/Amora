package com.retroxinteractive.amora;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

public class SplashActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    ImageView ivSplashLogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        init();

        new Handler(Looper.getMainLooper()).postDelayed(this::checkUserStatus, 3000);
    }

    private void init() {
        mAuth = FirebaseAuth.getInstance();

        ivSplashLogo = findViewById(R.id.ivSplashLogo);

        Animation animation = AnimationUtils.loadAnimation(this, R.anim.anim_splash);
        ivSplashLogo.startAnimation(animation);
    }

    private void checkUserStatus() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // 1. User NOT logged in → go to Onboarding
        if (currentUser == null) {
            startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
            finish();
            return;
        }

        refreshFcmTokenForCurrentUser();

        // 2. User is logged in → Now check if profile is completed
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUser.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (!snapshot.exists()) {
                    // No profile yet → go to ProfileDetailsActivity
                    goToProfileDetails();
                    return;
                }

                // Assuming you have a boolean "profileCompleted" stored in DB
                Boolean isCompleted = snapshot.child("profileCompleted").getValue(Boolean.class);

                if (isCompleted != null && isCompleted) {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    finish();
                } else {
                    goToProfileDetails();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // In case of error, force user to fill profile
                goToProfileDetails();
            }
        });
    }

    // Call this after successful login
    public static void refreshFcmTokenForCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) return;
                    String token = task.getResult();
                    FirebaseDatabase.getInstance().getReference("users")
                            .child(user.getUid())
                            .child("fcmToken")
                            .setValue(token);
                });
    }

    private void goToProfileDetails() {
        startActivity(new Intent(SplashActivity.this, ProfileDetailsActivity.class));
        finish();
    }
}