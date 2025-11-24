package com.retroxinteractive.amora;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

    private ImageView iconHome, iconDiscover, iconChat, iconProfile;
    private View bottomNavBar;
    // keep track of selected tab
    private enum Tab { HOME, DISCOVER, CHAT, PROFILE }
    private Tab currentTab = Tab.HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // init views
        iconHome = findViewById(R.id.icon_home);
        iconDiscover = findViewById(R.id.icon_discover);
        iconChat = findViewById(R.id.icon_chat);
        iconProfile = findViewById(R.id.icon_profile);

        FrameLayout navHome = findViewById(R.id.nav_home);
        FrameLayout navDiscover = findViewById(R.id.nav_discover);
        FrameLayout navChat = findViewById(R.id.nav_chat);
        FrameLayout navProfile = findViewById(R.id.nav_profile);
        bottomNavBar = (View) navHome.getParent();

        // default fragment (Home)
        if (savedInstanceState == null) {
            currentTab = null;
            switchToTab(Tab.HOME);
        }

        navHome.setOnClickListener(v -> switchToTab(Tab.HOME));
        navDiscover.setOnClickListener(v -> switchToTab(Tab.DISCOVER));
        navChat.setOnClickListener(v -> switchToTab(Tab.CHAT));
        navProfile.setOnClickListener(v -> switchToTab(Tab.PROFILE));
    }

    private void switchToTab(@NonNull Tab tab) {
        if (tab == currentTab) return; // already selected

        Fragment fragment;

        switch (tab) {
            case DISCOVER:
                fragment = new DiscoverFragment();
                break;
            case CHAT:
                fragment = new ChatFragment();
                break;
            case PROFILE:
                fragment = ProfileFragment.newInstance(true); // opened from main profile icon
                break;
            case HOME:
            default:
                fragment = new HomepageFragment();
                break;
        }

        // replace fragment
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .commit();

            // show / hide bottom nav
        if (bottomNavBar != null) {
            bottomNavBar.setVisibility(tab == Tab.PROFILE ? View.GONE : View.VISIBLE);
        }

        // update icons
        setActiveIcon(tab);

        currentTab = tab;
    }

    private void setActiveIcon(@NonNull Tab activeTab) {
        // reset all to inactive first
        iconHome.setImageResource(R.drawable.ic_nav_home);
        iconDiscover.setImageResource(R.drawable.ic_nav_block);
        iconChat.setImageResource(R.drawable.ic_nav_chat);
        iconProfile.setImageResource(R.drawable.ic_nav_profile);

        // then set active one (yellow background icons)
        switch (activeTab) {
            case HOME:
                iconHome.setImageResource(R.drawable.ic_highlight_homepage);
                break;
            case DISCOVER:
                iconDiscover.setImageResource(R.drawable.ic_highlight_discover);
                break;
            case CHAT:
                iconChat.setImageResource(R.drawable.ic_highlight_chat);
                break;
            case PROFILE:
                iconProfile.setImageResource(R.drawable.ic_highlight_profile);
                break;
        }
    }
}