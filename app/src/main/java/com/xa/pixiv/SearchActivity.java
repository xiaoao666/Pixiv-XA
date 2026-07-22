package com.xa.pixiv;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.xa.pixiv.data.ArtPage;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivImages;
import com.xa.pixiv.data.PixivRepository;
import com.xa.pixiv.data.PixivUser;
import com.xa.pixiv.data.SearchOptions;
import com.xa.pixiv.data.TrendingTag;
import com.xa.pixiv.ui.ArtAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SearchActivity extends AppCompatActivity implements ArtAdapter.Listener {
    public static final String EXTRA_QUERY = "query";
    private static final String MODE_WORKS = "works";
    private static final String MODE_USERS = "users";
    private static final String MODE_ID = "id";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService suggestionExecutor = Executors.newSingleThreadExecutor();
    private final Handler suggestionHandler = new Handler(Looper.getMainLooper());
    private final SearchOptions options = new SearchOptions();
    private PixivRepository repository;
    private ArtAdapter adapter;
    private SwipeRefreshLayout refresh;
    private TextInputEditText input;
    private TextView meta;
    private View filters;
    private View userScroll;
    private LinearLayout userResults;
    private View suggestionCard;
    private LinearLayout suggestionList;
    private Runnable pendingSuggestion;
    private int suggestionGeneration;
    private boolean suppressSuggestions;
    private String mode = MODE_WORKS;
    private String query = "";
    private String nextUrl = "";
    private boolean loading;
    private List<ArtWork> works = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_search);
        repository = new PixivRepository(this);
        ((MaterialToolbar) findViewById(R.id.search_toolbar)).setNavigationOnClickListener(v -> finish());
        input = findViewById(R.id.search_page_input);
        meta = findViewById(R.id.search_result_meta);
        refresh = findViewById(R.id.search_page_refresh);
        filters = findViewById(R.id.search_filters);
        userScroll = findViewById(R.id.search_user_scroll);
        userResults = findViewById(R.id.search_user_results);
        suggestionCard = findViewById(R.id.search_suggestions_card);
        suggestionList = findViewById(R.id.search_suggestions);

        RecyclerView list = findViewById(R.id.search_page_list);
        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        manager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        list.setLayoutManager(manager);
        adapter = new ArtAdapter(false, this);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recycler, int dx, int dy) {
                if (MODE_WORKS.equals(mode) && dy > 0 && !loading && !nextUrl.isEmpty()
                        && com.xa.pixiv.ui.GridScroll.lastVisible(manager) >= adapter.getItemCount() - 5) loadNext();
            }
        });

        TextInputLayout box = findViewById(R.id.search_page_box);
        box.setEndIconOnClickListener(v -> search());
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                search();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressSuggestions) scheduleSuggestions(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnFocusChangeListener((v, focused) -> {
            if (!focused) hideSuggestions();
            else if (input.getText() != null) scheduleSuggestions(input.getText().toString());
        });

        MaterialButtonToggleGroup modes = findViewById(R.id.search_mode_group);
        modes.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            if (checkedId == R.id.search_mode_users) applyMode(MODE_USERS);
            else if (checkedId == R.id.search_mode_id) applyMode(MODE_ID);
            else applyMode(MODE_WORKS);
        });

        bindFilters();
        refresh.setOnRefreshListener(this::search);
        String initial = getIntent().getStringExtra(EXTRA_QUERY);
        if (initial != null && !initial.isEmpty()) {
            input.setText(initial);
            search();
        }
    }

    private void applyMode(String value) {
        mode = value;
        hideSuggestions();
        boolean worksMode = MODE_WORKS.equals(mode);
        refresh.setVisibility(worksMode ? View.VISIBLE : View.GONE);
        filters.setVisibility(worksMode ? View.VISIBLE : View.GONE);
        userScroll.setVisibility(worksMode ? View.GONE : View.VISIBLE);
        userResults.removeAllViews();
        if (worksMode) {
            input.setHint("作品名、标签、漫画或小说");
            meta.setText(query.isEmpty() ? "输入关键词开始搜索" : "切回作品搜索，可继续浏览原结果");
        } else if (MODE_USERS.equals(mode)) {
            input.setHint("画师名称或画师 ID");
            meta.setText("输入画师名称；纯数字会直接打开画师主页");
        } else {
            input.setHint("粘贴 Pixiv 链接或输入数字 ID");
            meta.setText("可直达作品详情或画师主页");
        }
    }

    private void search() {
        hideSuggestions();
        query = input.getText() == null ? "" : input.getText().toString().trim();
        if (query.isEmpty()) {
            refresh.setRefreshing(false);
            meta.setText(MODE_ID.equals(mode) ? "输入 Pixiv 数字 ID" : "输入关键词开始搜索");
            return;
        }
        if (MODE_USERS.equals(mode)) searchUsers();
        else if (MODE_ID.equals(mode)) searchId();
        else searchWorks();
    }

    private void scheduleSuggestions(String raw) {
        if (pendingSuggestion != null) suggestionHandler.removeCallbacks(pendingSuggestion);
        String word = raw == null ? "" : raw.trim();
        if (!MODE_WORKS.equals(mode) || word.isEmpty() || word.length() > 80) {
            hideSuggestions();
            return;
        }
        final int generation = ++suggestionGeneration;
        pendingSuggestion = () -> suggestionExecutor.execute(() -> {
            try {
                List<TrendingTag> values = repository.autocomplete(word);
                runOnUiThread(() -> {
                    String current = input.getText() == null ? "" : input.getText().toString().trim();
                    if (generation == suggestionGeneration && word.equals(current)
                            && input.hasFocus() && MODE_WORKS.equals(mode)) {
                        renderSuggestions(word, values);
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    if (generation == suggestionGeneration) hideSuggestions();
                });
            }
        });
        suggestionHandler.postDelayed(pendingSuggestion, 380L);
    }

    private void renderSuggestions(String word, List<TrendingTag> values) {
        suggestionList.removeAllViews();
        int count = Math.min(8, values.size());
        for (int i = 0; i < count; i++) {
            TrendingTag value = values.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_search_suggestion, suggestionList, false);
            TextView title = row.findViewById(R.id.suggestion_title);
            TextView translation = row.findViewById(R.id.suggestion_translation);
            title.setText(highlight(value.name, word));
            boolean showTranslation = !value.translatedName.isEmpty()
                    && !value.translatedName.equalsIgnoreCase(value.name);
            translation.setText(showTranslation ? value.translatedName : "Pixiv 关联标签");
            row.setOnClickListener(v -> {
                suppressSuggestions = true;
                input.setText(value.name);
                input.setSelection(value.name.length());
                suppressSuggestions = false;
                hideSuggestions();
                search();
            });
            suggestionList.addView(row);
        }
        suggestionCard.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private CharSequence highlight(String text, String word) {
        SpannableString value = new SpannableString(text);
        String lower = text.toLowerCase(Locale.ROOT);
        String needle = word.toLowerCase(Locale.ROOT);
        int start = lower.indexOf(needle);
        if (start >= 0) value.setSpan(new ForegroundColorSpan(getColor(R.color.pink_500)),
                start, start + needle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    private void hideSuggestions() {
        suggestionGeneration++;
        if (pendingSuggestion != null) suggestionHandler.removeCallbacks(pendingSuggestion);
        suggestionCard.setVisibility(View.GONE);
    }

    private void searchWorks() {
        refresh.setRefreshing(true);
        executor.execute(() -> {
            ArtPage page = repository.search(query, options);
            runOnUiThread(() -> {
                works = new ArrayList<>(page.getItems());
                nextUrl = page.getNextUrl();
                adapter.setWorks(works, repository.session().isLoggedIn());
                meta.setText("“" + query + "” · " + works.size() + " 个作品");
                refresh.setRefreshing(false);
            });
        });
    }

    private void searchUsers() {
        Long id = extractId(query, false);
        if (id != null && query.matches("\\s*\\d+\\s*")) {
            openAuthor(id);
            return;
        }
        meta.setText("正在搜索画师…");
        userResults.removeAllViews();
        executor.execute(() -> {
            try {
                List<PixivUser> users = repository.searchUsers(query);
                runOnUiThread(() -> renderUsers(users));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    meta.setText("画师搜索失败");
                    Toast.makeText(this, error.getMessage() == null ? "画师搜索失败" : error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderUsers(List<PixivUser> users) {
        userResults.removeAllViews();
        meta.setText("“" + query + "” · " + users.size() + " 位画师");
        for (PixivUser user : users) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_user_search, userResults, false);
            ((TextView) row.findViewById(R.id.search_user_name)).setText(user.name);
            ((TextView) row.findViewById(R.id.search_user_account)).setText(
                    user.account.isEmpty() ? "Pixiv ID · " + user.id : "@" + user.account + " · ID " + user.id);
            ImageView avatar = row.findViewById(R.id.search_user_avatar);
            if (!user.avatar.isEmpty()) Glide.with(avatar).load(PixivImages.glide(this, user.avatar)).circleCrop().into(avatar);
            row.setOnClickListener(v -> openAuthor(user.id));
            userResults.addView(row);
        }
        if (users.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(repository.session().isLoggedIn() ? "没有找到匹配的画师" : "登录 Pixiv 后可搜索画师");
            empty.setTextColor(getColor(R.color.xa_muted));
            empty.setTextSize(14f);
            empty.setPadding(12, 28, 12, 28);
            userResults.addView(empty);
        }
    }

    private void searchId() {
        Long id = extractId(query, true);
        if (id == null) {
            Toast.makeText(this, "没有识别到有效的 Pixiv ID", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("打开 Pixiv ID " + id)
                .setItems(new String[]{"作品详情", "画师主页"}, (dialog, which) -> {
                    if (which == 0) openIllust(id); else openAuthor(id);
                }).show();
    }

    private Long extractId(String value, boolean allowUrl) {
        String normalized = value == null ? "" : value.trim();
        if (!allowUrl && !normalized.matches("\\d+")) return null;
        String digits = normalized.replaceAll(".*?(\\d+)\\D*$", "$1");
        if (!digits.matches("\\d+")) return null;
        try { return Long.parseLong(digits); }
        catch (NumberFormatException ignored) { return null; }
    }

    private void openIllust(long id) {
        meta.setText("正在打开作品 ID " + id + "…");
        executor.execute(() -> {
            try {
                ArtWork work = repository.loadIllust(id);
                runOnUiThread(() -> startActivity(new Intent(this, DetailActivity.class)
                        .putExtra(DetailActivity.EXTRA_WORK, work)));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    meta.setText("作品 ID " + id + " 打开失败");
                    Toast.makeText(this, error.getMessage() == null ? "没有找到这个作品" : error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openAuthor(long id) {
        startActivity(new Intent(this, AuthorActivity.class).putExtra(AuthorActivity.EXTRA_USER_ID, id));
    }

    private void loadNext() {
        loading = true;
        String url = nextUrl;
        executor.execute(() -> {
            ArtPage page = repository.loadNext(url);
            runOnUiThread(() -> {
                LinkedHashMap<Long, ArtWork> merged = new LinkedHashMap<>();
                for (ArtWork item : works) merged.put(item.getId(), item);
                for (ArtWork item : page.getItems()) merged.put(item.getId(), item);
                works = new ArrayList<>(merged.values());
                nextUrl = page.getNextUrl();
                adapter.setWorks(works, true);
                meta.setText("“" + query + "” · 已加载 " + works.size() + " 个作品");
                loading = false;
            });
        });
    }

    private void bindFilters() {
        findViewById(R.id.filter_sort).setOnClickListener(v -> choose("排序",
                new String[]{"最新发布", "较早发布", "热门优先（会员）"},
                new String[]{"date_desc", "date_asc", "popular_desc"}, value -> {
                    options.sort = value;
                    ((MaterialButton) v).setText("date_asc".equals(value) ? "较早" : "popular_desc".equals(value) ? "热门" : "最新");
                    search();
                }));
        findViewById(R.id.filter_target).setOnClickListener(v -> choose("匹配范围",
                new String[]{"标签部分匹配", "标签完全匹配", "标题与说明"},
                new String[]{"partial_match_for_tags", "exact_match_for_tags", "title_and_caption"}, value -> {
                    options.target = value;
                    ((MaterialButton) v).setText("exact_match_for_tags".equals(value) ? "标签完全匹配" : "title_and_caption".equals(value) ? "标题与说明" : "标签部分匹配");
                    search();
                }));
        findViewById(R.id.filter_bookmarks).setOnClickListener(v -> {
            String[] labels = {"不限", "100+", "500+", "1000+", "5000+", "10000+"};
            Integer[] values = {null, 100, 500, 1000, 5000, 10000};
            new MaterialAlertDialogBuilder(this).setTitle("最低收藏数").setItems(labels, (d, which) -> {
                options.bookmarkMin = values[which];
                ((MaterialButton) v).setText(values[which] == null ? "收藏数" : labels[which] + " 收藏");
                search();
            }).show();
        });
        findViewById(R.id.filter_type).setOnClickListener(v -> choose("作品类型",
                new String[]{"全部类型", "插画", "漫画", "小说"},
                new String[]{"all", "illust", "manga", "novel"}, value -> {
                    options.type = value;
                    ((MaterialButton) v).setText("all".equals(value) ? "全部类型" : "illust".equals(value) ? "插画" : "manga".equals(value) ? "漫画" : "小说");
                    search();
                }));
        findViewById(R.id.filter_period).setOnClickListener(v -> {
            String[] labels = {"全部时间", "最近 24 小时", "最近 7 天", "最近 30 天", "自定义开始日期"};
            new MaterialAlertDialogBuilder(this).setTitle("投稿时间").setItems(labels, (d, which) -> {
                if (which == 0) { options.startDate = null; options.endDate = null; }
                else if (which < 4) {
                    int days = which == 1 ? 1 : which == 2 ? 7 : 30;
                    options.endDate = dateDaysAgo(0);
                    options.startDate = dateDaysAgo(days);
                } else {
                    Calendar calendar = Calendar.getInstance();
                    new DatePickerDialog(this, (x, year, month, day) -> {
                        options.startDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                        options.endDate = dateDaysAgo(0);
                        ((MaterialButton) v).setText(options.startDate + " 起");
                        search();
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
                    return;
                }
                ((MaterialButton) v).setText(labels[which]);
                search();
            }).show();
        });
        findViewById(R.id.filter_ai).setOnClickListener(v -> {
            String[] labels = {"AI 不限", "隐藏 AI", "仅 AI"};
            new MaterialAlertDialogBuilder(this).setTitle("AI 作品").setItems(labels, (d, which) -> {
                options.aiType = which;
                ((MaterialButton) v).setText(labels[which]);
                search();
            }).show();
        });
        findViewById(R.id.filter_r18).setOnClickListener(v -> {
            String[] labels = repository.session().isR18Enabled()
                    ? new String[]{"隐藏 R18", "分级不限", "仅 R18"}
                    : new String[]{"隐藏 R18（请在设置中开启入口）"};
            new MaterialAlertDialogBuilder(this).setTitle("年龄分级").setItems(labels, (d, which) -> {
                options.r18 = which == 1 ? "all" : which == 2 ? "only" : "hide";
                ((MaterialButton) v).setText(which == 1 ? "分级不限" : which == 2 ? "仅 R18" : "隐藏 R18");
                search();
            }).show();
        });
    }

    private interface Pick { void apply(String value); }

    private void choose(String title, String[] labels, String[] values, Pick pick) {
        new MaterialAlertDialogBuilder(this).setTitle(title)
                .setItems(labels, (dialog, which) -> pick.apply(values[which])).show();
    }

    private String dateDaysAgo(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
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
    @Override protected void onDestroy() {
        if (pendingSuggestion != null) suggestionHandler.removeCallbacks(pendingSuggestion);
        suggestionExecutor.shutdownNow();
        executor.shutdownNow();
        super.onDestroy();
    }
}
