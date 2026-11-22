# 32 — AXS Clock screensaver

Replaces the Colors gradient screensaver with **AXS Clock**: the time and the
full date in large type on a black background.

```
                                   20:15
                          Sunday, 22 November 2026
```

- The block moves to a new random spot once a minute (a slow 1.5 s glide),
  so no pixels stay lit in one place.
- Any remote button wakes the TV back to where you were.
- 24-hour by default. For 12-hour with AM/PM, edit
  [`apps/clock/config/clock.conf`](../../apps/clock/config/clock.conf)
  (`clock|h:mm a|EEEE, d MMMM yyyy`) and re-run `apply.sh`. The formats are
  built into the APK, so the app needs no storage permission.
- AXS Clock (`io.github.yohannaftali.axsclock`, about 13 KB) is a separate
  app from AXS Home. It requests **no permissions**: no internet, no storage.
  It uses about 0.3% of one CPU core while showing, because the clock only
  redraws when the minute changes.
- When the screensaver starts (Settings → Device Preferences → Screen saver →
  *When to start*, 15 minutes on this TV) is not changed.

## Use

```sh
sh changes/32-clock-screensaver/apply.sh     # build, install, set as screensaver
sh changes/32-clock-screensaver/check.sh     # 0 = AXS Clock is the screensaver
sh changes/32-clock-screensaver/revert.sh    # back to baseline.txt, removes AXS Clock
```

To see it without waiting: Settings → Device Preferences → Screen saver →
*Start now*, or `sh scripts/tv.sh shell am start -n com.android.systemui/.Somnambulator`.

## Baseline and revert

`baseline.txt` records the screensaver (`com.android.dreams.basic/.Colors`,
set by [#2](../2-debloat/README.md)), its two switches and that AXS Clock was
not installed. `revert.sh` puts them back, uninstalls AXS Clock, then reads
each value back and lists any difference (exit 1), or exits 2 if a TV step
fails. `VERIFY_ONLY=1` only compares.

Revert this change before reverting #2: #2 puts back the original
Ambient-mode screensaver, and its `check.sh` does not look at the
screensaver, so the two do not conflict.

## Lock-out risk

None. If AXS Clock ever crashed, Android would show its default screensaver
instead, and any button still wakes the TV. Recovery: `revert.sh`, or pick
another screensaver in Settings → Device Preferences → Screen saver.
