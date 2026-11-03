package com.dsh.mail;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.ListView;

/**
 * ListView с жестом «потянуть вниз» для немедленной синхронизации почты.
 * Пока тянем — список едет вниз, освобождая место под подпись «Обновление…»;
 * после синхронизации возвращается на место.
 */
public class PullListView extends ListView {

    public interface OnPull {
        /** Тянут вниз: dy — на сколько список уже сдвинут. */
        void onPullProgress(float dy);

        /** Отпустили после порога: начинаем синхронизацию. */
        void onRefresh();

        /** Отпустили слишком рано: возвращаем список назад. */
        void onPullCancel();
    }

    private static final int HOLD_DP = 48;
    private static final int TRIGGER_DP = 56;
    private static final int MAX_DP = 84;

    private OnPull onPull;
    private float downX;
    private float downY;
    private boolean armed;
    private boolean pulling;
    private boolean refreshing;

    public PullListView(Context c) {
        super(c);
    }

    public PullListView(Context c, AttributeSet a) {
        super(c, a);
    }

    public PullListView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
    }

    public void setOnPull(OnPull l) {
        onPull = l;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean atTop() {
        if (getChildCount() == 0) return true;
        return getFirstVisiblePosition() == 0 && getChildAt(0).getTop() >= -dp(2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                armed = !refreshing && atTop();
                pulling = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                if (!armed || refreshing) break;
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                boolean vertical = dy > Math.abs(dx) * 1.5f;
                if (!pulling && vertical && dy > dp(8)) {
                    pulling = true;
                }
                if (pulling) {
                    float shift = Math.max(0, Math.min(dy * 0.55f, dp(MAX_DP)));
                    setTranslationY(shift);
                    if (onPull != null) onPull.onPullProgress(shift);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!pulling) {
                    armed = false;
                    break;
                }
                float shift = getTranslationY();
                boolean fire = e.getActionMasked() == MotionEvent.ACTION_UP
                        && shift >= dp(TRIGGER_DP);
                armed = false;
                pulling = false;
                // Список не должен решить, что это был тап по чату.
                MotionEvent cancel = MotionEvent.obtain(e);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.onTouchEvent(cancel);
                cancel.recycle();
                if (fire) {
                    refreshing = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    animate().translationY(dp(HOLD_DP)).setDuration(120).start();
                    if (onPull != null) onPull.onRefresh();
                } else {
                    if (shift > 0 && onPull != null) onPull.onPullCancel();
                    animateToZero();
                }
                return true;
            }
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    /** Синхронизация закончилась: список возвращается на место. */
    public void finishRefresh() {
        refreshing = false;
        pulling = false;
        armed = false;
        animateToZero();
    }

    private void animateToZero() {
        animate().translationY(0).setDuration(180).start();
    }
}
