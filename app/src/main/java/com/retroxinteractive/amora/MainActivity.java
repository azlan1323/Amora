package com.retroxinteractive.amora;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

    private FrameLayout navHome, navDiscover, navChat, navProfile;
    private ImageView iconHome, iconDiscover, iconChat, iconProfile;

    private View bottomNavBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        navHome = findViewById(R.id.nav_home);
        navDiscover = findViewById(R.id.nav_discover);
        navChat = findViewById(R.id.nav_chat);
        navProfile = findViewById(R.id.nav_profile);
        bottomNavBar = (View) navHome.getParent();

        iconHome = findViewById(R.id.icon_home);
        iconDiscover = findViewById(R.id.icon_discover);
        iconChat = findViewById(R.id.icon_chat);
        iconProfile = findViewById(R.id.icon_profile);

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
        iconHome.setImageResource(R.drawable.ic_nav_home);
        iconDiscover.setImageResource(R.drawable.ic_nav_block);
        iconChat.setImageResource(R.drawable.ic_nav_chat);
        iconProfile.setImageResource(R.drawable.ic_nav_profile);

        // then set active one (yellow background icons)
        switch (tab) {
            case "home":
                iconHome.setImageResource(R.drawable.ic_highlight_homepage);
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
}