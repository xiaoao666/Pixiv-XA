package com.xa.pixiv.ui;

import androidx.recyclerview.widget.StaggeredGridLayoutManager;

/** Small helpers shared by the staggered artwork grids. */
public final class GridScroll {
    private GridScroll() { }

    /** Largest visible adapter position across all spans. */
    public static int lastVisible(StaggeredGridLayoutManager manager) {
        int[] positions = manager.findLastVisibleItemPositions(null);
        int max = 0;
        for (int value : positions) if (value > max) max = value;
        return max;
    }
}
