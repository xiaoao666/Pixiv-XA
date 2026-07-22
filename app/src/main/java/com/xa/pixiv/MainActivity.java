package com.xa.pixiv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.xa.pixiv.data.SessionStore;
import com.xa.pixiv.ui.ExploreFragment;
import com.xa.pixiv.ui.HomeFragment;
import com.xa.pixiv.ui.LibraryFragment;

public final class MainActivity extends AppCompatActivity {
    private ViewPager2 pager;
    private BottomNavigationView navigation;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionStore session = new SessionStore(this);
        if (!session.isOnboarded()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();
        pager = findViewById(R.id.view_pager);
        navigation = findViewById(R.id.bottom_nav);
        subtitle = findViewById(R.id.subtitle);
        pager.setAdapter(new MainPagerAdapter(this));
        pager.setOffscreenPageLimit(2);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) pager.setCurrentItem(0, true);
            else if (item.getItemId() == R.id.nav_discover) pager.setCurrentItem(1, true);
            else if (item.getItemId() == R.id.nav_library) pager.setCurrentItem(2, true);
            return true;
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                int id = position == 0 ? R.id.nav_home : position == 1 ? R.id.nav_discover : R.id.nav_library;
                navigation.setSelectedItemId(id);
                String text = position == 0 ? "把灵感收进自己的星图"
                        : position == 1 ? "从标签、排行和画师发现下一张"
                        : "收藏、下载与账号都放在这里";
                subtitle.animate().alpha(0f).setDuration(90).withEndAction(() -> {
                    subtitle.setText(text);
                    subtitle.animate().alpha(1f).setDuration(160).start();
                }).start();
            }
        });
        findViewById(R.id.search_button).setOnClickListener(v -> pager.setCurrentItem(1, true));
    }

    private void applySystemBarInsets() {
        View toolbar = findViewById(R.id.toolbar);
        View bottom = findViewById(R.id.bottom_nav);
        int toolbarHeight = getResources().getDimensionPixelSize(R.dimen.main_toolbar_height);
        int navHeight = getResources().getDimensionPixelSize(R.dimen.main_nav_height);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomSafe = Math.max(bars.bottom, getResources().getDimensionPixelSize(R.dimen.main_nav_fallback_inset));
            ViewGroup.LayoutParams toolbarParams = toolbar.getLayoutParams();
            toolbarParams.height = toolbarHeight + bars.top;
            toolbar.setLayoutParams(toolbarParams);
            toolbar.setPadding(toolbar.getPaddingLeft(), bars.top, toolbar.getPaddingRight(), 0);
            ViewGroup.LayoutParams navParams = bottom.getLayoutParams();
            navParams.height = navHeight + bottomSafe;
            bottom.setLayoutParams(navParams);
            bottom.setPadding(bottom.getPaddingLeft(), 0, bottom.getPaddingRight(), bottomSafe);
            return insets;
        });
    }

    private static final class MainPagerAdapter extends FragmentStateAdapter {
        MainPagerAdapter(AppCompatActivity activity) { super(activity); }
        @NonNull @Override public Fragment createFragment(int position) {
            if (position == 1) return new ExploreFragment();
            if (position == 2) return new LibraryFragment();
            return new HomeFragment();
        }
        @Override public int getItemCount() { return 3; }
    }
}
