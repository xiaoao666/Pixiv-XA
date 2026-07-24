package com.xa.pixiv.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.xa.pixiv.data.NetworkSettings;
import com.xa.pixiv.data.PixivImages;
import com.xa.pixiv.data.SecurePixivDns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads an original artwork into app cache while reporting byte-level progress. */
public final class OriginalImageLoader {
    public interface Listener {
        void onProgress(long loaded, long total);
        void onReady(File file);
        void onError(Exception error);
    }

    public static final class Handle {
        private volatile boolean cancelled;
        private Call call;
        public void cancel() {
            cancelled = true;
            if (call != null) call.cancel();
        }
    }

    private final Context context;
    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File cacheDirectory;

    public OriginalImageLoader(Context context) {
        this.context = context.getApplicationContext();
        NetworkSettings settings = new NetworkSettings(this.context);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true);
        if (NetworkSettings.DOH.equals(settings.mode())
                || NetworkSettings.DIRECT.equals(settings.mode())) {
            builder.dns(new SecurePixivDns());
        }
        client = builder.build();
        cacheDirectory = new File(this.context.getCacheDir(), "original_artworks");
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs();
    }

    public Handle load(String originalUrl, Listener listener) {
        Handle handle = new Handle();
        if (originalUrl == null || originalUrl.isEmpty()) {
            main.post(() -> listener.onError(new IOException("原图地址为空")));
            return handle;
        }
        List<String> candidates = candidates(originalUrl);
        File cached = new File(cacheDirectory, sha256(originalUrl) + ".image");
        if (cached.isFile() && cached.length() > 0) {
            main.post(() -> {
                if (!handle.cancelled) {
                    listener.onProgress(cached.length(), cached.length());
                    listener.onReady(cached);
                }
            });
            return handle;
        }

        File partial = new File(cacheDirectory, cached.getName() + ".part");
        enqueue(candidates, 0, cached, partial, handle, listener, null);
        return handle;
    }

    private void enqueue(List<String> candidates, int index, File cached, File partial,
                         Handle handle, Listener listener, Exception previousError) {
        if (handle.cancelled) return;
        if (index >= candidates.size()) {
            Exception error = previousError == null ? new IOException("所有原图线路均不可用") : previousError;
            main.post(() -> listener.onError(error));
            return;
        }
        if (partial.exists()) partial.delete();
        main.post(() -> {
            if (!handle.cancelled && index > 0) listener.onProgress(0L, -1L);
        });
        Request request = new Request.Builder().url(candidates.get(index))
                .header("Referer", "https://app-api.pixiv.net/")
                .header("User-Agent", "PixivAndroidApp/5.0.234")
                .build();
        Call call = client.newCall(request);
        handle.call = call;
        call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) {
                partial.delete();
                enqueue(candidates, index + 1, cached, partial, handle, listener, error);
            }

            @Override public void onResponse(Call call, Response response) {
                try (Response result = response) {
                    if (!result.isSuccessful()) throw new IOException("原图 HTTP " + result.code());
                    ResponseBody body = result.body();
                    if (body == null) throw new IOException("原图响应为空");
                    long total = body.contentLength();
                    long loaded = 0L;
                    long lastUpdate = 0L;
                    byte[] buffer = new byte[64 * 1024];
                    try (InputStream input = body.byteStream();
                         FileOutputStream output = new FileOutputStream(partial)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            if (handle.cancelled) throw new IOException("cancelled");
                            output.write(buffer, 0, read);
                            loaded += read;
                            long now = System.currentTimeMillis();
                            if (now - lastUpdate >= 100L || (total > 0 && loaded >= total)) {
                                long progressLoaded = loaded;
                                lastUpdate = now;
                                main.post(() -> {
                                    if (!handle.cancelled) listener.onProgress(progressLoaded, total);
                                });
                            }
                        }
                    }
                    if (cached.exists()) cached.delete();
                    if (!partial.renameTo(cached)) throw new IOException("无法写入原图缓存");
                    long finalSize = cached.length();
                    main.post(() -> {
                        if (!handle.cancelled) {
                            listener.onProgress(finalSize, total > 0 ? total : finalSize);
                            listener.onReady(cached);
                        }
                    });
                } catch (Exception error) {
                    partial.delete();
                    enqueue(candidates, index + 1, cached, partial, handle, listener, error);
                }
            }
        });
    }

    private List<String> candidates(String originalUrl) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(PixivImages.rewrite(context, originalUrl));
        values.add(originalUrl.replace("i.pixiv.re", "i.pximg.net")
                .replace("i.pixiv.nl", "i.pximg.net"));
        values.add(originalUrl.replace("i.pximg.net", "i.pixiv.re")
                .replace("i.pixiv.nl", "i.pixiv.re"));
        values.add(originalUrl.replace("i.pximg.net", "i.pixiv.nl")
                .replace("i.pixiv.re", "i.pixiv.nl"));
        values.remove("");
        return new ArrayList<>(values);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte item : digest) out.append(String.format(java.util.Locale.US, "%02x", item));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
