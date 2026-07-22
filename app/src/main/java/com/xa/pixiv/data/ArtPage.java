package com.xa.pixiv.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A Pixiv feed page together with the opaque continuation URL returned by Pixiv. */
public final class ArtPage {
    private final List<ArtWork> items;
    private final String nextUrl;

    public ArtPage(List<ArtWork> items, String nextUrl) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.nextUrl = nextUrl == null ? "" : nextUrl;
    }

    public List<ArtWork> getItems() { return items; }
    public String getNextUrl() { return nextUrl; }
    public boolean hasNext() { return !nextUrl.isEmpty(); }
}
