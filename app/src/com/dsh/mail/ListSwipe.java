package com.dsh.mail;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

/**
 * Горизонтальный свайп по строке списка. Жест живёт на самом ListView, потому что
 * строки-children не получают MOVE, если не съели DOWN — а съесть DOWN значит
 * сломать обычные тапы и долгие нажатия.
 */
public class ListSwipe implements View.OnTouchListener {

    /** Что именно двигать в строке (карточку без подложки, пузырь целиком и т.п.). */
    public interface Content {
        View of(View row);
    }

    public interface Callback {
        /** dir: +1 — вправо, -1 — влево. */
        void onSwipe(int position, int dir);
    }

    private final ListView list;
    private final boolean rightOnly;
    private final Content content;
    private final Callback cb;
    private final int slop;
    private final int maxShift;
    private float downX;
    private float downY;
    private float startX;
    private boolean active;
    private int shift;
    private View moving;
    private int pos = -1;

    public ListSwipe(ListView list, boolean rightOnly, Content content, Callback cb) {
        this.list = list;
        this.rightOnly = rightOnly;
        this.content = content;
        this.cb = cb;
        this.slop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
        this.maxShift = Ui.dp(list.getContext(), 104);
    }

    /** Идёт ли сейчас горизонтальный жест (долгое нажатие надо игнорировать). */
    public boolean isActive() {
        return active;
    }

    public void attach() {
        list.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                active = false;
                shift = 0;
                moving = null;
                pos = -1;
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (!active) {
                    float dx0 = e.getX() - downX;
                    float dy0 = e.getY() - downY;
                    if (Math.abs(dx0) <= slop || Math.abs(dx0) <= Math.abs(dy0) * 1.5f) return false;
                    if (rightOnly && dx0 < 0) return false;
                    int p = list.pointToPosition((int) downX, (int) downY);
                    if (p < 0) return false;
                    View row = list.getChildAt(p - list.getFirstVisiblePosition());
                    if (row == null) return false;
                    View target = content.of(row);
                    if (target == null) return false;
                    moving = target;
                    pos = p;
                    active = true;
                    startX = e.getX();
                    shift = 0;
                }
                float dx = e.getX() - startX;
                if (rightOnly && dx < 0) dx = 0;
                dx = Math.max(-maxShift, Math.min(maxShift, dx));
                shift = (int) dx;
                moving.setTranslationX(shift);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!active) return false;
                active = false;
                boolean fire = e.getActionMasked() == MotionEvent.ACTION_UP
                        && Math.abs(shift) > maxShift * 0.55f;
                final View mv = moving;
                if (mv != null) mv.animate().translationX(0).setDuration(140).start();
                int dir = shift > 0 ? 1 : -1;
                int p = pos;
                moving = null;
                pos = -1;
                shift = 0;
                if (fire) {
                    if (mv != null) mv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    cb.onSwipe(p, dir);
                }
                return true;
            }
            default:
                return false;
        }
    }
}
