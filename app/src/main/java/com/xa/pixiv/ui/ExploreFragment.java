package com.xa.pixiv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.xa.pixiv.FeedActivity;
import com.xa.pixiv.R;
import com.xa.pixiv.RankingActivity;
import com.xa.pixiv.SearchActivity;
import com.xa.pixiv.TagsActivity;

/** Compact discovery dashboard. Search and ranking results intentionally live on full-screen pages. */
public final class ExploreFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.discover_search).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SearchActivity.class)));
        view.findViewById(R.id.discover_ranking).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RankingActivity.class)));
        view.findViewById(R.id.discover_tags).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TagsActivity.class)));
        view.findViewById(R.id.discover_latest_illust).setOnClickListener(v -> openFeed("latest_illust"));
        view.findViewById(R.id.discover_latest_manga).setOnClickListener(v -> openFeed("latest_manga"));
        view.findViewById(R.id.discover_latest_novel).setOnClickListener(v -> openFeed("latest_novel"));
        view.findViewById(R.id.discover_following).setOnClickListener(v -> openFeed("following"));
        view.findViewById(R.id.discover_history).setOnClickListener(v -> openFeed("history"));
    }

    private void openFeed(String mode) {
        startActivity(new Intent(requireContext(), FeedActivity.class)
                .putExtra(FeedActivity.EXTRA_MODE, mode));
    }
}
