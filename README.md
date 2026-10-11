# TV Key

Remap the **TV** and **Home** buttons on an Android TV remote to apps of your choice — in a
21 KB APK whose background service uses about **4 MB of RAM**, with no
dependencies and no permissions beyond what it needs.

- **Short press** → app A (e.g. an IPTV app on TV, an alternative launcher on Home)
- **Long press** → app B (e.g. the stock live-TV app)
- Pick the apps on the TV in the **TV Key** app, or via adb
- Screen off, target app missing, or any other key → **passes through untouched**.
  Uninstall the target app and the button simply behaves as stock again.

General-purpose remappers (Button Mapper, Key Mapper) do this too, but they
are full apps with a resident footprint of tens of MB. On a 3 GB TV that is
already swapping, that matters.

Tested on a Sony Bravia (Google TV, Android 12, MediaTek MT5895).

## Why an accessibility service?

On Sony (and most Android TV sets) the TV key is a *global key*: the system
broadcasts it to a hard-wired component (`com.sony.dtv.tvx/.GlobalKeyReceiver`
on Sony), which then starts an explicit activity. There is no implicit intent
anywhere in that chain that another app could claim, so without root the only
hook is an accessibility service with `flagRequestFilterKeyEvents` — it sees
keys before global-key dispatch.

The service requests no accessibility events and cannot read window content;
it only filters keys, and returns immediately for every key other than
`KEYCODE_TV` and `KEYCODE_HOME`.

## Install

Sideload the APK (adb, or a file manager on the TV), open **TV Key** from the
app list, enable the service via the button shown there, then pick an app per
button and press type. The settings screen runs in its own process, so it uses
no memory once closed.

### Via adb

```sh
adb install tv-key.apk

# A freshly installed app is in "stopped" state and ignores broadcasts
# unless --include-stopped-packages is given.
adb shell am broadcast --include-stopped-packages \
  -a io.github.forger7.tvkey.CONFIGURE -p io.github.forger7.tvkey \
  --es tv.short de.telekom.magentatv.tv --es tv.long com.sony.dtv.tvx

# Enable the service. This overwrites the list: if
# `settings get secure enabled_accessibility_services` is not "null",
# append ":io.github.forger7.tvkey/.TvKeyService" to the existing value instead.
adb shell settings put secure enabled_accessibility_services io.github.forger7.tvkey/.TvKeyService
adb shell settings put secure accessibility_enabled 1
```

Slots: `tv.short`, `tv.long`, `home.short`, `home.long`. The broadcast prints
the stored mapping. Send it without extras to just read the mapping; pass an empty string to clear a slot.
Apart from the settings screen, only adb can change the mapping: the receiver requires `android.permission.DUMP`,
which the shell has and regular apps do not.

To find package names: `adb shell pm list packages`.

**Testing:** use the real remote. Keys injected with `adb shell input keyevent`
bypass accessibility key filters, so they always show the stock behaviour.

## Uninstall

Settings → Accessibility → TV Key → off, or `adb uninstall io.github.forger7.tvkey`.

## Build

No Gradle. `build.sh` fetches JDK 17 and Android build-tools 34 from nixpkgs
(this accepts the Android SDK license) and runs aapt2 → javac → d8 →
zipalign → apksigner:

```sh
./build.sh            # → build/tv-key.apk, signed with a local debug key
```

## License

TBD
