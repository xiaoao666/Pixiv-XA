package com.xa.pixiv.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.xa.pixiv.DetailActivity;
import com.xa.pixiv.R;
import com.xa.pixiv.data.ArtPage;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeFragment extends Fragment implements ArtAdapter.Listener {
    private PixivRepository repository;
    private ArtAdapter adapter;
    private SwipeRefreshLayout refresh;
    private ExecutorService executor;
    private List<ArtWork> allWorks = new ArrayList<>();
    private String nextUrl = "";
    private boolean loadingMore;
    private String currentType = "illust";

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                                  @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = new PixivRepository(requireContext());
        executor = Executors.newSingleThreadExecutor();
        refresh = view.findViewById(R.id.swipe_refresh);
        RecyclerView recycler = view.findViewById(R.id.recycler);
        adapter = new ArtAdapter(true, this);
        GridLayoutManager manager = new GridLayoutManager(requireContext(), 2);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position == 0 ? 2 : 1; }
        });
        recycler.setLayoutManager(manager);
        recycler.setAdapter(adapter);
        refresh.setColorSchemeResources(R.color.pink_500, R.color.cyan_400, R.color.violet_400);
        refresh.setOnRefreshListener(() -> load(false));
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView list, int dx, int dy) {
                if (dy > 0 && !loadingMore && !nextUrl.isEmpty()
                        && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) loadNext();
            }
        });
        load(true);
    }

    private void load(boolean firstLoad) {
        refresh.setRefreshing(true);
        executor.execute(() -> {
            ArtPage page = repository.loadRecommended(currentType);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                allWorks = new ArrayList<>(page.getItems());
                nextUrl = page.getNextUrl();
                adapter.setWorks(allWorks, repository.session().isLoggedIn());
                refresh.setRefreshing(false);
                if (!firstLoad) Toast.makeText(requireContext(), "已刷新" + typeLabel(currentType) + "推荐", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void loadNext() {
        loadingMore = true;
        String url = nextUrl;
        executor.execute(() -> {
            ArtPage page = repository.loadNext(url);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                LinkedHashMap<Long, ArtWork> unique = new LinkedHashMap<>();
                for (ArtWork item : allWorks) unique.put(item.getId(), item);
                for (ArtWork item : page.getItems()) unique.put(item.getId(), item);
                allWorks = new ArrayList<>(unique.values());
                nextUrl = page.getNextUrl();
                adapter.setWorks(allWorks, repository.session().isLoggedIn());
                loadingMore = false;
            });
        });
    }

    @Override public void onOpen(ArtWork work, ImageView image) { DetailActivity.open(this, work, image); }
    @Override public void onBookmark(ArtWork work, int position) {
        executor.execute(() -> {
            try {
                boolean state = repository.toggleBookmark(work);
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    adapter.notifyItemChanged(position);
                    Toast.makeText(requireContext(), state ? "已同步到 Pixiv 收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        error.getMessage() == null ? "收藏同步失败" : error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    @Override public void onFilter(String type) {
        String requested = "all".equals(type) ? "illust" : type;
        if (!"illust".equals(requested) && !"manga".equals(requested) && !"novel".equals(requested)) return;
        if (requested.equals(currentType) && !allWorks.isEmpty()) return;
        currentType = requested;
        allWorks.clear();
        nextUrl = "";
        adapter.setWorks(allWorks, repository.session().isLoggedIn());
        load(true);
    }

    private static String typeLabel(String type) {
        if ("manga".equals(type)) return "漫画";
        if ("novel".equals(type)) return "小说";
        return "插画";
    }
    @Override public void onDestroyView() {
        if (executor != null) executor.shutdownNow();
        super.onDestroyView();
    }
}
