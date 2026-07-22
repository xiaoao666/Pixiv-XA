package com.xa.pixiv.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.xa.pixiv.DownloadActivity;
import com.xa.pixiv.FeedActivity;
import com.xa.pixiv.LoginActivity;
import com.xa.pixiv.R;
import com.xa.pixiv.RankingActivity;
import com.xa.pixiv.data.HistoryStore;
import com.xa.pixiv.data.ArtPage;
import com.xa.pixiv.data.NetworkSettings;
import com.xa.pixiv.data.PixivRepository;
import com.xa.pixiv.data.PixivUser;
import com.xa.pixiv.data.PixivImages;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LibraryFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PixivRepository repository;
    private HistoryStore historyStore;
    private TextView profileName;
    private TextView profileCaption;
    private TextView statBookmarks;
    private TextView statWorks;
    private TextView statHistory;
    private TextView loginButton;
    private ImageView avatar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = new PixivRepository(requireContext());
        historyStore = new HistoryStore(requireContext());
        profileName = view.findViewById(R.id.profile_name);
        profileCaption = view.findViewById(R.id.profile_caption);
        statBookmarks = view.findViewById(R.id.stat_bookmarks);
        statWorks = view.findViewById(R.id.stat_works);
        statHistory = view.findViewById(R.id.stat_history);
        loginButton = view.findViewById(R.id.login_button);
        avatar = view.findViewById(R.id.profile_avatar);

        loginButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), LoginActivity.class)));
        view.findViewById(R.id.bookmarks_button).setOnClickListener(v -> openFeed("bookmarks", true));
        view.findViewById(R.id.downloads_button).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DownloadActivity.class)));
        view.findViewById(R.id.following_button).setOnClickListener(v -> openFeed("following", true));
        view.findViewById(R.id.history_button).setOnClickListener(v -> openFeed("history", false));

        View r18Button = view.findViewById(R.id.r18_button);
        MaterialSwitch r18Switch = view.findViewById(R.id.r18_switch);
        r18Switch.setChecked(repository.session().isR18Enabled());
        r18Button.setVisibility(r18Switch.isChecked() ? View.VISIBLE : View.GONE);
        r18Switch.setOnCheckedChangeListener((button, checked) -> {
            repository.session().setR18Enabled(checked);
            r18Button.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked) Toast.makeText(requireContext(),
                    "已开启 R18 入口；Pixiv 账号本身也需要允许成人内容", Toast.LENGTH_LONG).show();
        });
        r18Button.setOnClickListener(v -> startActivity(new Intent(requireContext(), RankingActivity.class)
                .putExtra(RankingActivity.EXTRA_R18, true)));

        view.findViewById(R.id.theme_button).setOnClickListener(v -> toggleTheme());
        TextView network = view.findViewById(R.id.network_button);
        NetworkSettings settings = new NetworkSettings(requireContext());
        renderNetwork(network, settings.mode());
        network.setOnClickListener(v -> showNetworkDialog(network, settings));
        TextView imageHost = view.findViewById(R.id.image_host_button);
        renderImageHost(imageHost);
        imageHost.setOnClickListener(v -> showImageHostDialog(imageHost));
        updateProfile();
    }

    private void showNetworkDialog(TextView network, NetworkSettings settings) {
        int selected = NetworkSettings.SYSTEM.equals(settings.mode()) ? 0
                : NetworkSettings.DIRECT.equals(settings.mode()) ? 2 : 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pixiv 网络模式")
                .setSingleChoiceItems(new String[]{"系统网络", "安全 DNS（推荐）", "国内直连 · Cronet QUIC"},
                        selected, (dialog, which) -> {
                            String mode = which == 0 ? NetworkSettings.SYSTEM
                                    : which == 2 ? NetworkSettings.DIRECT : NetworkSettings.DOH;
                            settings.setMode(mode);
                            renderNetwork(network, mode);
                            dialog.dismiss();
                            Toast.makeText(requireContext(), "网络模式已保存，重启 XA 后生效", Toast.LENGTH_LONG).show();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renderNetwork(TextView view, String mode) {
        String label = NetworkSettings.DIRECT.equals(mode) ? "国内直连 · API QUIC + 图片自动镜像"
                : NetworkSettings.SYSTEM.equals(mode) ? "系统网络" : "安全 DNS";
        view.setText("网络模式：" + label);
    }

    private void showImageHostDialog(TextView target) {
        String[] labels = {"自动（直连时使用 pixiv.re）", "Pixiv 官方", "pixiv.re（大陆推荐）", "pixiv.nl（备用）"};
        String[] values = {PixivImages.AUTO, PixivImages.OFFICIAL, PixivImages.PIXIV_RE, PixivImages.PIXIV_NL};
        String current = PixivImages.mode(requireContext());
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        new MaterialAlertDialogBuilder(requireContext()).setTitle("图片加速源")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    PixivImages.setMode(requireContext(), values[which]);
                    renderImageHost(target);
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "图片源已切换，新加载的图片立即生效", Toast.LENGTH_LONG).show();
                }).setNegativeButton("取消", null).show();
    }

    private void renderImageHost(TextView target) {
        target.setText("图片加速源：" + PixivImages.label(requireContext()));
    }

    private void openFeed(String mode, boolean loginRequired) {
        if (loginRequired && !repository.session().isLoggedIn()) {
            Toast.makeText(requireContext(), "请先登录 Pixiv", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(requireContext(), FeedActivity.class).putExtra(FeedActivity.EXTRA_MODE, mode));
    }

    @Override public void onResume() {
        super.onResume();
        if (repository != null) updateProfile();
    }

    private void updateProfile() {
        if (!isAdded()) return;
        boolean loggedIn = repository.session().isLoggedIn();
        statHistory.setText(historyStore.size() + "\n浏览记录");
        profileName.setText(loggedIn ? repository.session().getUserName() : "旅人 · Demo");
        profileCaption.setText(loggedIn ? "已连接 Pixiv · " + repository.session().getUserAccount()
                : "登录后同步 Pixiv 收藏、关注与创作数据");
        loginButton.setText(loggedIn ? "切换 Pixiv 账号" : "连接 Pixiv 账号");
        if (!loggedIn) {
            statBookmarks.setText("--\nPixiv 收藏");
            statWorks.setText("--\n创作");
            return;
        }
        statBookmarks.setText("…\nPixiv 收藏");
        statWorks.setText("…\n创作");
        long userId = repository.session().getUserId();
        executor.execute(() -> {
            try {
                PixivUser user = repository.loadUser(userId);
                ArtPage privateBookmarks = repository.loadBookmarks("private");
                int localBookmarks = repository.session().getBookmarkedIds().size();
                int visibleCount = Math.max(localBookmarks,
                        user.publicBookmarks + privateBookmarks.getItems().size());
                String bookmarkLabel = visibleCount
                        + (privateBookmarks.getNextUrl().isEmpty() ? "" : "+");
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> renderUser(user, bookmarkLabel));
            } catch (Exception error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    statBookmarks.setText("--\nPixiv 收藏");
                    statWorks.setText("--\n创作");
                });
            }
        });
    }

    private void renderUser(PixivUser user, String bookmarkLabel) {
        profileName.setText(user.name);
        profileCaption.setText("@" + user.account + " · Pixiv 云端资料");
        statBookmarks.setText(bookmarkLabel + "\nPixiv 收藏");
        statWorks.setText((user.illusts + user.manga) + "\n创作");
        if (!user.avatar.isEmpty()) {
            Glide.with(this).load(PixivImages.glide(requireContext(), user.avatar)).circleCrop().into(avatar);
        }
    }

    private void toggleTheme() {
        int current = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        AppCompatDelegate.setDefaultNightMode(current == Configuration.UI_MODE_NIGHT_YES
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
