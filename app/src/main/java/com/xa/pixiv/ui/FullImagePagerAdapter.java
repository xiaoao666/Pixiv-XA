package com.xa.pixiv.ui;

import android.content.Context;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.github.chrisbanes.photoview.PhotoView;
import com.xa.pixiv.R;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivImages;
import com.xa.pixiv.util.OriginalImageLoader;

import java.io.File;
import java.util.List;

/** Shows cached previews immediately and downloads originals only for explicitly selected pages. */
public final class FullImagePagerAdapter extends RecyclerView.Adapter<FullImagePagerAdapter.Holder> {
    public interface ProgressListener {
        void onStart(int position);
        void onProgress(int position, long loaded, long total);
        void onReady(int position);
        void onError(int position, Exception error);
    }

    private final ArtWork work;
    private final List<String> originals;
    private final List<String> previews;
    private final boolean[] requested;
    private final boolean[] loading;
    private final boolean[] failed;
    private final File[] files;
    private final OriginalImageLoader.Handle[] handles;
    private final OriginalImageLoader loader;
    private final ProgressListener listener;

    public FullImagePagerAdapter(Context context, ArtWork work, ProgressListener listener) {
        this.work = work;
        this.originals = work.getPageUrls();
        this.previews = work.getPagePreviewUrls();
        int count = Math.max(1, originals.size());
        requested = new boolean[count];
        loading = new boolean[count];
        failed = new boolean[count];
        files = new File[count];
        handles = new OriginalImageLoader.Handle[count];
        loader = new OriginalImageLoader(context);
        this.listener = listener;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PhotoView image = new PhotoView(parent.getContext());
        image.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image.setMinimumScale(1f);
        image.setMediumScale(2.5f);
        image.setMaximumScale(6f);
        image.setZoomTransitionDuration(220);
        image.setAllowParentInterceptOnEdge(true);
        image.setBackgroundColor(parent.getContext().getColor(R.color.ink_950));
        return new Holder(image);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        if (holder.boundPosition != position) {
            holder.image.setScale(1f, false);
            holder.boundPosition = position;
        }
        holder.image.setContentDescription("原图第 " + (position + 1) + " 页");
        if (work.isLocal()) {
            Glide.with(holder.image).load(work.getImageRes()).fitCenter().into(holder.image);
            return;
        }
        if (files[position] != null && files[position].isFile()) {
            Glide.with(holder.image).load(files[position]).fitCenter()
                    .transition(DrawableTransitionOptions.withCrossFade(180)).into(holder.image);
        } else {
            String preview = previewAt(position);
            Glide.with(holder.image).load(PixivImages.glide(holder.image.getContext(), preview))
                    .fitCenter().into(holder.image);
            if (requested[position] && !loading[position] && !failed[position]) start(position);
        }
    }

    public void requestOriginal(int position) {
        if (position < 0 || position >= getItemCount()) return;
        if (work.isLocal() || files[position] != null) {
            listener.onReady(position);
            return;
        }
        requested[position] = true;
        failed[position] = false;
        notifyItemChanged(position);
    }

    public boolean isReady(int position) {
        return work.isLocal() || (position >= 0 && position < files.length && files[position] != null);
    }

    public void cancelAll() {
        for (OriginalImageLoader.Handle handle : handles) if (handle != null) handle.cancel();
    }

    private void start(int position) {
        loading[position] = true;
        listener.onStart(position);
        String url = position < originals.size() ? originals.get(position) : work.getOriginalUrl();
        handles[position] = loader.load(url, new OriginalImageLoader.Listener() {
            @Override public void onProgress(long loaded, long total) {
                listener.onProgress(position, loaded, total);
            }

            @Override public void onReady(File file) {
                loading[position] = false;
                files[position] = file;
                notifyItemChanged(position);
                listener.onReady(position);
            }

            @Override public void onError(Exception error) {
                loading[position] = false;
                failed[position] = true;
                listener.onError(position, error);
            }
        });
    }

    private String previewAt(int position) {
        if (position < previews.size() && !previews.get(position).isEmpty()) return previews.get(position);
        return work.getPreviewUrl();
    }

    @Override public int getItemCount() { return requested.length; }

    static final class Holder extends RecyclerView.ViewHolder {
        final PhotoView image;
        int boundPosition = RecyclerView.NO_POSITION;
        Holder(PhotoView image) { super(image); this.image = image; }
    }
}
