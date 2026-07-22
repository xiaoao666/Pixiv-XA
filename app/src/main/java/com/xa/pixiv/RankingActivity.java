package com.xa.pixiv;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xa.pixiv.data.ArtPage;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivRepository;
import com.xa.pixiv.ui.ArtAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RankingActivity extends AppCompatActivity implements ArtAdapter.Listener {
    public static final String EXTRA_R18 = "r18";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PixivRepository repository;
    private ArtAdapter adapter;
    private SwipeRefreshLayout refresh;
    private ChipGroup modes;
    private TextView meta;
    private String category = "illust";
    private String mode = "day";
    private String modeLabel = "日榜";
    private String date;
    private String nextUrl = "";
    private boolean mangaOnly;
    private boolean loading;
    private List<ArtWork> works = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);
        repository = new PixivRepository(this);
        ((MaterialToolbar) findViewById(R.id.rank_toolbar)).setNavigationOnClickListener(v -> finish());
        refresh = findViewById(R.id.rank_refresh);
        modes = findViewById(R.id.rank_modes);
        meta = findViewById(R.id.rank_meta);
        RecyclerView list = findViewById(R.id.rank_list);
        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        manager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        list.setLayoutManager(manager);
        adapter = new ArtAdapter(false, this);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && !loading && !nextUrl.isEmpty()
                        && com.xa.pixiv.ui.GridScroll.lastVisible(manager) >= adapter.getItemCount() - 5) loadNext();
            }
        });
        if (getIntent().getBooleanExtra(EXTRA_R18, false)) {
            category = "r18";
            ((MaterialButton) findViewById(R.id.rank_category)).setText("R18 榜");
        }
        renderModes();
        findViewById(R.id.rank_category).setOnClickListener(v -> chooseCategory());
        findViewById(R.id.rank_date).setOnClickListener(v -> pickDate());
        findViewById(R.id.rank_reset).setOnClickListener(v -> {
            date = null;
            ((MaterialButton) findViewById(R.id.rank_date)).setText("最新一期");
            load();
        });
        refresh.setOnRefreshListener(this::load);
        load();
    }

    private void chooseCategory() {
        String[] labels = repository.session().isR18Enabled()
                ? new String[]{"插画榜", "漫画榜", "小说榜", "R18 榜"}
                : new String[]{"插画榜", "漫画榜", "小说榜"};
        String[] values = repository.session().isR18Enabled()
                ? new String[]{"illust", "manga", "novel", "r18"}
                : new String[]{"illust", "manga", "novel"};
        new MaterialAlertDialogBuilder(this).setTitle("排行榜分类").setItems(labels, (dialog, which) -> {
            category = values[which];
            ((MaterialButton) findViewById(R.id.rank_category)).setText(labels[which]);
            renderModes();
            load();
        }).show();
    }

    private void renderModes() {
        modes.removeAllViews();
        String[] labels;
        String[] values;
        if ("r18".equals(category)) {
            labels = new String[]{"今日 R18", "本周 R18", "男性 R18", "女性 R18", "AI R18",
                    "今日 R18G", "本周 R18G", "R18 漫画"};
            values = new String[]{"day_r18", "week_r18", "day_male_r18", "day_female_r18", "day_r18_ai",
                    "day_r18g", "week_r18g", "day_r18|manga"};
        } else if ("manga".equals(category)) {
            labels = new String[]{"漫画日榜", "漫画周榜", "漫画月榜", "漫画新人榜"};
            values = new String[]{"day_manga", "week_manga", "month_manga", "week_rookie_manga"};
        } else if ("novel".equals(category)) {
            labels = new String[]{"小说日榜", "小说周榜", "男性日榜", "女性日榜", "新人周榜"};
            values = new String[]{"day", "week", "day_male", "day_female", "week_rookie"};
        } else {
            labels = new String[]{"日榜", "周榜", "月榜", "原创周榜", "新人周榜", "男性日榜", "女性日榜", "AI 日榜"};
            values = new String[]{"day", "week", "month", "week_original", "week_rookie", "day_male", "day_female", "day_ai"};
        }
        mode = values[0];
        modeLabel = labels[0];
        mangaOnly = false;
        for (int i = 0; i < labels.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            if (i == 0) chip.setChecked(true);
            String value = values[i];
            String label = labels[i];
            chip.setOnClickListener(v -> {
                mangaOnly = value.endsWith("|manga");
                mode = mangaOnly ? value.substring(0, value.indexOf('|')) : value;
                modeLabel = label;
                load();
            });
            modes.addView(chip);
        }
    }

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            ((MaterialButton) findViewById(R.id.rank_date)).setText(date);
            load();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void load() {
        refresh.setRefreshing(true);
        executor.execute(() -> {
            ArtPage page = "novel".equals(category)
                    ? repository.loadNovelRanking(mode, date) : repository.loadRanking(mode, date);
            runOnUiThread(() -> {
                works = filter(page.getItems());
                nextUrl = page.getNextUrl();
                adapter.setWorks(works, repository.session().isLoggedIn());
                meta.setText(modeLabel + (date == null ? " · 最新一期" : " · " + date)
                        + (works.isEmpty() ? " · 暂无结果" : ""));
                refresh.setRefreshing(false);
            });
        });
    }

    private List<ArtWork> filter(List<ArtWork> input) {
        if (!mangaOnly) return new ArrayList<>(input);
        List<ArtWork> result = new ArrayList<>();
        for (ArtWork item : input) if ("manga".equals(item.getType())) result.add(item);
        return result;
    }

    private void loadNext() {
        loading = true;
        String url = nextUrl;
        executor.execute(() -> {
            ArtPage page = repository.loadNext(url);
            runOnUiThread(() -> {
                LinkedHashMap<Long, ArtWork> unique = new LinkedHashMap<>();
                for (ArtWork item : works) unique.put(item.getId(), item);
                for (ArtWork item : filter(page.getItems())) unique.put(item.getId(), item);
                works = new ArrayList<>(unique.values());
                nextUrl = page.getNextUrl();
                adapter.setWorks(works, repository.session().isLoggedIn());
                loading = false;
            });
        });
    }

    @Override public void onOpen(ArtWork work, ImageView image) {
        startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_WORK, work));
    }

    @Override public void onBookmark(ArtWork work, int position) {
        executor.execute(() -> {
            try {
                repository.toggleBookmark(work);
                runOnUiThread(() -> adapter.notifyItemChanged(position));
            } catch (Exception error) {
                runOnUiThread(() -> android.widget.Toast.makeText(this,
                        error.getMessage() == null ? "收藏同步失败" : error.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override public void onFilter(String type) { }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
