package com.xa.pixiv.data;

public final class TrendingTag {
    public final String name;
    public final String translatedName;
    public final ArtWork sample;
    public TrendingTag(String name, String translatedName, ArtWork sample) {
        this.name = name == null ? "" : name;
        this.translatedName = translatedName == null || "null".equalsIgnoreCase(translatedName) ? "" : translatedName;
        this.sample = sample;
    }
}
