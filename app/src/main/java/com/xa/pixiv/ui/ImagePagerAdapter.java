package com.xa.pixiv.ui;

import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.xa.pixiv.R;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivImages;

import java.util.List;

public final class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.Holder> {
    public interface Listener { void onOpenPage(int position); }

    private final ArtWork work;
    private final List<String> pages;
    private final Listener listener;

    public ImagePagerAdapter(ArtWork work, Listener listener) {
        this.work = work;
        this.pages = work.getPageUrls();
        this.listener = listener;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AppCompatImageView image = new AppCompatImageView(parent.getContext());
        image.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(parent.getContext().getColor(R.color.ink_950));
        return new Holder(image);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.image.setContentDescription("作品第 " + (position + 1) + " 页");
        if (work.isLocal()) Glide.with(holder.image).load(work.getImageRes()).fitCenter().into(holder.image);
        else Glide.with(holder.image).load(PixivImages.glide(holder.image.getContext(), pages.get(position)))
                .fitCenter().into(holder.image);
        holder.image.setOnClickListener(v -> {
            if (listener != null) listener.onOpenPage(holder.getBindingAdapterPosition());
        });
    }

    @Override public int getItemCount() { return Math.max(1, pages.size()); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        Holder(ImageView image) { super(image); this.image = image; }
    }
}
