package com.retroxinteractive.amora;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

    private ImageView iconHome, iconDiscover, iconChat, iconProfile;

    private View bottomNavBar;
    private static final int REQ_POST_NOTIFICATIONS = 1001;
    private static final int REQ_IGNORE_BATTERY_OPTIMIZATIONS = 2001;

    private ActivityResultLauncher<Intent> batteryOptLauncher;

    private static final String PREFS_NAME = "amora_prefs";
    private static final String KEY_BATTERY_DIALOG_SHOWN = "battery_dialog_shown";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        init(savedInstanceState);
        maybeShowBatteryOptimizationDialogOnce();
        checkAndRequestNotificationPermission();
    }

    private void init(Bundle savedInstanceState) {
        FrameLayout navHome = findViewById(R.id.nav_home);
        FrameLayout navDiscover = findViewById(R.id.nav_discover);
        FrameLayout navChat = findViewById(R.id.nav_chat);
        FrameLayout navProfile = findViewById(R.id.nav_profile);
        bottomNavBar = (View) navHome.getParent();

        iconHome = findViewById(R.id.icon_home);
        iconDiscover = findViewById(R.id.icon_discover);
        iconChat = findViewById(R.id.icon_chat);
        iconProfile = findViewById(R.id.icon_profile);

        batteryOptLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {});

        // default tab: Home
        if (savedInstanceState == null) {
            switchToFragment(new HomepageFragment());
            highlightTab("home");
        }

        navHome.setOnClickListener(v -> {
            switchToFragment(new HomepageFragment());
            highlightTab("home");
        });

        navDiscover.setOnClickListener(v -> {
            switchToFragment(new DiscoverFragment());
            highlightTab("discover");
        });

        navChat.setOnClickListener(v -> {
            switchToFragment(new ChatListFragment());
            highlightTab("chat");
        });

        navProfile.setOnClickListener(v -> {
            // Open *own* profile
            switchToFragment(ProfileFragment.newInstance(true));
            highlightTab("profile");
        });
    }

    private void switchToFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .commit();
    }

    private void highlightTab(String tab) {
        // reset all to inactive first
        iconHome.setImageResource(R.drawable.ic_home);
        iconDiscover.setImageResource(R.drawable.ic_discover);
        iconChat.setImageResource(R.drawable.ic_chat);
        iconProfile.setImageResource(R.drawable.ic_profile);

        // then set active one (yellow background icons)
        switch (tab) {
            case "home":
                iconHome.setImageResource(R.drawable.ic_highlight_home);
                break;
            case "discover":
                iconDiscover.setImageResource(R.drawable.ic_highlight_discover);
                break;
            case "chat":
                iconChat.setImageResource(R.drawable.ic_highlight_chat);
                break;
            case "profile":
                iconProfile.setImageResource(R.drawable.ic_highlight_profile);
                break;
        }
    }

    /**
     * Called from Homepage card + ChatListFragment to open a 1-to-1 chat.
     */
    public void openChatWith(String receiverId,
                             String receiverName,
                             String receiverPhotoUrl) {
        highlightTab("chat");

        ChatFragment fragment = ChatFragment.newInstance(receiverId, receiverName, receiverPhotoUrl);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    public void setBottomNavVisible(boolean visible) {
        if (bottomNavBar != null) {
            bottomNavBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void checkAndRequestNotificationPermission() {
        // 1) For Android 13+ → request POST_NOTIFICATIONS runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
                return;
            }
        }

        // 2) For all versions → check if notifications are globally disabled for this app
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            showEnableNotificationsDialog();
        }
    }

    private void showEnableNotificationsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Notifications")
                .setMessage("To get chat alerts and updates, please enable notifications for Amora in your phone settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> openAppNotificationSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            // Older versions: generic app settings
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted → check if notifications are enabled at system level
                if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    showEnableNotificationsDialog();
                }
            }
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("Allow Background Activity")
                        .setMessage("To receive chat notifications instantly, please allow Amora to continue running in the background.")
                        .setPositiveButton("Allow", (dialog, which) -> {
                            // Mark as shown so we don't nag again
                            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                            prefs.edit().putBoolean(KEY_BATTERY_DIALOG_SHOWN, true).apply();

                            openBatteryOptimizationSettings();
                        })
                        .setNegativeButton("Later", (dialog, which) -> {
                            // Also mark as shown so it doesn't keep popping every launch
                            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                            prefs.edit().putBoolean(KEY_BATTERY_DIALOG_SHOWN, true).apply();
                        })
                        .show();
            } else {
                // System already ignoring battery optimizations → mark as shown as well
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_BATTERY_DIALOG_SHOWN, true).apply();
            }
        }
    }

    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            batteryOptLauncher.launch(intent);
        }
    }

    private void maybeShowBatteryOptimizationDialogOnce() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean alreadyShown = prefs.getBoolean(KEY_BATTERY_DIALOG_SHOWN, false);
        if (alreadyShown) return;

        checkBatteryOptimization();
    }
}