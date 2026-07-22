package com.xa.pixiv.data;

import android.os.Build;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class PixivApiClient {
    private static final String BASE = "https://app-api.pixiv.net";
    private final OkHttpClient client;

    public PixivApiClient(Context context) {
        NetworkSettings settings=new NetworkSettings(context);OkHttpClient.Builder b=new OkHttpClient.Builder().connectTimeout(18,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS);
        if(NetworkSettings.DOH.equals(settings.mode()))b.dns(new SecurePixivDns());
        if(NetworkSettings.DIRECT.equals(settings.mode()))b.dns(new SecurePixivDns()).addInterceptor(new CronetInterceptor(context));
        client=b.build();
    }

    public ArtPage recommended(String accessToken) throws Exception {
        JSONObject json = get(accessToken, "/v1/illust/recommended", mapOf(
                "include_ranking_illusts", "false",
                "include_privacy_policy", "true",
                "filter", "for_android"
        ));
        return parsePage(json);
    }

    public ArtPage recommendedManga(String accessToken) throws Exception {
        return parsePage(get(accessToken, "/v1/manga/recommended", mapOf(
                "include_ranking_illusts", "false", "include_privacy_policy", "true", "filter", "for_android")));
    }

    public ArtPage recommendedNovels(String accessToken) throws Exception {
        return parseNovelPage(get(accessToken, "/v1/novel/recommended", mapOf(
                "include_ranking_novels", "false", "include_privacy_policy", "true")));
    }

    public ArtPage latest(String accessToken, String type) throws Exception {
        if ("novel".equals(type)) return parseNovelPage(get(accessToken, "/v1/novel/new", mapOf()));
        return parsePage(get(accessToken, "/v1/illust/new", mapOf(
                "content_type", "manga".equals(type) ? "manga" : "illust", "filter", "for_android")));
    }

    public ArtPage rankingNovels(String accessToken, String mode, String date) throws Exception {
        Map<String,String> query = mapOf("mode", mode, "filter", "for_android");
        if (date != null && !date.isEmpty()) query.put("date", date);
        return parseNovelPage(get(accessToken, "/v1/novel/ranking", query));
    }

    public List<TrendingTag> trendingTags(String accessToken, String type) throws Exception {
        JSONObject json = get(accessToken, "/v1/trending-tags/" + type, mapOf(
                "filter", "for_android", "include_translated_tag_results", "true"));
        JSONArray array = json.optJSONArray("trend_tags");
        List<TrendingTag> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                ArtWork sample = parseWork(item.optJSONObject("illust"));
                if (sample == null) sample = parseNovel(item.optJSONObject("novel"));
                result.add(new TrendingTag(item.optString("tag"), item.optString("translated_name"), sample));
            }
        }
        return result;
    }

    public ArtPage nextPage(String accessToken, String nextUrl) throws Exception {
        if (nextUrl == null || nextUrl.isEmpty()) return new ArtPage(new ArrayList<>(), "");
        Request request = baseRequest(accessToken, nextUrl).get().build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("Pixiv API HTTP " + response.code() + ": " + body);
            JSONObject json = new JSONObject(body);
            return json.has("novels") ? parseNovelPage(json) : parsePage(json);
        }
    }

    public ArtPage ranking(String accessToken, String mode) throws Exception { return ranking(accessToken, mode, null); }

    public ArtPage ranking(String accessToken, String mode, String date) throws Exception {
        Map<String,String> query = mapOf("mode", mode, "filter", "for_android");
        if (date != null && !date.isEmpty()) query.put("date", date);
        JSONObject json = get(accessToken, "/v1/illust/ranking", query);
        return parsePage(json);
    }

    public ArtPage following(String accessToken) throws Exception {
        return parsePage(get(accessToken, "/v2/illust/follow", mapOf(
                "restrict", "all",
                "filter", "for_android"
        )));
    }

    public ArtPage related(String accessToken, long illustId) throws Exception {
        return parsePage(get(accessToken, "/v2/illust/related", mapOf(
                "illust_id", String.valueOf(illustId),
                "filter", "for_android"
        )));
    }

    public ArtPage userIllusts(String accessToken, long userId) throws Exception {
        return parsePage(get(accessToken, "/v1/user/illusts", mapOf("user_id",String.valueOf(userId),"type","illust","filter","for_android")));
    }

    public PixivUser userDetail(String accessToken, long userId) throws Exception {
        JSONObject json=get(accessToken,"/v2/user/detail",mapOf("user_id",String.valueOf(userId),"filter","for_android"));
        JSONObject user=json.optJSONObject("user"), profile=json.optJSONObject("profile"), urls=user==null?null:user.optJSONObject("profile_image_urls");
        return new PixivUser(userId,user==null?"Pixiv Creator":user.optString("name"),user==null?"":user.optString("account"),
                user==null?"":user.optString("comment"),firstNonEmpty(urls,"medium","square_medium"),user!=null&&user.optBoolean("is_followed"),
                profile==null?0:profile.optInt("total_illusts"),profile==null?0:profile.optInt("total_manga"),
                profile==null?0:profile.optInt("total_illust_bookmarks_public"));
    }

    public ArtWork illustDetail(String accessToken, long illustId) throws Exception {
        JSONObject json = get(accessToken, "/v1/illust/detail", mapOf(
                "illust_id", String.valueOf(illustId), "filter", "for_android"));
        ArtWork work = parseWork(json.optJSONObject("illust"));
        if (work == null) throw new IOException("没有找到这个作品 ID");
        return work;
    }

    public List<PixivUser> searchUsers(String accessToken, String word) throws Exception {
        JSONObject json = get(accessToken, "/v1/search/user", mapOf(
                "word", word, "filter", "for_android"));
        JSONArray previews = json.optJSONArray("user_previews");
        List<PixivUser> result = new ArrayList<>();
        if (previews == null) return result;
        for (int i = 0; i < previews.length(); i++) {
            JSONObject preview = previews.optJSONObject(i);
            JSONObject user = preview == null ? null : preview.optJSONObject("user");
            if (user == null || user.optLong("id") <= 0) continue;
            JSONObject urls = user.optJSONObject("profile_image_urls");
            JSONArray illusts = preview.optJSONArray("illusts");
            result.add(new PixivUser(
                    user.optLong("id"),
                    user.optString("name", "Pixiv Creator"),
                    user.optString("account", ""),
                    user.optString("comment", ""),
                    firstNonEmpty(urls, "medium", "square_medium"),
                    user.optBoolean("is_followed", false),
                    illusts == null ? 0 : illusts.length(),
                    0,
                    0
            ));
        }
        return result;
    }

    public void setFollow(String accessToken,long userId,boolean follow) throws Exception {
        String path=follow?"/v1/user/follow/add":"/v1/user/follow/delete";FormBody.Builder body=new FormBody.Builder().add("user_id",String.valueOf(userId));
        if(follow)body.add("restrict","public");Request req=baseRequest(accessToken,BASE+path).post(body.build()).build();
        try(Response response=client.newCall(req).execute()){if(!response.isSuccessful())throw new IOException("关注操作失败：HTTP "+response.code());}
    }

    public ArtPage bookmarks(String accessToken, long userId) throws Exception {
        return bookmarks(accessToken, userId, "public");
    }

    public ArtPage bookmarks(String accessToken, long userId, String restrict) throws Exception {
        return parsePage(get(accessToken, "/v1/user/bookmarks/illust", mapOf(
                "user_id", String.valueOf(userId),
                "restrict", restrict,
                "filter", "for_android"
        )));
    }

    public List<ArtWork> search(String accessToken, String word) throws Exception {
        return search(accessToken, word, new SearchOptions()).getItems();
    }

    public ArtPage search(String accessToken, String word, SearchOptions options) throws Exception {
        Map<String,String> query = mapOf(
                "word", word,
                "sort", options.sort,
                "search_target", options.target,
                "include_translated_tag_results", "true",
                "merge_plain_keyword_results", "true",
                "filter", "for_android"
        );
        if (options.bookmarkMin != null) query.put("bookmark_num_min", String.valueOf(options.bookmarkMin));
        if (options.startDate != null) query.put("start_date", options.startDate);
        if (options.endDate != null) query.put("end_date", options.endDate);
        query.put("search_ai_type", String.valueOf(options.aiType));
        if ("novel".equals(options.type)) return parseNovelPage(get(accessToken, "/v1/search/novel", query));
        if ("manga".equals(options.type)) query.put("content_type", "manga");
        if ("illust".equals(options.type)) query.put("content_type", "illust");
        return parsePage(get(accessToken, "/v1/search/illust", query));
    }

    public void setBookmark(String accessToken, long illustId, boolean bookmarked) throws Exception {
        String path = bookmarked ? "/v2/illust/bookmark/add" : "/v1/illust/bookmark/delete";
        FormBody.Builder body = new FormBody.Builder().add("illust_id", String.valueOf(illustId));
        if (bookmarked) body.add("restrict", "public");
        Request request = baseRequest(accessToken, BASE + path).post(body.build()).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("收藏同步失败：HTTP " + response.code());
        }
    }

    public List<PixivComment> comments(String accessToken, long illustId) throws Exception {
        JSONObject json = get(accessToken, "/v3/illust/comments", mapOf(
                "illust_id", String.valueOf(illustId),
                "include_total_comments", "true"
        ));
        return parseComments(json.optJSONArray("comments"));
    }

    public void addComment(String accessToken, long illustId, String text, Long parentCommentId) throws Exception {
        FormBody.Builder body = new FormBody.Builder()
                .add("illust_id", String.valueOf(illustId))
                .add("comment", text);
        if (parentCommentId != null && parentCommentId > 0) {
            body.add("parent_comment_id", String.valueOf(parentCommentId));
        }
        Request request = baseRequest(accessToken, BASE + "/v1/illust/comment/add").post(body.build()).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("评论发布失败：HTTP " + response.code());
        }
    }

    public void addStampComment(String token,long illustId,long stampId,Long parentId)throws Exception{FormBody.Builder b=new FormBody.Builder().add("illust_id",String.valueOf(illustId)).add("stamp_id",String.valueOf(stampId));if(parentId!=null)b.add("parent_comment_id",String.valueOf(parentId));Request r=baseRequest(token,BASE+"/v1/illust/comment/add").post(b.build()).build();try(Response x=client.newCall(r).execute()){if(!x.isSuccessful())throw new IOException("贴图发布失败：HTTP "+x.code());}}
    public List<PixivStamp> stamps(String token)throws Exception{JSONObject j=get(token,"/v1/stamps",mapOf());JSONArray a=j.optJSONArray("stamps");List<PixivStamp> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)out.add(new PixivStamp(x.optLong("stamp_id"),x.optString("stamp_url")));}return out;}
    public List<PixivComment> replies(String token,long commentId)throws Exception{return parseComments(get(token,"/v2/illust/comment/replies",mapOf("comment_id",String.valueOf(commentId))).optJSONArray("comments"));}

    private JSONObject get(String accessToken, String path, Map<String, String> query) throws Exception {
        HttpUrl parsed = HttpUrl.parse(BASE + path);
        if (parsed == null) throw new IOException("无效 Pixiv API 地址");
        HttpUrl.Builder url = parsed.newBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            url.addQueryParameter(entry.getKey(), entry.getValue());
        }
        Request request = baseRequest(accessToken, url.build().toString()).get().build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("Pixiv API HTTP " + response.code() + ": " + body);
            return new JSONObject(body);
        }
    }

    private Request.Builder baseRequest(String accessToken, String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "PixivAndroidApp/5.0.234 (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")")
                .header("App-OS", "android")
                .header("App-OS-Version", Build.VERSION.RELEASE)
                .header("App-Version", "5.0.234")
                .header("Accept-Language", Locale.getDefault().toLanguageTag());
    }

    private static void addAll(LinkedHashMap<Long, ArtWork> map, List<ArtWork> works) {
        for (ArtWork work : works) map.put(work.getId(), work);
    }

    private static ArtPage parsePage(JSONObject json) {
        LinkedHashMap<Long, ArtWork> unique = new LinkedHashMap<>();
        addAll(unique, parseArray(json.optJSONArray("illusts")));
        return new ArtPage(new ArrayList<>(unique.values()), json.optString("next_url", ""));
    }

    private static ArtPage parseNovelPage(JSONObject json) {
        LinkedHashMap<Long, ArtWork> unique = new LinkedHashMap<>();
        JSONArray array = json.optJSONArray("novels");
        if (array != null) for (int i = 0; i < array.length(); i++) {
            ArtWork work = parseNovel(array.optJSONObject(i));
            if (work != null) unique.put(work.getId(), work);
        }
        return new ArtPage(new ArrayList<>(unique.values()), json.optString("next_url", ""));
    }

    private static List<PixivComment> parseComments(JSONArray array) {
        List<PixivComment> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            JSONObject user = json.optJSONObject("user");
            JSONObject stamp=json.optJSONObject("stamp");
            JSONObject avatar=user==null?null:user.optJSONObject("profile_image_urls");
            result.add(new PixivComment(
                    json.optLong("id"),
                    user == null ? "Pixiv User" : user.optString("name", "Pixiv User"),
                    json.optString("comment", ""),
                    json.optString("date", ""),
                    json.optBoolean("has_replies", false),stamp==null?"":stamp.optString("stamp_url"),
                    user==null?0L:user.optLong("id"),firstNonEmpty(avatar,"medium","square_medium")
            ));
        }
        return result;
    }

    private static List<ArtWork> parseArray(JSONArray array) {
        List<ArtWork> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            ArtWork work = parseWork(json);
            if (work != null) result.add(work);
        }
        return result;
    }

    private static ArtWork parseWork(JSONObject json) {
        if (json == null) return null;
        long id = json.optLong("id", 0L);
        if (id == 0L) return null;
        JSONObject urls = json.optJSONObject("image_urls");
        String preview = firstNonEmpty(urls, "large", "medium", "square_medium");
        String original = "";
        List<String> pageUrls = new ArrayList<>();
        JSONObject single = json.optJSONObject("meta_single_page");
        if (single != null) {
            original = single.optString("original_image_url", "");
            if (!original.isEmpty()) pageUrls.add(original);
        }
        if (original.isEmpty()) {
            JSONArray pages = json.optJSONArray("meta_pages");
            if (pages != null) for (int i = 0; i < pages.length(); i++) {
                JSONObject page = pages.optJSONObject(i);
                JSONObject pageImageUrls = page == null ? null : page.optJSONObject("image_urls");
                String pageUrl = firstNonEmpty(pageImageUrls, "original", "large", "medium");
                if (!pageUrl.isEmpty()) pageUrls.add(pageUrl);
            }
            if (!pageUrls.isEmpty()) original = pageUrls.get(0);
        }
        if (original.isEmpty()) {
            original = preview;
            if (!preview.isEmpty()) pageUrls.add(preview);
        }
        JSONObject user = json.optJSONObject("user");
        String author = user == null ? "Pixiv Creator" : user.optString("name", "Pixiv Creator");
        List<String> tags = new ArrayList<>();
        JSONArray tagArray = json.optJSONArray("tags");
        if (tagArray != null) {
            for (int i = 0; i < Math.min(8, tagArray.length()); i++) {
                JSONObject tag = tagArray.optJSONObject(i);
                if (tag != null) tags.add(tag.optString("name", ""));
            }
        }
        return new ArtWork(
                id,
                json.optString("title", "Untitled"),
                author,
                json.optString("type", "illust"),
                preview,
                original,
                0,
                json.optInt("width", 1),
                json.optInt("height", 1),
                json.optInt("page_count", 1),
                json.optInt("total_bookmarks", 0),
                tags,
                json.optBoolean("is_bookmarked", false),
                json.optInt("x_restrict", 0),
                user == null ? 0L : user.optLong("id"),
                user == null ? "" : firstNonEmpty(user.optJSONObject("profile_image_urls"), "medium", "square_medium"),
                pageUrls
        );
    }

    private static ArtWork parseNovel(JSONObject json) {
        if (json == null || json.optLong("id") == 0L) return null;
        JSONObject urls=json.optJSONObject("image_urls"),user=json.optJSONObject("user");
        String preview=firstNonEmpty(urls,"large","medium","square_medium");
        List<String> tags=new ArrayList<>();JSONArray array=json.optJSONArray("tags");
        if(array!=null)for(int i=0;i<Math.min(8,array.length());i++){JSONObject tag=array.optJSONObject(i);if(tag!=null)tags.add(tag.optString("name"));}
        return new ArtWork(json.optLong("id"),json.optString("title","Untitled"),user==null?"Pixiv Creator":user.optString("name","Pixiv Creator"),
                "novel",preview,preview,0,1,1,1,json.optInt("total_bookmarks"),tags,json.optBoolean("is_bookmarked"),json.optInt("x_restrict"),
                user==null?0L:user.optLong("id"),user==null?"":firstNonEmpty(user.optJSONObject("profile_image_urls"),"medium","square_medium"));
    }

    private static String firstNonEmpty(JSONObject json, String... keys) {
        if (json == null) return "";
        for (String key : keys) {
            String value = json.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(values[i], values[i + 1]);
        return map;
    }
}
