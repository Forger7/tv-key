package io.github.forger7.tvkey;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks an app per slot (key × short/long press). Runs in its own process
 * (see the manifest) so the always-on service stays small.
 */
public class SettingsActivity extends Activity {

    private static final String STOCK = "Stock behaviour";

    private TextView status;
    private Button enableButton;
    private ArrayAdapter<String> slotAdapter;
    private final List<String> slots = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (32 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad * 2, pad, pad * 2, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, pad / 4, 0, pad / 4);
        root.addView(status);

        enableButton = new Button(this);
        enableButton.setText("Open accessibility settings");
        enableButton.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(enableButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Collections.addAll(slots, Mapping.allSlots());
        slotAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView list = new ListView(this);
        list.setAdapter(slotAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> pick(slots.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        list.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = serviceEnabled();
        status.setText(enabled
                ? "Service is on. Choose an app per button and press type."
                : "Service is off: mappings have no effect until it is enabled.");
        enableButton.setVisibility(enabled ? Button.GONE : Button.VISIBLE);
        refresh();
    }

    private void refresh() {
        slotAdapter.clear();
        for (String slot : slots) {
            slotAdapter.add(slotTitle(slot) + ":  " + appLabel(Mapping.get(this, slot)));
        }
    }

    private void pick(String slot) {
        Map<String, String> apps = launchableApps();
        List<String> labels = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        labels.add(STOCK);
        packages.add(null);
        for (Map.Entry<String, String> e : apps.entrySet()) {
            labels.add(e.getValue());
            packages.add(e.getKey());
        }
        new AlertDialog.Builder(this)
                .setTitle(slotTitle(slot))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    Mapping.set(this, slot, packages.get(which));
                    refresh();
                })
                .show();
    }

    /** Package → label of every app with a TV or phone launcher entry, sorted by label. */
    private Map<String, String> launchableApps() {
        PackageManager pm = getPackageManager();
        Map<String, String> byPackage = new LinkedHashMap<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!pkg.equals(getPackageName()) && !byPackage.containsKey(pkg)) {
                    byPackage.put(pkg, ri.loadLabel(pm).toString());
                }
            }
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(byPackage.entrySet());
        entries.sort((a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
        Map<String, String> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries) {
            sorted.put(e.getKey(), e.getValue());
        }
        return sorted;
    }

    private String appLabel(String pkg) {
        if (pkg == null) {
            return STOCK;
        }
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            // Uninstalled target: the service already passes the key through.
            return STOCK + " (" + pkg + " not installed)";
        }
    }

    private static String slotTitle(String slot) {
        String[] parts = slot.split("\\.");
        String key = parts[0].equals("tv") ? "TV button" : "Home button";
        return key + (parts[1].equals("long") ? " – long press" : " – short press");
    }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName me = new ComponentName(this, TvKeyService.class);
        for (String s : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) {
                return true;
            }
        }
        return false;
    }
}
