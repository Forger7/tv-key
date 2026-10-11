package io.github.forger7.tvkey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Sets the mapping from adb, e.g.
 *   am broadcast -a io.github.forger7.tvkey.CONFIGURE -p io.github.forger7.tvkey \
 *       --es short de.telekom.magentatv.tv --es long com.sony.dtv.tvx
 * An empty string clears a slot. Without extras it only reports the mapping.
 */
public class ConfigReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs =
                context.getSharedPreferences(TvKeyService.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        for (String slot : new String[] {TvKeyService.KEY_SHORT, TvKeyService.KEY_LONG}) {
            if (intent.hasExtra(slot)) {
                String value = intent.getStringExtra(slot);
                if (value == null || value.isEmpty()) {
                    edit.remove(slot);
                } else {
                    edit.putString(slot, value);
                }
            }
        }
        // commit, not apply: the result line below must show the stored state.
        edit.commit();

        String state = "short=" + prefs.getString(TvKeyService.KEY_SHORT, "")
                + " long=" + prefs.getString(TvKeyService.KEY_LONG, "");
        Log.i(TvKeyService.TAG, "mapping " + state);
        setResultData(state);
    }
}
