package com.retroxinteractive.amora;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.Intent;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.material.button.MaterialButton;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SignInActivity extends AppCompatActivity {
    private static final String TAG = "SignInActivity";

    private FirebaseAuth mAuth;
    private CredentialManager credentialManager;
    private GetCredentialRequest getCredentialRequest;

    private MaterialButton btnGoogleSignIn;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        init();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        checkUserStatus(currentUser);
    }

    private void init() {
        // Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Credential Manager
        credentialManager = CredentialManager.create(this);

        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressBar      = findViewById(R.id.progressBar);

        buildGoogleSignInRequest();

        btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
    }

    /**
     * Build the GetCredentialRequest using GetGoogleIdOption
     * (from the new Sign in with Google / Credential Manager flow)
     */
    private void buildGoogleSignInRequest() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                // Allow the user to select from any of their Google accounts
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        // Create the Credential Manager request
        getCredentialRequest = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();
    }

    private void startGoogleSignIn() {
        progressBar.setVisibility(View.VISIBLE);
        btnGoogleSignIn.setEnabled(false);

        credentialManager.getCredentialAsync(
                /* activity = */ this,
                /* request  = */ getCredentialRequest,
                /* cancellationSignal = */ new CancellationSignal(),
                /* executor = */ ContextCompat.getMainExecutor(this),
                /* callback = */ new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse result) {
                        progressBar.setVisibility(View.GONE);
                        btnGoogleSignIn.setEnabled(true);

                        Credential credential = result.getCredential();
                        handleSignIn(credential);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        progressBar.setVisibility(View.GONE);
                        btnGoogleSignIn.setEnabled(true);

                        Log.e(TAG, "Google sign-in failed: " + e.getLocalizedMessage(), e);
                        Toast.makeText(SignInActivity.this,
                                "Google sign-in cancelled or failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Convert the returned Credential into a GoogleIdTokenCredential
     * and then log into Firebase with that ID token.
     */
    private void handleSignIn(@NonNull Credential credential) {
        if (credential instanceof CustomCredential) {
            CustomCredential customCredential = (CustomCredential) credential;

            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    .equals(customCredential.getType())) {
                try {
                    Bundle credentialData = customCredential.getData();
                    GoogleIdTokenCredential googleIdTokenCredential =
                            GoogleIdTokenCredential.createFrom(credentialData);

                    String idToken = googleIdTokenCredential.getIdToken();
                    Log.d(TAG, "Google ID token received.");
                    firebaseAuthWithGoogle(idToken);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse GoogleIdTokenCredential", e);
                    Toast.makeText(this,
                            "Error reading Google credentials.",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "Unexpected credential type: " + customCredential.getType());
            }
        } else {
            Log.w(TAG, "Credential is not a CustomCredential type.");
        }
    }

    /**
     * Exchange the Google ID token for a Firebase credential
     * and sign in to Firebase.
     */
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        checkUserStatus(user);
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(SignInActivity.this,
                                "Firebase authentication failed.",
                                Toast.LENGTH_SHORT).show();
                        checkUserStatus(null);
                    }
                });
    }

    /**
     * Navigate to your main/home activity when signed-in.
     */

    private void checkUserStatus(@Nullable FirebaseUser currentUser) {

        // 1. User NOT logged in → go to Onboarding
        if (currentUser == null) {
            return;
        }

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
                    startActivity(new Intent(SignInActivity.this, MainActivity.class));
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

    private void goToProfileDetails() {
        startActivity(new Intent(this, ProfileDetailsActivity.class));
        finish();
    }
}