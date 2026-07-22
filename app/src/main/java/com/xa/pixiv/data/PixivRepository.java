package com.xa.pixiv.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class PixivRepository {
    private final SessionStore session;
    private final PixivAuthClient auth;
    private final PixivApiClient api;

    public PixivRepository(Context context) {
        session = new SessionStore(context);
        auth = new PixivAuthClient(context);
        api = new PixivApiClient(context.getApplicationContext());
    }

    public SessionStore session() { return session; }

    public ArtPage loadHomePage() {
        if (!session.isLoggedIn()) return demoPage();
        try {
            ensureFreshToken();
            return prepare(api.recommended(session.getAccessToken()));
        } catch (Exception ignored) {
            return demoPage();
        }
    }

    public ArtPage loadNext(String nextUrl) {
        if (!session.isLoggedIn() || nextUrl == null || nextUrl.isEmpty()) return demoPage();
        try {
            ensureFreshToken();
            return prepare(api.nextPage(session.getAccessToken(), nextUrl));
        } catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public ArtPage loadFollowing() {
        if (!session.isLoggedIn()) return new ArtPage(new ArrayList<>(), "");
        try { ensureFreshToken(); return prepare(api.following(session.getAccessToken())); }
        catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public ArtPage loadRecommended(String type) {
        if (!session.isLoggedIn()) return demoPage();
        try {
            ensureFreshToken();
            if ("manga".equals(type)) return prepare(api.recommendedManga(session.getAccessToken()));
            if ("novel".equals(type)) return prepare(api.recommendedNovels(session.getAccessToken()));
            return prepare(api.recommended(session.getAccessToken()));
        } catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public ArtPage loadLatest(String type) {
        if (!session.isLoggedIn()) return new ArtPage(new ArrayList<>(), "");
        try { ensureFreshToken(); return prepare(api.latest(session.getAccessToken(), type)); }
        catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public List<TrendingTag> loadTrendingTags(String type) {
        if (!session.isLoggedIn()) return new ArrayList<>();
        try { ensureFreshToken(); return api.trendingTags(session.getAccessToken(), type); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

    public List<TrendingTag> autocomplete(String word) throws Exception {
        if (!session.isLoggedIn() || word == null || word.trim().isEmpty()) return new ArrayList<>();
        ensureFreshToken();
        return api.autocomplete(session.getAccessToken(), word.trim());
    }

    public ArtPage loadR18(String mode) {
        if (!session.isLoggedIn()) return demoPage();
        try { ensureFreshToken(); return prepare(api.ranking(session.getAccessToken(), mode)); }
        catch (Exception ignored) { return demoPage(); }
    }

    public ArtPage loadBookmarks() {
        if (!session.isLoggedIn()) return new ArtPage(new ArrayList<>(), "");
        try {
            ensureFreshToken();
            ArtPage publicPage = api.bookmarks(session.getAccessToken(), session.getUserId(), "public");
            ArtPage privatePage = api.bookmarks(session.getAccessToken(), session.getUserId(), "private");
            java.util.LinkedHashMap<Long,ArtWork> merged = new java.util.LinkedHashMap<>();
            for (ArtWork item : publicPage.getItems()) merged.put(item.getId(), item);
            for (ArtWork item : privatePage.getItems()) merged.put(item.getId(), item);
            String next = !publicPage.getNextUrl().isEmpty() ? publicPage.getNextUrl() : privatePage.getNextUrl();
            return prepare(new ArtPage(new ArrayList<>(merged.values()), next));
        }
        catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public ArtPage loadBookmarks(String restrict) {
        if (!session.isLoggedIn()) return new ArtPage(new ArrayList<>(), "");
        try { ensureFreshToken(); return prepare(api.bookmarks(session.getAccessToken(), session.getUserId(), restrict)); }
        catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public ArtPage loadRelated(long illustId) {
        if (!session.isLoggedIn()) return new ArtPage(new ArrayList<>(), "");
        try { ensureFreshToken(); return prepare(api.related(session.getAccessToken(), illustId)); }
        catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public ArtPage loadUserIllusts(long userId) { if(!session.isLoggedIn())return new ArtPage(new ArrayList<>(), "");try{ensureFreshToken();return prepare(api.userIllusts(session.getAccessToken(),userId));}catch(Exception ignored){return new ArtPage(new ArrayList<>(), "");} }
    public PixivUser loadUser(long userId) throws Exception { ensureFreshToken(); return api.userDetail(session.getAccessToken(),userId); }
    public ArtWork loadIllust(long illustId) throws Exception { ensureFreshToken(); return prepare(java.util.Collections.singletonList(api.illustDetail(session.getAccessToken(),illustId))).get(0); }
    public List<PixivUser> searchUsers(String word) throws Exception {
        if (!session.isLoggedIn()) return new ArrayList<>();
        ensureFreshToken();
        return api.searchUsers(session.getAccessToken(), word.trim());
    }
    public void setFollow(long userId,boolean follow) throws Exception { ensureFreshToken();api.setFollow(session.getAccessToken(),userId,follow); }

    public List<PixivComment> loadComments(long illustId) throws Exception {
        if (!session.isLoggedIn()) return new ArrayList<>();
        ensureFreshToken();
        return api.comments(session.getAccessToken(), illustId);
    }

    public void addComment(long illustId, String text, Long parentId) throws Exception {
        if (!session.isLoggedIn()) throw new IllegalStateException("请先登录 Pixiv");
        ensureFreshToken();
        api.addComment(session.getAccessToken(), illustId, text, parentId);
    }
    public List<PixivStamp> loadStamps()throws Exception{ensureFreshToken();return api.stamps(session.getAccessToken());}
    public List<PixivComment> loadReplies(long commentId)throws Exception{ensureFreshToken();return api.replies(session.getAccessToken(),commentId);}
    public void addStamp(long illustId,long stampId,Long parentId)throws Exception{ensureFreshToken();api.addStampComment(session.getAccessToken(),illustId,stampId,parentId);}

    public List<ArtWork> loadHome() { return loadHomePage().getItems(); }

    public List<ArtWork> loadRanking() {
        if (!session.isLoggedIn()) return prepare(DemoRepository.all());
        try {
            ensureFreshToken();
            List<ArtWork> works = api.ranking(session.getAccessToken(), "day").getItems();
            return works.isEmpty() ? prepare(DemoRepository.all()) : prepare(works);
        } catch (Exception ignored) {
            return prepare(DemoRepository.all());
        }
    }

    public List<ArtWork> search(String query) {
        if (query == null || query.trim().isEmpty()) return loadRanking();
        if (!session.isLoggedIn()) return prepare(DemoRepository.search(query));
        try {
            ensureFreshToken();
            return prepare(api.search(session.getAccessToken(), query.trim()));
        } catch (Exception ignored) {
            return prepare(DemoRepository.search(query));
        }
    }

    public ArtPage search(String query, SearchOptions options) {
        if (!session.isLoggedIn()) return new ArtPage(filter(DemoRepository.search(query), options), "");
        try { ensureFreshToken(); return prepareFilter(api.search(session.getAccessToken(), query.trim(), options), options); }
        catch (Exception ignored) { return new ArtPage(filter(DemoRepository.search(query), options), ""); }
    }

    public ArtPage loadRanking(String mode, String date) {
        if (!session.isLoggedIn()) return demoPage();
        try { ensureFreshToken(); return prepare(api.ranking(session.getAccessToken(), mode, date)); }
        catch (Exception ignored) { return demoPage(); }
    }

    public ArtPage loadNovelRanking(String mode, String date) {
        if (!session.isLoggedIn()) return new ArtPage(new ArrayList<>(), "");
        try { ensureFreshToken(); return prepare(api.rankingNovels(session.getAccessToken(), mode, date)); }
        catch (Exception ignored) { return new ArtPage(new ArrayList<>(), ""); }
    }

    public boolean toggleBookmark(ArtWork work) throws Exception {
        boolean bookmarked = !work.isBookmarked();
        if (session.isLoggedIn() && !work.isLocal()) {
            ensureFreshToken();
            api.setBookmark(session.getAccessToken(), work.getId(), bookmarked);
        }
        session.setBookmarked(work.getId(), bookmarked);
        work.setBookmarked(bookmarked);
        return bookmarked;
    }

    private void ensureFreshToken() {
        if (!session.isLoggedIn()) return;
        if (session.getExpiresAt() > System.currentTimeMillis() + 60_000L) return;
        PixivAuthClient.AuthResult result = auth.refresh(session.getRefreshToken());
        if (result.success) session.saveAuth(result);
    }

    private List<ArtWork> prepare(List<ArtWork> input) {
        List<ArtWork> output = new ArrayList<>(input.size());
        for (ArtWork work : input) {
            work.setBookmarked(work.isBookmarked() || session.isBookmarked(work.getId()));
            output.add(work);
        }
        return output;
    }

    private ArtPage prepare(ArtPage page) {
        return new ArtPage(prepare(page.getItems()), page.getNextUrl());
    }

    private ArtPage demoPage() {
        List<ArtWork> works = prepare(DemoRepository.all());
        java.util.Collections.rotate(works, (int) (System.currentTimeMillis() / 1000L % Math.max(1, works.size())));
        return new ArtPage(works, "demo://next");
    }

    private ArtPage prepareFilter(ArtPage page, SearchOptions options) {
        return new ArtPage(prepare(filter(page.getItems(), options)), page.getNextUrl());
    }

    private List<ArtWork> filter(List<ArtWork> input, SearchOptions options) {
        List<ArtWork> out = new ArrayList<>();
        for (ArtWork work : input) {
            if (!"all".equals(options.type) && !options.type.equals(work.getType())) continue;
            if ("hide".equals(options.r18) && work.getXRestrict() > 0) continue;
            if ("only".equals(options.r18) && work.getXRestrict() == 0) continue;
            out.add(work);
        }
        return out;
    }
}
