package com.dsh.mail;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Полноэкранный просмотр картинки из вложения: щипок и двойной тап для масштаба. */
public class PhotoActivity extends Activity {

    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_NAME = "name";

    private ImageView image;
    private TextView name;

    private final Matrix matrix = new Matrix();
    private float fitScale = 1f;
    private float scale = 1f;
    private float tx;
    private float ty;
    private int drawableW;
    private int drawableH;
    private GestureDetector gestures;
    private ScaleGestureDetector pinch;
    private ValueAnimator animator;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        String title = getIntent().getStringExtra(EXTRA_NAME);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        image = new ImageView(this);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image.setScaleType(ImageView.ScaleType.MATRIX);
        root.addView(image);

        TextView close = new TextView(this);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(44), dp(44));
        clp.gravity = Gravity.TOP | Gravity.LEFT;
        clp.leftMargin = dp(10);
        clp.topMargin = dp(10);
        close.setLayoutParams(clp);
        close.setText("✕");
        close.setTextSize(18);
        close.setTextColor(Color.WHITE);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundResource(R.drawable.chip_bg);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(close);

        if (title != null && title.length() > 0) {
            name = new TextView(this);
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.gravity = Gravity.BOTTOM | Gravity.LEFT;
            nlp.leftMargin = dp(14);
            nlp.bottomMargin = dp(16);
            name.setLayoutParams(nlp);
            name.setText(title);
            name.setTextSize(12);
            name.setTextColor(Color.WHITE);
            name.setBackgroundResource(R.drawable.chip_bg);
            name.setPadding(dp(10), dp(6), dp(10), dp(6));
            root.addView(name);
        }

        TextView hint = new TextView(this);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        hlp.rightMargin = dp(14);
        hlp.bottomMargin = dp(16);
        hint.setLayoutParams(hlp);
        hint.setText("⤢  " + L.t("Двойной тап или щипок — масштаб"));
        hint.setTextSize(11);
        hint.setTextColor(0x99FFFFFF);
        root.addView(hint);

        setContentView(root);
        setupGestures();
        load(path);
    }

    private void setupGestures() {
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scale <= fitScale * 1.001f) return false;
                tx -= dx;
                ty -= dy;
                clampPan();
                applyMatrix();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (scale > fitScale * 1.05f) {
                    animateTo(fitScale, image.getWidth() / 2f, image.getHeight() / 2f);
                } else {
                    animateTo(fitScale * 2.6f, e.getX(), e.getY());
                }
                return true;
            }
        });
        pinch = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                if (animator != null) animator.cancel();
                float next = scale * d.getScaleFactor();
                float min = fitScale;
                float max = fitScale * 8f;
                if (next < min) next = min;
                if (next > max) next = max;
                float k = next / scale;
                tx = d.getFocusX() - (d.getFocusX() - tx) * k;
                ty = d.getFocusY() - (d.getFocusY() - ty) * k;
                scale = next;
                clampPan();
                applyMatrix();
                return true;
            }
        });
        image.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                pinch.onTouchEvent(e);
                gestures.onTouchEvent(e);
                return true;
            }
        });
    }

    private void applyMatrix() {
        matrix.setScale(scale, scale);
        matrix.postTranslate(tx, ty);
        image.setImageMatrix(matrix);
    }

    private void clampPan() {
        int vw = image.getWidth();
        int vh = image.getHeight();
        float cw = drawableW * scale;
        float ch = drawableH * scale;
        if (cw <= vw) tx = (vw - cw) / 2f;
        else tx = Math.max(vw - cw, Math.min(0f, tx));
        if (ch <= vh) ty = (vh - ch) / 2f;
        else ty = Math.max(vh - ch, Math.min(0f, ty));
    }

    private void fit() {
        int vw = image.getWidth();
        int vh = image.getHeight();
        if (vw <= 0 || vh <= 0 || drawableW <= 0 || drawableH <= 0) return;
        fitScale = Math.min(vw / (float) drawableW, vh / (float) drawableH);
        scale = fitScale;
        tx = (vw - drawableW * scale) / 2f;
        ty = (vh - drawableH * scale) / 2f;
        applyMatrix();
    }

    private void animateTo(final float targetScale, final float fx, final float fy) {
        final float startScale = scale;
        final float startTx = tx;
        final float startTy = ty;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                float s = startScale + (targetScale - startScale) * f;
                float k = s / startScale;
                scale = s;
                tx = fx - (fx - startTx) * k;
                ty = fy - (fy - startTy) * k;
                clampPan();
                applyMatrix();
            }
        });
        animator.start();
    }

    private void load(final String path) {
        if (path == null) return;
        final int w = getResources().getDisplayMetrics().widthPixels;
        final int h = getResources().getDisplayMetrics().heightPixels;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = null;
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, bounds);
                    int sample = 1;
                    while (bounds.outWidth / (sample * 2) >= w * 2
                            && bounds.outHeight / (sample * 2) >= h * 2) {
                        sample *= 2;
                    }
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inSampleSize = sample;
                    bm = BitmapFactory.decodeFile(path, opt);
                } catch (Throwable ignored) {
                }
                final Bitmap out = bm;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (out != null) {
                            image.setImageBitmap(out);
                            drawableW = out.getWidth();
                            drawableH = out.getHeight();
                            image.post(new Runnable() {
                                @Override
                                public void run() {
                                    fit();
                                }
                            });
                        } else {
                            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            image.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }
                });
            }
        }).start();
    }

}
