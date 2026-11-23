# #1 — AXS Home: fast minimal home screen with HDMI shortcuts

Replaces the Google TV home screen with **AXS Home** (`apps/home/`): a 46 KB
app with two permissions (read-only network status; reading media files for your
own wallpaper) and no internet access, no ads
and no recommendations.
Layout since #18:

```
15:23                                                          🖧  ☰
 Sunday, 22 November
 [YouTube] [Netflix] [Prime Video] [YouTube Music] [Browser]

                     ~ animated waves ~
 Inputs
 | 🎮 PS5 ● HDMI 1 | 🎮 Xbox ● HDMI 2 | 🎮 Switch ● HDMI 3 | ○○○ AV ● |
```

- **Apps row**: one row packed from the left, console style. The selected
  banner grows to 1.3x from its left edge, steps a little to the right (so its
  white ring never touches the page edge), the apps after it slide right, and
  its name appears centred underneath. With more apps the row scrolls
  sideways; when focus leaves the row it settles back. OK opens an app. HOME always lands on the first app.
- **Rearrange on the TV**: long-press OK on an app (the ring turns amber),
  move it with LEFT/RIGHT (it swaps places), OK to place. The order is kept across restarts and
  reboots. ☰ → *Reset app order* goes back to the `home.conf` order.
- **Inputs bar** (bottom): OK switches to that input; DOWN from the apps
  reaches it. A card shows its name and port: the name set in Home settings →
  **Inputs**, else the TV's own name from Settings → Inputs (read with
  `TvInputInfo.loadCustomLabel`, refreshed when you come back home), else the
  `home.conf` label, else only the port (#56). Home settings → Inputs also
  changes a card's icon (game console, HDMI, AV, antenna) and colour, hides it
  or resets it; these choices live only in AXS Home's own data. No TV setting
  is written. Icons are original drawings (gamepad, HDMI plug, AV jacks,
  antenna), not console logos. Long names shrink onto two lines.
- **Aerial TV** (first card): the TV's tuner input
  (`com.droidlogic.tvinput/.services.ADTVInputService/HW16`, analog + digital).
  OK opens Live TV on that input's channels, so it plays the last channel; with
  no channels scanned yet Live TV starts its channel scan (stop it with BACK →
  Stop). It has no dot: an antenna signal cannot be read like HDMI's. No
  antenna? Delete its `input|Aerial TV|…` line in `home.conf` and re-run
  `apply.sh`.
- **Layout**: the same 24 dp margin on every edge; clock and date top left,
  network and ☰ top right, the apps row centred between them and the inputs.
- **Connection dot**: green while the input is sending a picture
  (`INPUT_STATE_CONNECTED`), grey otherwise; it updates live. This TV reports
  "standby" for every input, even an empty AV port, so standby counts as grey.
- **Home settings** (☰ → *Home settings*): only the wallpaper stays visible,
  dimmed, behind a translucent panel. Categories Clock, Wallpaper, Sound,
  Inputs and Screensaver (AXS Clock tickers, sources and keys, see #33 / #48) on
  the left (UP/DOWN, RIGHT or OK to enter); option cards on the right with the
  app tiles' white ring and zoom; the selected one has an accent border and a
  check mark. Wallpaper cards are small previews (the focused one animates);
  picking one changes the wallpaper behind the page at once. BACK returns to
  the categories, then closes; HOME also closes it.
- **System monitor** (☰ → *System monitor*): live every 2 s, CPU clock (average
  core speed from cpufreq `time_in_state`, with a graph), memory, storage
  (internal and USB) and uptime; below, the apps used today with time on screen
  and last use (LEFT/RIGHT sorts, OK refreshes, BACK closes). Nothing runs while
  it is closed. `apply.sh` allows `GET_USAGE_STATS` with `appops` for the list.
  Checked on this TV: Android 11 denies apps `/proc/stat`, `/proc/loadavg` and
  thermal readings (SELinux), and hides the `cpuinfo`/`meminfo` services even
  with `DUMP`, so total CPU %, temperature and per-app CPU/RAM are not shown;
  `sh scripts/tv.sh top` gives that view on a PC.
- **Clock**: 24-hour by default, whatever the TV's setting. ☰ → *Home settings* →
  Clock switches between 24-hour (15:30) and 12-hour with AM/PM (3:30 PM); the
  choice is kept. Without a choice, the `clock|<time format>|<date format>` line
  in `home.conf` applies.
- **Wallpaper** (☰ → *Home settings* → Wallpaper):
  - Built-in, all original designs: **Waves** (blue flowing waves), **Orbit**
    (green turning rings), **Pop** (red with drifting shapes), **Bloom** (soft
    blue petals). Each is drawn once in the background and then only moved, so
    it costs about 11–12% of one CPU core with home on screen and nothing while
    another app is open.
  - Your own file: *Choose a file…* browses USB drives and the TV's Download,
    Pictures, Movies and DCIM folders (Android asks once for access to photos
    and media). PNG, JPEG, WebP, animated GIF, animated WebP and MP4 work. The
    file is copied into AXS Home's own folder, so it keeps working without
    the USB drive. Videos loop muted unless *Play sound of video wallpapers* is
    on. A file that cannot be played is removed and the default style returns.
- **Network status** (left of ☰): Ethernet or Wi-Fi icon (Wi-Fi with signal
  bars); white = online, amber = connected without internet, grey with a slash
  = offline. Updates live. The Wi-Fi network name is never shown.
- **☰ menu** (also the remote's MENU key): Settings, Google Play Store, Home
  settings, All apps, Reset app order. The remote's ALL_APPS key opens All apps directly.
- The wallpaper starts about 350 ms after the home screen appears (never
  before the tiles) and stops whenever another app is on screen.
- Measured on the TV (#18): 502–531 ms after a full restart (before #14:
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
