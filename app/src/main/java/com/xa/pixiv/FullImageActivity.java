package com.xa.pixiv;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.ui.ImagePagerAdapter;

public final class FullImageActivity extends AppCompatActivity {
    public static final String EXTRA_WORK = "full_image_work";
    public static final String EXTRA_PAGE = "full_image_page";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_full_image);
        ArtWork work = readWork();
        if (work == null) { finish(); return; }
        int count = Math.max(1, work.getPageUrls().size());
        int initial = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_PAGE, 0), count - 1));
        TextView hint = findViewById(R.id.full_image_hint);
        ViewPager2 pager = findViewById(R.id.full_image_pager);
        pager.setAdapter(new ImagePagerAdapter(work, null));
        pager.setCurrentItem(initial, false);
        hint.setText(count > 1 ? (initial + 1) + " / " + count + "  ·  左右滑动" : "已按原比例完整显示");
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                if (count > 1) hint.setText((position + 1) + " / " + count + "  ·  左右滑动");
            }
        });
        findViewById(R.id.full_image_back).setOnClickListener(v -> finish());
        if (count == 1) hint.animate().alpha(0f).setStartDelay(1600L).setDuration(350L).start();
    }

    private ArtWork readWork() {
        if (Build.VERSION.SDK_INT >= 33) return getIntent().getSerializableExtra(EXTRA_WORK, ArtWork.class);
        return (ArtWork) getIntent().getSerializableExtra(EXTRA_WORK);
    }
}
