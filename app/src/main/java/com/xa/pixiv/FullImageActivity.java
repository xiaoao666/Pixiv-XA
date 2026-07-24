package com.xa.pixiv;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.ui.FullImagePagerAdapter;

import java.util.Locale;

public final class FullImageActivity extends AppCompatActivity {
    public static final String EXTRA_WORK = "full_image_work";
    public static final String EXTRA_PAGE = "full_image_page";

    private FullImagePagerAdapter adapter;
    private View loadingCard;
    private TextView loadingTitle;
    private TextView loadingBytes;
    private TextView retry;
    private TextView hint;
    private LinearProgressIndicator progress;
    private int currentPage;
    private int pageCount;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_full_image);
        ArtWork work = readWork();
        if (work == null) { finish(); return; }

        pageCount = Math.max(1, work.getPageUrls().size());
        int initial = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_PAGE, 0), pageCount - 1));
        hint = findViewById(R.id.full_image_hint);
        loadingCard = findViewById(R.id.full_image_loading);
        loadingTitle = findViewById(R.id.full_image_loading_title);
        loadingBytes = findViewById(R.id.full_image_loading_bytes);
        retry = findViewById(R.id.full_image_retry);
        progress = findViewById(R.id.full_image_progress);

        adapter = new FullImagePagerAdapter(this, work, new FullImagePagerAdapter.ProgressListener() {
            @Override public void onStart(int position) {
                if (position == currentPage) showLoading();
            }

            @Override public void onProgress(int position, long loaded, long total) {
                if (position != currentPage) return;
                loadingCard.setVisibility(View.VISIBLE);
                retry.setVisibility(View.GONE);
                boolean known = total > 0;
                int percent = known ? (int) Math.min(100L, loaded * 100L / total) : 0;
                progress.setIndeterminate(!known);
                if (known) progress.setProgressCompat(percent, true);
                loadingTitle.setText(known ? "正在加载原图 · " + percent + "%" : "正在加载原图");
                loadingBytes.setText(bytes(loaded) + (known ? " / " + bytes(total) : " 已接收"));
            }

            @Override public void onReady(int position) {
                if (position != currentPage) return;
                progress.setIndeterminate(false);
                progress.setProgressCompat(100, true);
                loadingTitle.setText("原图加载完成");
                retry.setVisibility(View.GONE);
                updateHint(position, true);
                loadingCard.postDelayed(() -> {
                    if (position == currentPage && adapter.isReady(position)) {
                        loadingCard.setVisibility(View.GONE);
                    }
                }, 420L);
            }

            @Override public void onError(int position, Exception error) {
                if (position != currentPage) return;
                loadingCard.setVisibility(View.VISIBLE);
                progress.setIndeterminate(false);
                progress.setProgressCompat(0, false);
                loadingTitle.setText("原图加载失败");
                loadingBytes.setText(error.getMessage() == null ? "网络连接异常" : error.getMessage());
                retry.setVisibility(View.VISIBLE);
            }
        });

        ViewPager2 pager = findViewById(R.id.full_image_pager);
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { selectPage(position); }
        });
        pager.setCurrentItem(initial, false);
        selectPage(initial);
        loadingCard.setOnClickListener(v -> {
            if (retry.getVisibility() == View.VISIBLE) {
                showLoading();
                adapter.requestOriginal(currentPage);
            }
        });
        findViewById(R.id.full_image_back).setOnClickListener(v -> finish());
    }

    private void selectPage(int position) {
        currentPage = position;
        updateHint(position, adapter.isReady(position));
        if (adapter.isReady(position)) loadingCard.setVisibility(View.GONE);
        else {
            showLoading();
            adapter.requestOriginal(position);
        }
    }

    private void showLoading() {
        loadingCard.animate().cancel();
        loadingCard.setAlpha(1f);
        loadingCard.setVisibility(View.VISIBLE);
        loadingTitle.setText("正在准备原图…");
        loadingBytes.setText("缩略图保持显示，原图会在后台载入");
        retry.setVisibility(View.GONE);
        progress.setIndeterminate(true);
    }

    private void updateHint(int position, boolean originalReady) {
        String pages = pageCount > 1 ? (position + 1) + " / " + pageCount + "  ·  左右滑动" : "双指缩放 · 双击放大";
        hint.setText(pages + (pageCount > 1 ? "  ·  双指缩放" : "")
                + (originalReady ? "  ·  原图" : "  ·  预览"));
    }

    private static String bytes(long value) {
        if (value < 1024L) return value + " B";
        if (value < 1024L * 1024L) return String.format(Locale.CHINA, "%.1f KB", value / 1024f);
        return String.format(Locale.CHINA, "%.1f MB", value / (1024f * 1024f));
    }

    @Override protected void onDestroy() {
        if (adapter != null) adapter.cancelAll();
        super.onDestroy();
    }

    private ArtWork readWork() {
        if (Build.VERSION.SDK_INT >= 33) return getIntent().getSerializableExtra(EXTRA_WORK, ArtWork.class);
        return (ArtWork) getIntent().getSerializableExtra(EXTRA_WORK);
    }
}
