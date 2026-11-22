# #1 — AXS Home: fast minimal home screen with HDMI shortcuts

Replaces the Google TV home screen with **AXS Home** (`apps/home/`): a 21 KB
app with no permissions (not even internet), no ads and no recommendations.

```
Play    [ PS5 ]   [ Xbox ]   [ Nintendo Switch ]      ← one press switches HDMI 1 / 2 / 3
Watch   [Netflix] [YouTube] [Prime Video] [YouTube Music]
More    [Settings] [every other app, A–Z]
```

- HOME always lands on the first tile (PS5).
- LEFT/RIGHT stay within a row; UP/DOWN move between rows.
- Measured on the TV: 456 ms from memory, 759 ms after a full restart, 25 MB of memory
  (PatchWall used 108 MB).

## Customise

Edit [`apps/home/config/home.conf`](../../apps/home/config/home.conf) — rows,
order, labels, tile colours, which apps — then:

```sh
SKIP_BUILD=1 sh changes/1-fast-home/apply.sh
```

No rebuild is needed for config changes. Entries for apps or inputs that do not
exist are skipped.

## Apply / check / revert

```sh
sh changes/1-fast-home/apply.sh    # build, install, push config, make it HOME
sh changes/1-fast-home/check.sh    # exit 0 = AXS Home is HOME
sh changes/1-fast-home/revert.sh   # Google TV home back, AXS Home uninstalled
```

`apply.sh` disables the Google TV launcher (`pm disable-user`), because
Android TV 11 sends HOME to it while it is enabled, whatever the preferred
home is. It does that only after AXS Home has started successfully.

## Lock-out risk and recovery

Low. If AXS Home ever fails to start, Android falls back to its built-in
`com.android.tv.settings/.system.FallbackHome`, and ADB keeps working. Recover with:

```sh
sh changes/1-fast-home/revert.sh
# or by hand:
adb shell pm enable com.google.android.tvlauncher
adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
adb uninstall io.github.yohannaftali.axshome
```

## Known

- After a reboot, the Xiaomi firmware first opens PatchWall and the Mi TV+
  splash screen. HOME then goes to AXS Home. Disabling both apps is part of #2.
