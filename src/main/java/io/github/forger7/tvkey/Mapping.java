package io.github.forger7.tvkey;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.KeyEvent;

/** Stored key → app mapping, shared by the service and the settings screen. */
final class Mapping {

    static final String PREFS = "mapping";

    /** Keys that can be remapped, with the prefix used for their slots. */
    static final int[] KEYS = {KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_HOME};

    private Mapping() {
    }

    static String prefix(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_TV:
                return "tv";
            case KeyEvent.KEYCODE_HOME:
                return "home";
            default:
                return null;
        }
    }

    static String slot(int keyCode, boolean longPress) {
        return prefix(keyCode) + (longPress ? ".long" : ".short");
    }

    static String[] allSlots() {
        String[] slots = new String[KEYS.length * 2];
        for (int i = 0; i < KEYS.length; i++) {
            slots[2 * i] = slot(KEYS[i], false);
            slots[2 * i + 1] = slot(KEYS[i], true);
        }
        return slots;
    }

    // The settings screen runs in its own process so that it costs no RAM
    // while closed. MODE_MULTI_PROCESS makes each process re-read the file
    // when it changed; it is deprecated because concurrent writers can lose
    // updates, which cannot happen here (one screen or one adb call at a time).
    @SuppressWarnings("deprecation")
    static SharedPreferences prefs(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_MULTI_PROCESS);
        migrate(prefs);
        return prefs;
    }

    static String get(Context context, String slot) {
        return prefs(context).getString(slot, null);
    }

    static void set(Context context, String slot, String pkg) {
        SharedPreferences.Editor edit = prefs(context).edit();
        if (pkg == null || pkg.isEmpty()) {
            edit.remove(slot);
        } else {
            edit.putString(slot, pkg);
        }
        // commit, not apply: the other process may read the file right away.
        edit.commit();
    }

    /** v0.1 stored the TV key under "short"/"long". */
    private static void migrate(SharedPreferences prefs) {
        if (!prefs.contains("short") && !prefs.contains("long")) {
            return;
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (String old : new String[] {"short", "long"}) {
            String value = prefs.getString(old, null);
            if (value != null && !prefs.contains("tv." + old)) {
                edit.putString("tv." + old, value);
            }
            edit.remove(old);
        }
        edit.commit();
    }

    static Intent launchIntent(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        // Leanback first: several TV apps only declare a LEANBACK_LAUNCHER entry.
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(pkg);
        }
        return intent;
    }
}
