package com.xa.pixiv.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView that sizes its height from a given aspect ratio (height / width),
 * enabling Pinterest-style staggered grids without image cropping.
 */
public final class RatioImageView extends AppCompatImageView {
    private static final float MIN_RATIO = 0.55f;
    private static final float MAX_RATIO = 1.9f;

    private float ratio = 1.25f;

    public RatioImageView(Context context) { super(context); }
    public RatioImageView(Context context, AttributeSet attrs) { super(context, attrs); }

    /** @param heightOverWidth artwork height divided by width; clamped for layout sanity. */
    public void setAspect(int width, int height) {
        float value = (width <= 0 || height <= 0) ? 1.25f : (float) height / width;
        value = Math.max(MIN_RATIO, Math.min(MAX_RATIO, value));
        if (value != ratio) {
            ratio = value;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width * ratio);
        setMeasuredDimension(width, height);
    }
}
