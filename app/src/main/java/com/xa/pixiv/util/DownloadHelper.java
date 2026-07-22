package com.xa.pixiv.util;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivImages;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class DownloadHelper {
    private static final int REQUEST_STORAGE = 4102;

    private DownloadHelper() {}

    public static void save(Activity activity, ArtWork work) {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            Toast.makeText(activity, "授权后再点一次下载", Toast.LENGTH_SHORT).show();
            return;
        }
        if (work.isLocal()) saveLocal(activity, work);
        else enqueueRemote(activity, work);
    }

    private static void enqueueRemote(Activity activity, ArtWork work) {
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(PixivImages.rewrite(activity, work.getOriginalUrl())));
            request.addRequestHeader("Referer", "https://app-api.pixiv.net/");
            request.setTitle(work.getTitle());
            request.setDescription("Pixiv XA · " + work.getAuthor());
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES,
                    "PixivXA/" + fileName(work) + ".jpg");
            long id = manager.enqueue(request);
            new DownloadStore(activity).add(id, work);
            Toast.makeText(activity, "已加入下载队列，可在我的画室查看进度", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, "下载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void saveLocal(Activity activity, ArtWork work) {
        new Thread(() -> {
            try {
                Bitmap bitmap = BitmapFactory.decodeResource(activity.getResources(), work.getImageRes());
                if (bitmap == null) throw new IllegalStateException("无法读取本地图片");
                String name = fileName(work) + ".jpg";
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixivXA");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("无法创建媒体文件");
                    try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                        if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) throw new IllegalStateException("写入失败");
                    }
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    activity.getContentResolver().update(uri, values, null, null);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PixivXA");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建下载目录");
                    try (OutputStream out = new FileOutputStream(new File(dir, name))) {
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) throw new IllegalStateException("写入失败");
                    }
                }
                bitmap.recycle();
                activity.runOnUiThread(() -> Toast.makeText(activity, "已保存到 Pictures/PixivXA", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "xa-local-save").start();
    }

    private static String fileName(ArtWork work) {
        String raw = work.getId() + "_" + work.getTitle();
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
    }
}
