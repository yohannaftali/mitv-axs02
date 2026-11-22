# #1 — AXS Home: fast minimal home screen with HDMI shortcuts

Replaces the Google TV home screen with **AXS Home** (`apps/home/`): a 29 KB
app with no permissions (not even internet), no ads and no recommendations.
Layout since #14:

```
 3:06                                                         ☰
 Sunday, 22 November
 [YouTube] [Netflix] [Prime Video]        │ 🎮 PS5     HDMI 1
 [YouTube Music] [Browser]                │ 🎮 Xbox    HDMI 2
                                          │ 🎮 Switch  HDMI 3
        ~ animated waves ~                │ ○○○ AV
```

- **Apps grid**: OK opens an app. HOME always lands on the first app.
- **Rearrange on the TV**: long-press OK on an app, move it with the arrows
  (it swaps places), OK to place. The order is kept across restarts and
  reboots. ☰ → *Reset app order* goes back to the `home.conf` order.
- **Inputs sidebar**: OK switches to that input. A labelled input shows the
  label and its port; an unlabelled one only the port. Icons are original
  drawings (gamepad, HDMI plug, AV jacks), not console logos.
- **☰ menu** (also the remote's MENU key): Settings, Google Play Store, All
  apps, Reset app order. The remote's ALL_APPS key opens All apps directly.
- **Waves**: each layer is drawn once in the background at half resolution and
  then only slid sideways (20 fps), starting about 350 ms after the home screen
  appears. They stop whenever another app is on screen.
- Measured on the TV (#14): 552–560 ms after a full restart (before #14:
  ~681 ms), 26 MB of memory, about 13% of one CPU core while home is on screen
  and 0 while another app is open.

## Customise

Edit [`apps/home/config/home.conf`](../../apps/home/config/home.conf) — apps
and their default order, input labels, ports, icons and colours, menu items,
wave colours — then:

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
