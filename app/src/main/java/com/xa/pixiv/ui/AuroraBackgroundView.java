package com.xa.pixiv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.xa.pixiv.R;

import java.util.Random;

/** Slow moving aurora orbs + tiny constellation dots. */
public final class AuroraBackgroundView extends View {
    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final float[] starX = new float[28];
    private final float[] starY = new float[28];
    private RadialGradient pinkShader;
    private RadialGradient cyanShader;
    private ValueAnimator animator;
    private float phase;
    private float radius;

    public AuroraBackgroundView(Context context) { this(context, null); }

    public AuroraBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Random random = new Random(0x58414154L);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        radius = Math.max(w, h) * 0.55f;
        int pink = ContextCompat.getColor(getContext(), R.color.pink_500);
        int cyan = ContextCompat.getColor(getContext(), R.color.cyan_400);
        pinkShader = new RadialGradient(0, 0, radius,
                new int[]{withAlpha(pink, 72), withAlpha(pink, 20), Color.TRANSPARENT},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
        cyanShader = new RadialGradient(0, 0, radius,
                new int[]{withAlpha(cyan, 58), withAlpha(cyan, 16), Color.TRANSPARENT},
                new float[]{0f, .46f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pinkShader == null || cyanShader == null) return;
        float w = getWidth();
        float h = getHeight();
        float wave = (float) Math.sin(phase * Math.PI * 2.0);
        float wave2 = (float) Math.cos(phase * Math.PI * 2.0);

        shaderMatrix.reset();
        shaderMatrix.setTranslate(w * (.12f + wave * .08f), h * (.18f + wave2 * .04f));
        pinkShader.setLocalMatrix(shaderMatrix);
        orbPaint.setShader(pinkShader);
        canvas.drawCircle(w * .12f, h * .18f, radius, orbPaint);

        shaderMatrix.reset();
        shaderMatrix.setTranslate(w * (.88f - wave2 * .08f), h * (.68f + wave * .06f));
        cyanShader.setLocalMatrix(shaderMatrix);
        orbPaint.setShader(cyanShader);
        canvas.drawCircle(w * .88f, h * .68f, radius, orbPaint);
        orbPaint.setShader(null);

        starPaint.setColor(ContextCompat.getColor(getContext(), R.color.xa_text));
        for (int i = 0; i < starX.length; i++) {
            float pulse = .25f + .35f * (float) Math.abs(Math.sin(phase * Math.PI * 2 + i));
            starPaint.setAlpha((int) (255 * pulse));
            float size = i % 5 == 0 ? 2.2f : 1.2f;
            canvas.drawCircle(starX[i] * w, starY[i] * h, size, starPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(14000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null;
        super.onDetachedFromWindow();
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
