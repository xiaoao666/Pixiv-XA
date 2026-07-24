package com.xa.pixiv.ui;

import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.google.android.material.chip.Chip;
import com.xa.pixiv.R;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivImages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ArtAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HERO = 0;
    private static final int TYPE_ART = 1;

    public interface Listener {
        void onOpen(ArtWork work, ImageView sharedImage);
        void onBookmark(ArtWork work, int adapterPosition);
        void onFilter(String type);
    }

    private final List<ArtWork> works = new ArrayList<>();
    private final boolean showHero;
    private final Listener listener;
    private boolean loggedIn;

    public ArtAdapter(boolean showHero, Listener listener) {
        this.showHero = showHero;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setWorks(List<ArtWork> values, boolean loggedIn) {
        works.clear();
        works.addAll(values);
        this.loggedIn = loggedIn;
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        if (showHero && position == 0) return Long.MIN_VALUE;
        return works.get(position - (showHero ? 1 : 0)).getId();
    }

    @Override public int getItemViewType(int position) {
        return showHero && position == 0 ? TYPE_HERO : TYPE_ART;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HERO) {
            return new HeroHolder(inflater.inflate(R.layout.item_home_hero, parent, false));
        }
        return new ArtHolder(inflater.inflate(R.layout.item_art_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeroHolder) {
            HeroHolder hero = (HeroHolder) holder;
            fillSpan(hero.itemView);
            hero.modeBadge.setText(loggedIn ? "PIXIV · PERSONAL FEED" : "DEMO · LOCAL GALLERY");
            hero.bind(works, listener);
            return;
        }
        int index = position - (showHero ? 1 : 0);
        ((ArtHolder) holder).bind(works.get(index), listener, position);
    }

    @Override public int getItemCount() { return works.size() + (showHero ? 1 : 0); }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof HeroHolder) fillSpan(holder.itemView);
    }

    /** Hero row must span both columns when hosted by a staggered grid. */
    private static void fillSpan(View itemView) {
        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        if (params instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) params).setFullSpan(true);
        }
    }

    private static final class HeroHolder extends RecyclerView.ViewHolder {
        final ViewPager2 pager;
        final TextView modeBadge;
        final TextView title;
        final TextView indicator;
        final TextView next;
        final Chip all;
        final Chip illust;
        final Chip manga;
        final Chip novel;

        HeroHolder(View itemView) {
            super(itemView);
            pager = itemView.findViewById(R.id.hero_pager);
            modeBadge = itemView.findViewById(R.id.mode_badge);
            title = itemView.findViewById(R.id.hero_title);
            indicator = itemView.findViewById(R.id.hero_indicator);
            next = itemView.findViewById(R.id.daily_next);
            all = itemView.findViewById(R.id.filter_all);
            illust = itemView.findViewById(R.id.filter_illust);
            manga = itemView.findViewById(R.id.filter_manga);
            novel = itemView.findViewById(R.id.filter_novel);
        }

        void bind(List<ArtWork> works, Listener listener) {
            HeroPagerAdapter heroAdapter = new HeroPagerAdapter(listener::onOpen);
            heroAdapter.submit(works);
            pager.setAdapter(heroAdapter);
            pager.setPageTransformer((page, position) -> {
                float factor = Math.max(0f, 1f - Math.abs(position));
                page.setAlpha(.72f + factor * .28f);
                page.setScaleX(.94f + factor * .06f);
            });
            ViewPager2.OnPageChangeCallback callback = new ViewPager2.OnPageChangeCallback() {
                @Override public void onPageSelected(int position) {
                    ArtWork work = heroAdapter.item(position);
                    if (work == null) return;
                    title.setText(work.getTitle());
                    StringBuilder dots = new StringBuilder();
                    for (int i = 0; i < heroAdapter.getItemCount(); i++) dots.append(i == position ? "● " : "○ ");
                    indicator.setText(dots.toString().trim());
                }
            };
            pager.registerOnPageChangeCallback(callback);
            if (heroAdapter.getItemCount() > 0) callback.onPageSelected(0);
            next.setOnClickListener(v -> {
                int count = heroAdapter.getItemCount();
                if (count > 1) pager.setCurrentItem((pager.getCurrentItem() + 1) % count, true);
            });
            all.setOnClickListener(v -> listener.onFilter("all"));
            illust.setOnClickListener(v -> listener.onFilter("illust"));
            manga.setOnClickListener(v -> listener.onFilter("manga"));
            novel.setOnClickListener(v -> listener.onFilter("novel"));
        }
    }

    private static final class ArtHolder extends RecyclerView.ViewHolder {
        final RatioImageView image;
        final TextView title;
        final TextView author;
        final TextView badge;
        final ImageButton bookmark;

        ArtHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.art_image);
            title = itemView.findViewById(R.id.art_title);
            author = itemView.findViewById(R.id.art_author);
            badge = itemView.findViewById(R.id.page_badge);
            bookmark = itemView.findViewById(R.id.bookmark_button);
        }

        void bind(ArtWork work, Listener listener, int adapterPosition) {
            title.setText(work.getTitle());
            author.setText("by " + work.getAuthor());
            badge.setText(work.getPageCount() > 1 ? "P" + work.getPageCount() : work.getType().toUpperCase(Locale.ROOT));
            image.setAspect(work.getWidth(), work.getHeight());
            image.setTransitionName("art_image_" + work.getId());
            ColorStateList tint = ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(),
                    work.isBookmarked() ? R.color.pink_500 : R.color.xa_muted));
            ImageViewCompat.setImageTintList(bookmark, tint);
            bookmark.setAlpha(work.isBookmarked() ? 1f : .72f);

            if (work.isLocal()) {
                Glide.with(image).load(work.getImageRes()).fitCenter().into(image);
            } else {
                GlideUrl url = PixivImages.glide(itemView.getContext(), work.getPreviewUrl());
                Glide.with(image).load(url)
                        .placeholder(new ColorDrawable(ContextCompat.getColor(itemView.getContext(), R.color.xa_surface_soft)))
                        .fitCenter().into(image);
            }
            itemView.setOnClickListener(v -> listener.onOpen(work, image));
            bookmark.setOnClickListener(v -> listener.onBookmark(work, adapterPosition));
        }
    }
}
