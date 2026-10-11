package io.github.forger7.tvkey;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

/**
 * Redirects remote keys (TV, Home) to configured apps: one for a short press,
 * optionally another for a long press.
 *
 * The rule throughout: when in doubt, return false and let the system handle
 * the key as if this service did not exist. An accessibility key filter sees
 * every key press, so anything slow or wrong here would affect the whole remote.
 */
public class TvKeyService extends AccessibilityService {

    static final String TAG = "TvKey";

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Key code we currently own (its DOWN was consumed), or 0. */
    private int ownedKey;
    private boolean longFired;
    private String pendingLong;

    private final Runnable longPress = () -> {
        longFired = true;
        launch(pendingLong);
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (Mapping.prefix(code) == null) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (ownedKey == code) {
                // Repeat of a key we already own.
                return true;
            }
            if (ownedKey != 0) {
                // Another mapped key is still held; keep it simple and let this one through.
                return false;
            }
            // With the screen off the system uses these keys to wake the set;
            // that must keep working.
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && !pm.isInteractive()) {
                return false;
            }
            if (Mapping.launchIntent(this, Mapping.get(this, Mapping.slot(code, false))) == null) {
                // Target unset or uninstalled: the key behaves as stock.
                return false;
            }
            ownedKey = code;
            longFired = false;
            pendingLong = Mapping.get(this, Mapping.slot(code, true));
            if (Mapping.launchIntent(this, pendingLong) != null) {
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (ownedKey != code) {
                // We passed the DOWN through, so the UP must follow it.
                return false;
            }
            ownedKey = 0;
            handler.removeCallbacks(longPress);
            if (!longFired) {
                launch(Mapping.get(this, Mapping.slot(code, false)));
            }
            return true;
        }
        return ownedKey == code;
    }

    private void launch(String pkg) {
        Intent intent = Mapping.launchIntent(this, pkg);
        if (intent == null) {
            Log.w(TAG, "no launch intent for " + pkg);
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "launch failed for " + pkg, e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
