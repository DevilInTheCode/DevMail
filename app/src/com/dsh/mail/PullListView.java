package com.dsh.mail;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.ListView;

/**
 * ListView с жестом «потянуть вниз» для немедленной синхронизации почты.
 * Работает только когда список уже в самом верху.
 */
public class PullListView extends ListView {

    public interface OnPull {
        void onPull();
    }

    private OnPull onPull;
    private float downX;
    private float downY;
    private boolean armed;
    private boolean fired;

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
                armed = atTop();
                fired = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                if (!armed || fired) break;
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (dy > dp(64) && dy > Math.abs(dx) * 1.5f) {
                    fired = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (onPull != null) onPull.onPull();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                armed = false;
                break;
            default:
                break;
        }
        return super.onTouchEvent(e);
    }
}
