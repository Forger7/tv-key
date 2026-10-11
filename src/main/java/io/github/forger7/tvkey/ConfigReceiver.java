package io.github.forger7.tvkey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Sets the mapping from adb, e.g.
 *   am broadcast --include-stopped-packages -a io.github.forger7.tvkey.CONFIGURE \
 *       -p io.github.forger7.tvkey --es tv.short de.telekom.magentatv.tv --es tv.long com.sony.dtv.tvx
 * Slots: tv.short, tv.long, home.short, home.long. An empty string clears a
 * slot. Without extras it only reports the mapping.
 */
public class ConfigReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        StringBuilder state = new StringBuilder();
        for (String slot : Mapping.allSlots()) {
            if (intent.hasExtra(slot)) {
                Mapping.set(context, slot, intent.getStringExtra(slot));
            }
            String value = Mapping.get(context, slot);
            state.append(slot).append('=').append(value == null ? "" : value).append(' ');
        }
        Log.i(TvKeyService.TAG, "mapping " + state);
        setResultData(state.toString().trim());
    }
}
