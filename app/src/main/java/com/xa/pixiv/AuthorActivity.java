package com.xa.pixiv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.xa.pixiv.data.ArtPage;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivRepository;
import com.xa.pixiv.data.PixivUser;
import com.xa.pixiv.data.PixivImages;
import com.xa.pixiv.ui.ArtAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuthorActivity extends AppCompatActivity implements ArtAdapter.Listener {
    public static final String EXTRA_USER_ID = "user_id";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PixivRepository repository;
    private ArtAdapter adapter;
    private List<ArtWork> works = new ArrayList<>();
    private String nextUrl = "";
    private long userId;
    private boolean followed;
    private boolean loading;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author);
        userId = getIntent().getLongExtra(EXTRA_USER_ID, 0L);
        repository = new PixivRepository(this);
        ((MaterialToolbar) findViewById(R.id.author_toolbar)).setNavigationOnClickListener(v -> finish());
        RecyclerView list = findViewById(R.id.author_list);
        GridLayoutManager manager = new GridLayoutManager(this, 2);
        list.setLayoutManager(manager);
        adapter = new ArtAdapter(false, this);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && !loading && !nextUrl.isEmpty()
                        && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) loadNext();
            }
        });
        findViewById(R.id.author_follow).setOnClickListener(v -> toggleFollow());
        if (userId <= 0) {
            Toast.makeText(this, "这个作品没有可用的画师 ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        load();
    }

    private void load() {
        executor.execute(() -> {
            try {
                PixivUser user = repository.loadUser(userId);
                ArtPage page = repository.loadUserIllusts(userId);
                runOnUiThread(() -> render(user, page));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        error.getMessage() == null ? "画师资料加载失败" : error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void render(PixivUser user, ArtPage page) {
        followed = user.followed;
        ((TextView) findViewById(R.id.author_name)).setText(user.name);
        ((TextView) findViewById(R.id.author_info)).setText("@" + user.account + " · "
                + user.illusts + " 插画 · " + user.manga + " 漫画");
        ((TextView) findViewById(R.id.author_comment)).setText(user.comment.isEmpty() ? "这位画师还没有填写简介" : user.comment);
        ((MaterialButton) findViewById(R.id.author_follow)).setText(followed ? "已关注" : "关注");
        if (!user.avatar.isEmpty()) Glide.with(this).load(PixivImages.glide(this, user.avatar))
                .circleCrop().into((ImageView) findViewById(R.id.author_avatar));
        works = new ArrayList<>(page.getItems());
        nextUrl = page.getNextUrl();
        adapter.setWorks(works, true);
        findViewById(R.id.author_empty).setVisibility(works.isEmpty() ? View.VISIBLE : View.GONE);
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
                adapter.setWorks(works, true);
                loading = false;
            });
        });
    }

    private void toggleFollow() {
        View button = findViewById(R.id.author_follow);
        button.setEnabled(false);
        executor.execute(() -> {
            try {
                repository.setFollow(userId, !followed);
                followed = !followed;
                runOnUiThread(() -> {
                    ((MaterialButton) button).setText(followed ? "已关注" : "关注");
                    button.setEnabled(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
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
                runOnUiThread(() -> Toast.makeText(this,
                        error.getMessage() == null ? "收藏同步失败" : error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    @Override public void onFilter(String type) { }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
