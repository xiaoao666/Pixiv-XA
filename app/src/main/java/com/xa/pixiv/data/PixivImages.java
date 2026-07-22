package com.xa.pixiv.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

/** Central image-host routing, based on Shaft's image mirror abstraction. */
public final class PixivImages {
    public static final String AUTO = "auto";
    public static final String OFFICIAL = "official";
    public static final String PIXIV_RE = "pixiv_re";
    public static final String PIXIV_NL = "pixiv_nl";
    private static final String PREF = "xa_network";
    private static final String KEY = "image_host";

    private PixivImages() { }

    public static String mode(Context context) {
        return prefs(context).getString(KEY, AUTO);
    }

    public static void setMode(Context context, String mode) {
        prefs(context).edit().putString(KEY, mode).apply();
    }

    public static GlideUrl glide(Context context, String originalUrl) {
        return new GlideUrl(rewrite(context, originalUrl), new LazyHeaders.Builder()
                .addHeader("Referer", "https://app-api.pixiv.net/").build());
    }

    /**
     * Comment stamps live on Pixiv's static host. The commonly used image mirrors only
     * mirror i.pximg.net and return TLS/404 errors for s.pximg.net stamp paths, so stamps
     * must always keep the official static host even when artwork mirroring is enabled.
     */
    public static GlideUrl stamp(Context context, String originalUrl) {
        String officialUrl = originalUrl == null ? "" : originalUrl
                .replace("s.pixiv.re", "s.pximg.net")
                .replace("s.pixiv.nl", "s.pximg.net");
        return new GlideUrl(officialUrl, new LazyHeaders.Builder()
                .addHeader("Referer", "https://app-api.pixiv.net/").build());
    }

    public static String rewrite(Context context, String originalUrl) {
        if (originalUrl == null || originalUrl.isEmpty()) return "";
        String selected = mode(context);
        if (AUTO.equals(selected)) {
            selected = NetworkSettings.DIRECT.equals(new NetworkSettings(context).mode()) ? PIXIV_RE : OFFICIAL;
        }
        if (OFFICIAL.equals(selected)) return originalUrl;
        String imageHost = PIXIV_NL.equals(selected) ? "i.pixiv.nl" : "i.pixiv.re";
        return originalUrl.replace("i.pximg.net", imageHost);
    }

    public static String label(Context context) {
        String selected = mode(context);
        if (OFFICIAL.equals(selected)) return "Pixiv 官方";
        if (PIXIV_RE.equals(selected)) return "pixiv.re（大陆推荐）";
        if (PIXIV_NL.equals(selected)) return "pixiv.nl（备用）";
        return "自动（直连时 pixiv.re）";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
