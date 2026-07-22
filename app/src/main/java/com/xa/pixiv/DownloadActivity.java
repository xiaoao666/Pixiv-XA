package com.xa.pixiv;

import android.app.DownloadManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.xa.pixiv.util.DownloadStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DownloadActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DownloadAdapter adapter;
    private TextView empty;
    private final Runnable refresh = new Runnable() { @Override public void run() { reload(); handler.postDelayed(this, 900L); } };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_downloads);
        findViewById(R.id.download_toolbar).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        empty = findViewById(R.id.download_empty);
        RecyclerView list = findViewById(R.id.download_list); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadAdapter(); list.setAdapter(adapter);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private void reload() {
        DownloadStore store = new DownloadStore(this);
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        List<Row> rows = new ArrayList<>();
        for (long id : store.ids()) {
            try (Cursor c = manager.query(new DownloadManager.Query().setFilterById(id))) {
                if (!c.moveToFirst()) { store.remove(id); continue; }
                long done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                rows.add(new Row(id, store.title(id), store.author(id), status, done, total));
            }
        }
        adapter.set(rows); empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private final class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.Holder> {
        private final List<Row> rows = new ArrayList<>();
        void set(List<Row> values) { rows.clear(); rows.addAll(values); notifyDataSetChanged(); }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) { return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_download,p,false)); }
        @Override public void onBindViewHolder(@NonNull Holder h,int p) {
            Row r=rows.get(p); int pc=r.total>0?(int)(r.done*100/r.total):0; h.title.setText(r.title);
            h.progress.setIndeterminate(r.total<=0&&r.status==DownloadManager.STATUS_RUNNING); h.progress.setProgressCompat(pc,true);
            h.state.setText(label(r.status)+" · "+pc+"% · "+bytes(r.done)+" / "+bytes(r.total)+"\n"+r.author);
            h.remove.setOnClickListener(v->{((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).remove(r.id);new DownloadStore(DownloadActivity.this).remove(r.id);reload();});
        }
        @Override public int getItemCount(){return rows.size();}
        final class Holder extends RecyclerView.ViewHolder { final TextView title,state; final LinearProgressIndicator progress; final View remove;
            Holder(View v){super(v);title=v.findViewById(R.id.download_title);state=v.findViewById(R.id.download_state);progress=v.findViewById(R.id.download_progress);remove=v.findViewById(R.id.download_remove);} }
    }
    private static String label(int s){if(s==DownloadManager.STATUS_SUCCESSFUL)return"已完成";if(s==DownloadManager.STATUS_RUNNING)return"下载中";if(s==DownloadManager.STATUS_PAUSED)return"已暂停";if(s==DownloadManager.STATUS_FAILED)return"失败";return"排队中";}
    private static String bytes(long n){if(n<0)return"--";if(n<1048576)return String.format(Locale.CHINA,"%.1f KB",n/1024f);return String.format(Locale.CHINA,"%.1f MB",n/1048576f);}
    private static final class Row{final long id,done,total;final String title,author;final int status;Row(long i,String t,String a,int s,long d,long z){id=i;title=t;author=a;status=s;done=d;total=z;}}
}
