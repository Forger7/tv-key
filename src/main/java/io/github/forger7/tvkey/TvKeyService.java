package io.github.forger7.tvkey;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

/**
 * Redirects the remote's TV key to a configured app (short press) and
 * optionally to a second app (long press).
 *
 * The rule throughout: when in doubt, return false and let the system handle
 * the key as if this service did not exist. An accessibility key filter sees
 * every key press, so anything slow or wrong here would affect the whole remote.
 */
public class TvKeyService extends AccessibilityService {

    static final String TAG = "TvKey";
    static final String PREFS = "mapping";
    static final String KEY_SHORT = "short";
    static final String KEY_LONG = "long";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean keyDown;
    private boolean longFired;
    private String pendingLong;

    private final Runnable longPress = () -> {
        longFired = true;
        launch(pendingLong);
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_TV) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyDown) {
                // Repeat of a key we already own.
                return true;
            }
            // With the screen off the system uses the TV key to wake the set;
            // that must keep working.
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && !pm.isInteractive()) {
                return false;
            }
            String shortTarget = prefs().getString(KEY_SHORT, null);
            if (launchIntent(shortTarget) == null) {
                // Target unset or uninstalled: the key behaves as stock.
                return false;
            }
            keyDown = true;
            longFired = false;
            pendingLong = prefs().getString(KEY_LONG, null);
            if (launchIntent(pendingLong) != null) {
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (!keyDown) {
                // We passed the DOWN through, so the UP must follow it.
                return false;
            }
            keyDown = false;
            handler.removeCallbacks(longPress);
            if (!longFired) {
                launch(prefs().getString(KEY_SHORT, null));
            }
            return true;
        }
        return keyDown;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private Intent launchIntent(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        PackageManager pm = getPackageManager();
        // Leanback first: several TV apps only declare a LEANBACK_LAUNCHER entry.
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(pkg);
        }
        return intent;
    }

    private void launch(String pkg) {
        Intent intent = launchIntent(pkg);
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
