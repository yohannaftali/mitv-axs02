# #2 — Disable ads, tracking and unused preinstalled apps

Disables the apps in [`packages.txt`](packages.txt) for user 0 with
`pm disable-user`. Nothing is deleted; `revert.sh` turns them back on.

| Package | What it is |
|---|---|
| `com.miui.tv.analytics` | Xiaomi analytics |
| `com.xiaomi.statistic` | Xiaomi usage statistics |
| `com.mitv.tvhome.mitvplus` | Mi TV+ (free channels with ads; also opened its splash after boot) |
| `com.mitv.tvhome.michannel` | Mi Channel (promoted content) |
| `com.xm.webcontent` | Xiaomi web content / promos |
| `com.google.android.tvrecommendations` | Google recommendation rows |
| `com.google.android.backdrop` | Ambient mode screensaver (74 MB) |
| `com.mitv.tvhome.atv` | PatchWall (108 MB; opened itself after boot) |
| `com.google.android.play.games` | Play Games |
| `com.google.android.videos` | Google TV / Play Movies |
| `com.mitv.gallery` | Mi gallery |
| `com.xiaomi.mimusic2` | Mi music |
| `com.xiaomi.mitv.handbook` | Mi TV handbook |
| `com.xiaomi.mitv.mediaexplorer` | Mi media explorer |

Because Ambient mode is disabled, the screensaver is switched to Android's
built-in **Colors** (`com.android.dreams.basic/.Colors`). `revert.sh` puts
back whatever `baseline.txt` recorded.

Kept on purpose: Play Store, Play Services, Netflix, YouTube, Prime Video,
YouTube Music, voice search, Chromecast, settings, SystemUI, the TV input
apps (`com.android.tv`, `com.droidlogic.tvinput`), Xiaomi quick settings and
the firmware updater.

## Apply / check / revert

Needs #1 (AXS Home) applied first, because PatchWall is on the list.

```sh
sh changes/2-debloat/apply.sh
sh changes/2-debloat/check.sh     # exit 0 = all listed packages disabled
sh changes/2-debloat/revert.sh    # re-enable what baseline.txt says was enabled
```

To keep an app, delete its line from `packages.txt` and run
`pm enable <package>`, or just run `revert.sh` and then `apply.sh`.

Manually:

```sh
adb shell pm disable-user --user 0 <package>
adb shell pm enable <package>
adb shell pm list packages -d          # what is disabled
```

## Lock-out risk

Low. None of these is a HOME app, settings, the installer, the network stack
or ADB.
