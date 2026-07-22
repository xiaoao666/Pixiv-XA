package com.xa.pixiv;

import android.app.DownloadManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.appbar.MaterialToolbar;
import com.xa.pixiv.data.PixivImages;
import com.xa.pixiv.util.DownloadStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DownloadActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DownloadAdapter adapter;
    private View empty;
    private TextView runningCount;
    private TextView completedCount;
    private TextView taskCount;
    private final Runnable refresh = new Runnable() { @Override public void run() { reload(); handler.postDelayed(this, 900L); } };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_downloads);
        ((MaterialToolbar) findViewById(R.id.download_toolbar)).setNavigationOnClickListener(
                v -> getOnBackPressedDispatcher().onBackPressed());
        empty = findViewById(R.id.download_empty);
        runningCount = findViewById(R.id.download_running_count);
        completedCount = findViewById(R.id.download_completed_count);
        taskCount = findViewById(R.id.download_task_count);
        RecyclerView list = findViewById(R.id.download_list); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadAdapter(); list.setAdapter(adapter);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private void reload() {
        DownloadStore store = new DownloadStore(this);
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        List<Row> rows = new ArrayList<>();
        int running = 0;
        int completed = 0;
        for (long id : store.ids()) {
            try (Cursor c = manager.query(new DownloadManager.Query().setFilterById(id))) {
                if (!c.moveToFirst()) { store.remove(id); continue; }
                long done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                String preview = store.preview(id);
                if (preview.isEmpty() && status == DownloadManager.STATUS_SUCCESSFUL) {
                    int localIndex = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    if (localIndex >= 0) preview = c.getString(localIndex);
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL) completed++;
                else if (status == DownloadManager.STATUS_RUNNING
                        || status == DownloadManager.STATUS_PENDING
                        || status == DownloadManager.STATUS_PAUSED) running++;
                rows.add(new Row(id, store.title(id), store.author(id), store.artworkId(id),
                        preview, status, done, total));
            }
        }
        runningCount.setText(String.valueOf(running));
        completedCount.setText(String.valueOf(completed));
        taskCount.setText(rows.isEmpty() ? "暂无任务" : rows.size() + " 个任务");
        adapter.set(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private final class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.Holder> {
        private final List<Row> rows = new ArrayList<>();
        void set(List<Row> values) { rows.clear(); rows.addAll(values); notifyDataSetChanged(); }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) { return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_download,p,false)); }
        @Override public void onBindViewHolder(@NonNull Holder h,int p) {
            Row r=rows.get(p); int pc=r.total>0?(int)(r.done*100/r.total):0; h.title.setText(r.title);
            h.author.setText(r.artworkId > 0 ? r.author + "  ·  ID " + r.artworkId : r.author);
            h.progress.setIndeterminate(r.total<=0&&r.status==DownloadManager.STATUS_RUNNING); h.progress.setProgressCompat(pc,true);
            h.state.setText(label(r.status)+"  ·  "+pc+"%  ·  "+bytes(r.done)+" / "+bytes(r.total));
            Glide.with(h.preview).clear(h.preview);
            if (r.preview == null || r.preview.isEmpty()) {
                h.preview.setImageResource(R.drawable.ic_launcher_foreground);
            } else if (r.preview.startsWith("http://") || r.preview.startsWith("https://")) {
                Glide.with(h.preview).load(PixivImages.glide(DownloadActivity.this, r.preview))
                        .centerCrop().placeholder(R.drawable.bg_download_preview)
                        .error(R.drawable.ic_launcher_foreground).into(h.preview);
            } else {
                Glide.with(h.preview).load(Uri.parse(r.preview)).centerCrop()
                        .placeholder(R.drawable.bg_download_preview)
                        .error(R.drawable.ic_launcher_foreground).into(h.preview);
            }
            h.remove.setOnClickListener(v->{((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).remove(r.id);new DownloadStore(DownloadActivity.this).remove(r.id);reload();});
        }
        @Override public int getItemCount(){return rows.size();}
        final class Holder extends RecyclerView.ViewHolder { final TextView title,author,state; final ImageView preview; final LinearProgressIndicator progress; final View remove;
            Holder(View v){super(v);preview=v.findViewById(R.id.download_preview);title=v.findViewById(R.id.download_title);author=v.findViewById(R.id.download_author);state=v.findViewById(R.id.download_state);progress=v.findViewById(R.id.download_progress);remove=v.findViewById(R.id.download_remove);} }
    }
    private static String label(int s){if(s==DownloadManager.STATUS_SUCCESSFUL)return"已完成";if(s==DownloadManager.STATUS_RUNNING)return"下载中";if(s==DownloadManager.STATUS_PAUSED)return"已暂停";if(s==DownloadManager.STATUS_FAILED)return"失败";return"排队中";}
    private static String bytes(long n){if(n<0)return"--";if(n<1048576)return String.format(Locale.CHINA,"%.1f KB",n/1024f);return String.format(Locale.CHINA,"%.1f MB",n/1048576f);}
    private static final class Row{final long id,artworkId,done,total;final String title,author,preview;final int status;Row(long i,String t,String a,long w,String p,int s,long d,long z){id=i;title=t;author=a;artworkId=w;preview=p;status=s;done=d;total=z;}}
}
