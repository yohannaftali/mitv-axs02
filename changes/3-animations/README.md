# #3 — Halve system animation durations

Sets `window_animation_scale`, `transition_animation_scale` and
`animator_duration_scale` (global settings) to `0.5`, so menus and app
switches animate twice as fast. Persists across reboots.

```sh
sh changes/3-animations/apply.sh            # 0.5x
SCALE=0 sh changes/3-animations/apply.sh    # no animations at all
sh changes/3-animations/check.sh            # exit 0 = applied (pass the same SCALE)
sh changes/3-animations/revert.sh           # back to baseline.txt
VERIFY_ONLY=1 sh changes/3-animations/revert.sh   # read-only: compare the TV with baseline.txt
```

Baseline on this TV: `1.0`, `1.0`, unset. `revert.sh` deletes a key that was
unset instead of writing `1.0`.

Manually:

```sh
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
# undo
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings delete global animator_duration_scale
```

Lock-out risk: none.
