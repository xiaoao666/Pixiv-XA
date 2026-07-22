package com.xa.pixiv.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArtWork implements Serializable {
    private final long id;
    private final String title;
    private final String author;
    private final String type;
    private final String previewUrl;
    private final String originalUrl;
    private final int imageRes;
    private final int width;
    private final int height;
    private final int pageCount;
    private final int bookmarkCount;
    private final List<String> tags;
    private final int xRestrict;
    private final long authorId;
    private final String authorAvatar;
    private final List<String> pageUrls;
    private boolean bookmarked;

    public ArtWork(long id, String title, String author, String type, String previewUrl,
                   String originalUrl, int imageRes, int width, int height, int pageCount,
                   int bookmarkCount, List<String> tags, boolean bookmarked) {
        this(id, title, author, type, previewUrl, originalUrl, imageRes, width, height,
                pageCount, bookmarkCount, tags, bookmarked, 0, 0L, "");
    }

    public ArtWork(long id, String title, String author, String type, String previewUrl,
                   String originalUrl, int imageRes, int width, int height, int pageCount,
                   int bookmarkCount, List<String> tags, boolean bookmarked, int xRestrict) {
        this(id,title,author,type,previewUrl,originalUrl,imageRes,width,height,pageCount,bookmarkCount,tags,bookmarked,xRestrict,0L,"");
    }

    public ArtWork(long id, String title, String author, String type, String previewUrl,
                   String originalUrl, int imageRes, int width, int height, int pageCount,
                   int bookmarkCount, List<String> tags, boolean bookmarked, int xRestrict,
                   long authorId, String authorAvatar) {
        this(id, title, author, type, previewUrl, originalUrl, imageRes, width, height,
                pageCount, bookmarkCount, tags, bookmarked, xRestrict, authorId,
                authorAvatar, originalUrl == null || originalUrl.isEmpty()
                        ? Collections.emptyList() : Collections.singletonList(originalUrl));
    }

    public ArtWork(long id, String title, String author, String type, String previewUrl,
                   String originalUrl, int imageRes, int width, int height, int pageCount,
                   int bookmarkCount, List<String> tags, boolean bookmarked, int xRestrict,
                   long authorId, String authorAvatar, List<String> pageUrls) {
        this.id = id;
        this.title = title == null ? "Untitled" : title;
        this.author = author == null ? "Unknown" : author;
        this.type = type == null ? "illust" : type;
        this.previewUrl = previewUrl == null ? "" : previewUrl;
        this.originalUrl = originalUrl == null ? this.previewUrl : originalUrl;
        this.imageRes = imageRes;
        this.width = width;
        this.height = height;
        this.pageCount = Math.max(1, pageCount);
        this.bookmarkCount = Math.max(0, bookmarkCount);
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags == null ? Collections.emptyList() : tags));
        this.bookmarked = bookmarked;
        this.xRestrict = xRestrict;
        this.authorId = authorId;
        this.authorAvatar = authorAvatar == null ? "" : authorAvatar;
        List<String> pages = new ArrayList<>();
        if (pageUrls != null) for (String url : pageUrls) {
            if (url != null && !url.isEmpty()) pages.add(url);
        }
        if (pages.isEmpty() && !this.originalUrl.isEmpty()) pages.add(this.originalUrl);
        this.pageUrls = Collections.unmodifiableList(pages);
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getType() { return type; }
    public String getPreviewUrl() { return previewUrl; }
    public String getOriginalUrl() { return originalUrl; }
    public int getImageRes() { return imageRes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getPageCount() { return pageCount; }
    public int getBookmarkCount() { return bookmarkCount; }
    public List<String> getTags() { return tags; }
    public boolean isBookmarked() { return bookmarked; }
    public int getXRestrict() { return xRestrict; }
    public long getAuthorId() { return authorId; }
    public String getAuthorAvatar() { return authorAvatar; }
    public List<String> getPageUrls() {
        if (pageUrls == null || pageUrls.isEmpty()) {
            return originalUrl == null || originalUrl.isEmpty()
                    ? Collections.emptyList() : Collections.singletonList(originalUrl);
        }
        return pageUrls;
    }
    public void setBookmarked(boolean bookmarked) { this.bookmarked = bookmarked; }
    public boolean isLocal() { return imageRes != 0; }
}
