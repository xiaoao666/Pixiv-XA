package com.xa.pixiv;

import com.xa.pixiv.data.ArtWork;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class ExampleUnitTest {
    @Test
    public void pagePreviewsStayAlignedWithOriginalPages() {
        ArtWork work = new ArtWork(1L, "title", "author", "illust",
                "preview-0", "original-0", 0, 100, 200, 2, 0,
                Collections.emptyList(), false, 0, 2L, "",
                Arrays.asList("original-0", "original-1"),
                Arrays.asList("preview-0", "preview-1"));

        assertEquals(Arrays.asList("preview-0", "preview-1"), work.getPagePreviewUrls());
    }

    @Test
    public void missingPagePreviewFallsBackWithoutUsingOriginal() {
        ArtWork work = new ArtWork(1L, "title", "author", "illust",
                "fast-preview", "original-0", 0, 100, 200, 2, 0,
                Collections.emptyList(), false, 0, 2L, "",
                Arrays.asList("original-0", "original-1"));

        assertEquals(Arrays.asList("fast-preview", "fast-preview"), work.getPagePreviewUrls());
    }
}
