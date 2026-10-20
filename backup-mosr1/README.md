# MiTV-MOSR1 (hermano_eu) — Debloat + Custom Launcher

**TV**: Xiaomi MiTV-MOSR1 · codename `hermano_eu`  
**OS**: Android TV 11 (SDK 30) · MediaTek MT5867  
**Firmware**: RTT8.210119.001.1129  
**Done**: Tuesday 20 October 2026  
**TV IP**: 192.168.1.8

---

## What was done

### Debloat — 29 packages disabled (not uninstalled)

| Category | Packages |
|---|---|
| Telemetry / analytics | `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.feedback`, `com.google.android.partnersetup` |
| PatchWall / ad rows | `com.mitv.tvhome.atv`, `com.mitv.tvhome.michannel`, `com.xm.webcontent`, `com.google.android.tvrecommendations`, `android.autoinstalls.config.xiaomi.amelie` |
| Demo mode | `fusion.android.tv.demo` |
| Cleaner / nagware | `com.xiaomi.mitv.tvmanager`, `com.mitv.toolhouse`, `com.xiaomo.tv.milegal` |
| Miracast / Mi Cast (Chromecast kept) | `com.xiaomi.mitv.smartshare`, `com.xiaomi.wfdsinkhelperservice`, `com.mitv.milinkservice` |
| Preloaded apps | `com.mitv.gallery`, `com.xiaomi.mimusic2`, `com.google.android.play.games`, `com.google.android.marvin.talkback`, `com.xiaomi.mitv.mediaexplorer`, `com.google.android.backdrop`, `com.xiaomi.floatingframe`, `com.google.android.videos`, `com.mitv.videoplayer`, `com.mxtech.videoplayer.television`, `com.android.printspooler`, `com.mitv.dream` |
| Factory test (claimed HOME) | `com.duokan.factorytest` |

**Everything is `pm disable-user --user 0` — reversible, not deleted.**

### Launcher

Replaced stock Google launcher with `com.gathin.tvhome` (Apple TV-style):

- **Dock**: Netflix · YouTube · JioHotstar · SonyLIV
- **Grid**: all remaining apps in a 4-column scrollable grid
- **Top bar**: clock, date, Inputs / Wi-Fi / Settings pills
- **Live TV**: handled via the Inputs menu (com.mitv.livetv)

### AdGuard DNS

Private DNS set to `dns.adguard-dns.com` — blocks most ad/tracking domains network-wide.  
Note: **does not block YouTube ads** (those are served from YouTube's own domain).

---

## Re-enable a single disabled app

```bash
TV_HOST=192.168.1.8 tools/venv/bin/python -I tools/tv.py 'pm enable com.PACKAGE.NAME'
```

Replace `com.PACKAGE.NAME` with the package from the table above.

## Re-enable everything / restore stock launcher

```bash
TV_HOST=192.168.1.8 bash backup-mosr1/restore-mosr1.sh
```

This re-enables all 29 packages and sets `com.google.android.tvlauncher` back as Home.

## Rebuild and reinstall the custom launcher

After editing `launcher/app/src/main/res/values/config.xml` (dock order, columns, etc.):

```bash
TV_HOST=192.168.1.8 bash backup-mosr1/build-and-install-mosr1.sh
```

## Change the dock apps

Edit `launcher/app/src/main/res/values/config.xml`:

```xml
<string-array name="dock_apps">
    <item>com.netflix.ninja</item>          <!-- Netflix -->
    <item>com.google.android.youtube.tv</item>  <!-- YouTube -->
    <item>in.startv.hotstar</item>          <!-- JioHotstar -->
    <item>com.sonyliv</item>               <!-- SonyLIV -->
</string-array>
```

Replace any `<item>` with the package name of the app you want (find package names with  
`TV_HOST=192.168.1.8 tools/venv/bin/python -I tools/tv.py 'pm list packages'`).  
Then run `build-and-install-mosr1.sh` to push the new version.

## Undo AdGuard DNS

```bash
TV_HOST=192.168.1.8 tools/venv/bin/python -I tools/tv.py \
  'settings put global private_dns_mode opportunistic'
```

## Re-enable ADB when you need it again

1. On the TV: **Settings → Device Preferences → Developer options → USB debugging → ON**
2. On your Mac:
   ```bash
   adb connect 192.168.1.8:5555
   # Accept the "Allow USB debugging?" prompt on TV
   TV_HOST=192.168.1.8 tools/venv/bin/python -I tools/tv.py --probe
   ```
3. When done, disable again:
   ```bash
   adb -s 192.168.1.8:5555 shell settings put global adb_enabled 0
   ```
   Also revoke authorizations on the TV: **Settings → Developer options → Revoke USB debugging authorizations** (stops the TV from auto-accepting future connections).

## Handle setupwraith (optional — improves launcher stability)

`com.google.android.tungsten.setupwraith` is still enabled. On this firmware `set-home-activity`
held fine without disabling it, but if after a firmware update the stock launcher ever comes back:

1. On the TV: **Settings → Network & Internet → Wi-Fi → [your network] → Forget**, then re-join.
   (The saved network is owned by setupwraith; Android deletes it when setupwraith is disabled.
   Re-joining creates a new entry owned by the system, which survives the disable.)
2. Then:
   ```bash
   TV_HOST=192.168.1.8 tools/venv/bin/python -I tools/tv.py \
     'pm disable-user --user 0 com.google.android.tungsten.setupwraith'
   ```
3. Re-enable it before a factory reset:
   ```bash
   TV_HOST=192.168.1.8 tools/venv/bin/python -I tools/tv.py \
     'pm enable com.google.android.tungsten.setupwraith'
   ```

## Packages that must NOT be disabled

| Package | Why |
|---|---|
| `com.mitv.livetv` | owns the HDMI/source switcher (SelectInputActivity); Inputs menu dies without it |
| `mitv.service` | PERSISTENT; remote key handling, TvServer |
| `com.mitv.overseaservice` | PERSISTENT; hosts the Netflix key on the remote |
| `com.mediatek.tvinput` | HDMI / tuner framework |
| `com.mediatek.tv.service` | core TV service |
| `com.mi.bluetooth` | Bluetooth remote pairing |
| `com.google.android.gms` | Google Play Services |
| `com.android.vending` | Play Store |
| `com.google.android.katniss` | Google Assistant (kept by choice) |
| `com.google.android.apps.mediashell` | Chromecast built-in (kept by choice) |
| `com.google.android.tv.remote.service` | phone-as-remote (kept by choice) |
| `com.xiaomi.mitv.updateservice` + `com.mitv.updater` | OTA firmware updates (kept by choice) |
