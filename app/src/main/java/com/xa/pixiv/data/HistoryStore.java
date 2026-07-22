package com.xa.pixiv.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class HistoryStore {
    private static final int MAX = 120;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Type type = new TypeToken<List<ArtWork>>(){}.getType();
    public HistoryStore(Context context) { prefs = context.getSharedPreferences("xa_history", Context.MODE_PRIVATE); }
    public synchronized void record(ArtWork work) {
        List<ArtWork> items = all();
        items.removeIf(item -> item.getId() == work.getId());
        items.add(0, work);
        if (items.size() > MAX) items = new ArrayList<>(items.subList(0, MAX));
        prefs.edit().putString("items", gson.toJson(items, type)).apply();
    }
    public synchronized List<ArtWork> all() {
        try { List<ArtWork> items = gson.fromJson(prefs.getString("items", "[]"), type); return items == null ? new ArrayList<>() : new ArrayList<>(items); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }
    public int size() { return all().size(); }
    public void clear() { prefs.edit().remove("items").apply(); }
}
