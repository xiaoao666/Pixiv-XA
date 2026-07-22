package com.xa.pixiv.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.xa.pixiv.data.ArtWork;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DownloadStore {
    private static final String PREFS = "xa_downloads";
    private final SharedPreferences prefs;
    public DownloadStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public void add(long id, ArtWork work) {
        Set<String> ids = new HashSet<>(prefs.getStringSet("ids", Collections.emptySet()));
        ids.add(String.valueOf(id));
        prefs.edit().putStringSet("ids", ids).putString("title_" + id, work.getTitle())
                .putString("author_" + id, work.getAuthor())
                .putString("preview_" + id, work.getPreviewUrl())
                .putLong("artwork_" + id, work.getId()).apply();
    }
    public void remove(long id) {
        Set<String> ids = new HashSet<>(prefs.getStringSet("ids", Collections.emptySet()));
        ids.remove(String.valueOf(id));
        prefs.edit().putStringSet("ids", ids)
                .remove("title_" + id)
                .remove("author_" + id)
                .remove("preview_" + id)
                .remove("artwork_" + id)
                .apply();
    }
    public List<Long> ids() {
        List<Long> out = new ArrayList<>();
        for (String value : prefs.getStringSet("ids", Collections.emptySet())) try { out.add(Long.parseLong(value)); } catch (Exception ignored) {}
        out.sort(Collections.reverseOrder()); return out;
    }
    public String title(long id) { return prefs.getString("title_" + id, "Pixiv 作品"); }
    public String author(long id) { return prefs.getString("author_" + id, "Pixiv Creator"); }
    public String preview(long id) { return prefs.getString("preview_" + id, ""); }
    public long artworkId(long id) { return prefs.getLong("artwork_" + id, 0L); }
}
