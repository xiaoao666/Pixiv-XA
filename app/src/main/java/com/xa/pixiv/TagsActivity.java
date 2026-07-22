package com.xa.pixiv;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivRepository;
import com.xa.pixiv.data.TrendingTag;
import com.xa.pixiv.data.PixivImages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TagsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PixivRepository repository;
    private TagAdapter adapter;
    private SwipeRefreshLayout refresh;
    private String type = "illust";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags);
        repository = new PixivRepository(this);
        ((MaterialToolbar) findViewById(R.id.tags_toolbar)).setNavigationOnClickListener(v -> finish());
        RecyclerView list = findViewById(R.id.tags_list);
        list.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new TagAdapter();
        list.setAdapter(adapter);
        refresh = findViewById(R.id.tags_refresh);
        findViewById(R.id.tags_illust).setOnClickListener(v -> { type = "illust"; load(); });
        findViewById(R.id.tags_novel).setOnClickListener(v -> { type = "novel"; load(); });
        refresh.setOnRefreshListener(this::load);
        load();
    }

    private void load() {
        refresh.setRefreshing(true);
        executor.execute(() -> {
            List<TrendingTag> tags = repository.loadTrendingTags(type);
            runOnUiThread(() -> {
                adapter.setItems(tags);
                refresh.setRefreshing(false);
                if (tags.isEmpty()) Toast.makeText(this, "暂时没有获取到热门标签", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private final class TagAdapter extends RecyclerView.Adapter<TagAdapter.Holder> {
        private List<TrendingTag> items = new ArrayList<>();
        void setItems(List<TrendingTag> values) { items = new ArrayList<>(values); notifyDataSetChanged(); }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trending_tag, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            TrendingTag tag = items.get(position);
            holder.name.setText("# " + tag.name);
            holder.translation.setText(tag.translatedName.isEmpty() ? "点击搜索这个标签" : tag.translatedName);
            ArtWork sample = tag.sample;
            if (sample != null && !sample.getPreviewUrl().isEmpty()) {
                GlideUrl url = PixivImages.glide(TagsActivity.this, sample.getPreviewUrl());
                Glide.with(holder.cover).load(url).centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade()).into(holder.cover);
            } else {
                Glide.with(holder.cover).clear(holder.cover);
                holder.cover.setImageResource(R.drawable.demo_art_05);
            }
            holder.itemView.setOnClickListener(v -> startActivity(new Intent(TagsActivity.this, SearchActivity.class)
                    .putExtra(SearchActivity.EXTRA_QUERY, tag.name)));
        }

        @Override public int getItemCount() { return items.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView cover; final TextView name; final TextView translation;
            Holder(View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.tag_cover);
                name = itemView.findViewById(R.id.tag_name);
                translation = itemView.findViewById(R.id.tag_translation);
            }
        }
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
