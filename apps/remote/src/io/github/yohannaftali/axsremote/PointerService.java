package io.github.yohannaftali.axsremote;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * The phone's touchpad on the TV, as an accessibility service: it draws a
 * pointer as an accessibility overlay and, on a tap, long press or two-finger
 * drag, presses or scrolls the item under the pointer with accessibility
 * actions. (Gestures - dispatchGesture - would be simpler, but Android 11
 * cancels them on devices without a touchscreen, like this TV.) To find that
 * item it looks at the layout of the app in front at that moment, on the TV
 * only; nothing is stored, logged or sent anywhere.
 */
public final class PointerService extends AccessibilityService {
    private static final long HIDE_MS = 4000;
    /** Phone pixels of two-finger travel per scroll step. */
    private static final float SCROLL_STEP = 60f;
    private static PointerService current;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View dot;
    private WindowManager.LayoutParams lp;
    private int size;
    private float x;
    private float y;
    private int width;
    private int height;
    private float scrollX;
    private float scrollY;

    private final Runnable hide = () -> {
        if (dot != null) dot.setVisibility(View.GONE);
    };

    static boolean active() {
        return current != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        current = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Rect bounds = wm.getCurrentWindowMetrics().getBounds();
        width = bounds.width();
        height = bounds.height();
        x = width / 2f;
        y = height / 2f;
        size = Math.round(28 * getResources().getDisplayMetrics().density);
        dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0xE6FFFFFF);
        d.setStroke(Math.max(2, size / 9), 0xCC0B0F18);
        dot.setBackground(d);
        dot.setElevation(size / 3f);
        dot.setVisibility(View.GONE);
        lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        place();
        try {
            wm.addView(dot, lp);
        } catch (RuntimeException e) {
            dot = null;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used: the service only draws the pointer and performs gestures.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (dot != null && wm != null) {
            try {
                wm.removeView(dot);
            } catch (RuntimeException ignored) {
                // Already gone.
            }
        }
        dot = null;
        if (current == this) current = null;
        super.onDestroy();
    }

    // ---- commands from the phone (main thread) ----

    /** Moves the pointer by the finger's movement on the phone, faster for quick swipes. */
    static void move(float dx, float dy) {
        PointerService s = current;
        if (s == null) return;
        float speed = (float) Math.hypot(dx, dy);
        float gain = 1.6f + Math.min(speed, 40f) / 12f;
        s.x = clamp(s.x + dx * gain, 0, s.width - 1);
        s.y = clamp(s.y + dy * gain, 0, s.height - 1);
        s.show();
    }

    static void tap(boolean longPress) {
        PointerService s = current;
        if (s == null) return;
        s.show();
        AccessibilityNodeInfo hit = s.nodeAt();
        // The deepest item under the pointer, or the nearest parent that reacts to a click.
        for (AccessibilityNodeInfo n = hit; n != null; n = n.getParent()) {
            if (longPress ? n.isLongClickable() : n.isClickable()) {
                n.performAction(longPress ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
        }
        if (hit != null) {
            // Nothing clickable: at least move the focus there, so OK can act on it.
            hit.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        }
    }

    /**
     * Two-finger drag: scrolls the scrollable item under the pointer one step
     * each time the fingers have travelled far enough; content follows the fingers.
     */
    static void scroll(float dx, float dy) {
        PointerService s = current;
        if (s == null) return;
        s.show();
        s.scrollX += dx;
        s.scrollY += dy;
        AccessibilityNodeInfo.AccessibilityAction action = null;
        if (Math.abs(s.scrollY) >= SCROLL_STEP && Math.abs(s.scrollY) >= Math.abs(s.scrollX)) {
            action = s.scrollY < 0 ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP;
        } else if (Math.abs(s.scrollX) >= SCROLL_STEP) {
            action = s.scrollX < 0 ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT;
        }
        if (action == null) return;
        boolean forward = action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
                || action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT;
        s.scrollX = 0;
        s.scrollY = 0;
        for (AccessibilityNodeInfo n = s.nodeAt(); n != null; n = n.getParent()) {
            if (!n.isScrollable()) continue;
            if (n.getActionList().contains(action) && n.performAction(action.getId())) return;
            if (n.performAction(forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return;
        }
    }

    /** The deepest visible item of the active window that contains the pointer, or null. */
    private AccessibilityNodeInfo nodeAt() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        Rect r = new Rect();
        root.getBoundsInScreen(r);
        if (!r.contains(Math.round(x), Math.round(y))) return null;
        AccessibilityNodeInfo node = root;
        boolean deeper = true;
        while (deeper) {
            deeper = false;
            // Later children are drawn on top, so look at them first.
            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                AccessibilityNodeInfo c = node.getChild(i);
                if (c == null || !c.isVisibleToUser()) continue;
                c.getBoundsInScreen(r);
                if (r.contains(Math.round(x), Math.round(y))) {
                    node = c;
                    deeper = true;
                    break;
                }
            }
        }
        return node;
    }

    private void show() {
        if (dot == null) return;
        place();
        try {
            wm.updateViewLayout(dot, lp);
        } catch (RuntimeException ignored) {
            // Window gone.
        }
        dot.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hide);
        ui.postDelayed(hide, HIDE_MS);
    }

    /** The tip of the pointer is its centre. */
    private void place() {
        lp.x = Math.round(x - size / 2f);
        lp.y = Math.round(y - size / 2f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
