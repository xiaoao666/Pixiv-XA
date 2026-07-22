package com.xa.pixiv.ui;

import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.xa.pixiv.R;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivImages;
import java.util.ArrayList;
import java.util.List;

final class HeroPagerAdapter extends RecyclerView.Adapter<HeroPagerAdapter.Holder> {
    interface Listener { void onClick(ArtWork work, ImageView image); }
    private final List<ArtWork> items = new ArrayList<>();
    private final Listener listener;
    HeroPagerAdapter(Listener listener) { this.listener = listener; }
    void submit(List<ArtWork> works) {
        items.clear();
        items.addAll(works.subList(0, Math.min(6, works.size())));
        notifyDataSetChanged();
    }
    ArtWork item(int position) { return items.isEmpty() ? null : items.get(Math.min(position, items.size() - 1)); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hero_slide, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        ArtWork work = items.get(position);
        holder.image.setTransitionName("art_image_" + work.getId());
        if (work.isLocal()) Glide.with(holder.image).load(work.getImageRes()).centerCrop().into(holder.image);
        else Glide.with(holder.image).load(PixivImages.glide(holder.image.getContext(), work.getPreviewUrl()))
                .placeholder(new ColorDrawable(ContextCompat.getColor(holder.image.getContext(), R.color.xa_surface_soft)))
                .centerCrop().into(holder.image);
        holder.itemView.setOnClickListener(v -> listener.onClick(work, holder.image));
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        Holder(View view) { super(view); image = view.findViewById(R.id.hero_slide_image); }
    }
}
