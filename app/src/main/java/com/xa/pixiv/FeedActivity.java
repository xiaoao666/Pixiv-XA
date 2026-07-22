package com.xa.pixiv;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.xa.pixiv.data.ArtPage;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.HistoryStore;
import com.xa.pixiv.data.PixivRepository;
import com.xa.pixiv.ui.ArtAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Generic full-screen feed for account, latest, related and local-history timelines. */
public final class FeedActivity extends AppCompatActivity implements ArtAdapter.Listener {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_ILLUST_ID = "illust_id";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PixivRepository repository;
    private HistoryStore historyStore;
    private ArtAdapter adapter;
    private SwipeRefreshLayout refresh;
    private TextView empty;
    private List<ArtWork> works = new ArrayList<>();
    private String nextUrl = "";
    private String mode = "following";
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);
        repository = new PixivRepository(this);
        historyStore = new HistoryStore(this);
        String requested = getIntent().getStringExtra(EXTRA_MODE);
        if (requested != null && !requested.isEmpty()) mode = requested;

        MaterialToolbar toolbar = findViewById(R.id.feed_toolbar);
        toolbar.setTitle(titleFor(mode));
        toolbar.setSubtitle(subtitleFor(mode));
        toolbar.setNavigationOnClickListener(v -> finish());
        if ("history".equals(mode)) {
            toolbar.getMenu().add(Menu.NONE, 1001, Menu.NONE, "清空")
                    .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1001) {
                    historyStore.clear();
                    load();
                    Toast.makeText(this, "浏览记录已清空", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }

        refresh = findViewById(R.id.feed_refresh);
        empty = findViewById(R.id.feed_empty);
        RecyclerView list = findViewById(R.id.feed_list);
        GridLayoutManager manager = new GridLayoutManager(this, 2);
        list.setLayoutManager(manager);
        adapter = new ArtAdapter(false, this);
        list.setAdapter(adapter);
        refresh.setOnRefreshListener(this::load);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && !loading && !nextUrl.isEmpty()
                        && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    loadNext();
                }
            }
        });
        load();
    }

    private void load() {
        if (requiresLogin(mode) && !repository.session().isLoggedIn()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("登录 Pixiv 后才能读取这个页面\n点击此处前往登录");
            empty.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            refresh.setRefreshing(false);
            adapter.setWorks(new ArrayList<>(), false);
            return;
        }
        empty.setOnClickListener(null);
        refresh.setRefreshing(true);
        executor.execute(() -> {
            ArtPage page = firstPage();
            runOnUiThread(() -> {
                works = new ArrayList<>(page.getItems());
                nextUrl = page.getNextUrl();
                adapter.setWorks(works, repository.session().isLoggedIn());
                renderEmpty();
                refresh.setRefreshing(false);
            });
        });
    }

    private ArtPage firstPage() {
        switch (mode) {
            case "bookmarks": return repository.loadBookmarks();
            case "related": return repository.loadRelated(getIntent().getLongExtra(EXTRA_ILLUST_ID, 0L));
            case "latest_illust": return repository.loadLatest("illust");
            case "latest_manga": return repository.loadLatest("manga");
            case "latest_novel": return repository.loadLatest("novel");
            case "history": return new ArtPage(historyStore.all(), "");
            case "following":
            default: return repository.loadFollowing();
        }
    }

    private void loadNext() {
        loading = true;
        String url = nextUrl;
        executor.execute(() -> {
            ArtPage page = repository.loadNext(url);
            runOnUiThread(() -> {
                LinkedHashMap<Long, ArtWork> unique = new LinkedHashMap<>();
                for (ArtWork item : works) unique.put(item.getId(), item);
                for (ArtWork item : page.getItems()) unique.put(item.getId(), item);
                works = new ArrayList<>(unique.values());
                nextUrl = page.getNextUrl();
                adapter.setWorks(works, repository.session().isLoggedIn());
                renderEmpty();
                loading = false;
            });
        });
    }

    private void renderEmpty() {
        boolean show = works.isEmpty();
        empty.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) empty.setText("history".equals(mode)
                ? "还没有浏览记录\n打开任意作品后会自动保存"
                : "暂时没有内容，下拉可以重试");
    }

    private static boolean requiresLogin(String value) {
        return !"history".equals(value);
    }

    private static String titleFor(String value) {
        switch (value) {
            case "bookmarks": return "Pixiv 收藏";
            case "related": return "相关推荐";
            case "latest_illust": return "最新插画";
            case "latest_manga": return "最新漫画";
            case "latest_novel": return "最新小说";
            case "history": return "浏览记录";
            default: return "关注画师动态";
        }
    }

    private static String subtitleFor(String value) {
        switch (value) {
            case "bookmarks": return "BOOKMARKS / PIXIV CLOUD";
            case "related": return "RELATED / CONTINUOUS FEED";
            case "latest_illust": return "NEW / ILLUSTRATION";
            case "latest_manga": return "NEW / MANGA";
            case "latest_novel": return "NEW / NOVEL";
            case "history": return "LOCAL / RECENTLY VIEWED";
            default: return "FOLLOWING / PUBLIC & PRIVATE";
        }
    }

    @Override public void onOpen(ArtWork work, ImageView image) {
        startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_WORK, work));
    }

    @Override public void onBookmark(ArtWork work, int position) {
        executor.execute(() -> {
            try {
                boolean state = repository.toggleBookmark(work);
                runOnUiThread(() -> {
                    adapter.notifyItemChanged(position);
                    Toast.makeText(this, state ? "已同步到 Pixiv 收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        error.getMessage() == null ? "收藏同步失败" : error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override public void onFilter(String type) { }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
