# AGENTS.md — MiTV-AXSO2 UI customisation

Single source of truth for the `planner`, `coder`, `tester` and `reviewer`
skills in `.claude/skills/`. Read it in full before working on an issue.

## Repository
- remote: https://github.com/yohannaftali/mitv-axso2
- platform: GitHub, **public**. Every commit, issue, comment and PR is
  world-readable. Use the logged-in `gh` CLI; there is no token in `.env`.
- default branch: `main`; one branch per issue, `<id>-<short-slug>`.

## Goal
Change the user interface of one Xiaomi TV, model **MiTV-AXSO2**: launcher /
home screen, system UI settings, animations, display and density, the apps
that are shown, preinstalled apps that are hidden or disabled, and companion
apps installed on the TV. Every change is made over ADB and can be reverted.

## Target device (non-sensitive, may be published)
| Fact | Value |
|---|---|
| Model | `MiTV-AXSO2` (letter O, as reported by `ro.product.model`) |
| Manufacturer / device | Xiaomi / `dangalUHD` |
| OS | Android 11 (API 30), Android TV |
| Build | RTT0.211201.001.1150 release-keys |
| Security patch | 2024-02-05 |
| Kernel | 4.9.269 (on device; the project brief said 4.9.262) |
| ABI | armeabi-v7a, armeabi (32-bit) |
| UI resolution / density | 1920x1080, 320 dpi |
| Default home | `com.google.android.tvlauncher/.MainActivity` (Google TV launcher) |
| Access | ADB over the LAN (port 5555), `shell` user, **no root** |

Verified on the device with `scripts/tv.sh info` on 2026-11-22. Re-run it after
any firmware update and update this table from its output, never from memory.

## Private data — never exposed
These live only in `.env` (git-ignored) and on the TV:
- `tv_private_ip_address` — the TV's LAN address
- `netflix_esn` — the Netflix ESN of the TV
- also: the device serial number, Android ID, MAC addresses, Wi-Fi SSID,
  Google/Netflix/Xiaomi account names, profile names shown on screen.

Rules:
1. Never print, log, commit, or post any of them, including into issues, PRs,
   comments, `CHANGE_HISTORY.md`, test fixtures or commit messages.
2. Read `.env` only through `scripts/tv.sh` / `scripts/redact.sh`. Never
   `cat .env`; refer to values as `$TV_IP` / `$NETFLIX_ESN`.
3. Every piece of TV output (`getprop`, `dumpsys`, `logcat`, `settings list`,
   `pm list`, `uiautomator dump`) goes through `scripts/redact.sh` before it is
   shown, saved in the repo or posted. Raw captures stay in `captures/`
   (git-ignored).
4. Screenshots (`captures/*.png`) can show account and profile names. Raw
   captures are never committed or uploaded; describe them in text instead.
   Exception (owner's decision, 2026-11-22): reviewed screenshots of AXS
   Home's own screens may be committed to `docs/screenshots/` after
   checking each one shows no account or profile name, no personal photo or
   video, no network name and nothing from inside another app (the app
   banners on the home screen are fine).
5. Do not read, change, spoof or work around the Netflix ESN, DRM (Widevine)
   or any app's licence checks.
6. `.githooks/pre-commit` blocks commits that contain `.env` values, `.env`
   itself, `captures/`, or ESN-shaped strings. Enable it once per clone:
   `git config core.hooksPath .githooks`. Never bypass it (`--no-verify`).

## Guardrails for changes on the TV
- **Reversible only.** Every change ships as a script under `changes/` with
  both `apply` and `revert`, and the baseline it replaces is captured first
  (`changes/<id>-<slug>/baseline.txt`, redacted).
- **Allowed without root:** `settings put` (system/secure/global),
  `pm disable-user --user 0` / `pm enable`, `pm uninstall -k --user 0`
  (restored with `cmd package install-existing`), `cmd overlay enable/disable`
  for overlays that already exist on the device, `wm size/density` (with
  `reset`), `cmd package set-home-activity`, installing / uninstalling
  user APKs, `am`/`input` to drive the UI.
- **Forbidden:** factory reset, `fastboot`/flashing, bootloader unlock,
  root exploits, writing to `/system` or `/vendor`, disabling
  `com.android.systemui`, the package installer, the settings app, the only
  launcher, the Wi-Fi/network stack, or `adbd` (each of these can lock us out
  of the TV), and changing anything about Netflix/DRM.
- **Ask before** every apply/revert/reboot on the TV during development and
  testing. Never two risky changes in a row without checking the TV still
  shows a working home screen and ADB still answers.

## Layout
| Path | Purpose |
|---|---|
| `scripts/tv.sh` | adb wrapper: `connect`, `info`, `shell`, `screenshot`, `adb` |
| `scripts/redact.sh` | scrubs private values from any output (stdin → stdout) |
| `changes/<id>-<slug>/` | `apply.sh`, `revert.sh`, `check.sh`, `baseline.txt`, `README.md` |
| `apps/` | source of companion APKs, if an issue needs one (stack decided in that issue) |
| `captures/` | raw screenshots/dumps, git-ignored |
| `.githooks/pre-commit` | secret guard |

## Conventions
- Scripts are POSIX `sh`, run from Git Bash on Windows (the dev machine);
  the TV-side commands they send must also be valid in the TV's `toybox`
  `sh`. Every TV call goes through `scripts/tv.sh`, never a hard-coded IP.
- `check.sh` exits 0 when the change is in place, 1 when it is not, and 2 when
  the TV cannot be checked (unreachable, adb error), so `tester` can verify
  apply and revert mechanically and an offline TV never counts as reverted.
- `scripts/tv.sh` exits with adb's status (the remote command's status for
  `shell`), so `set -e` stops a script at the first failed TV step. Check its
  status; never pipe it into something that hides it.
- `revert.sh` confirms the TV equals `baseline.txt` value by value (every
  package state and setting it restores) and lists any difference; `check.sh`
  exiting 1 is not enough, because it only shows the change is not fully in
  place. A failing TV step exits 2. `VERIFY_ONLY=1` runs just the comparison.
- Changes are idempotent: running `apply.sh` twice is safe; `revert.sh` on
  an unchanged TV is safe.
- Use Android 11 (API 30) commands and APIs only; check that a command exists
  on this TV (`cmd <svc> help`) before relying on it.
- Conventional commits referencing the issue: `feat(launcher): hide ad row (#3)`.
- No comments unless they explain a non-obvious why; no unrelated refactors.

## Tracked Issues
| ID | Title | Status | Last Checked |
|----|-------|--------|--------------|
| #1 | feat(launcher): fast minimal home screen with HDMI shortcuts for PS5, Xbox and Switch | closed | 2026-11-22 |
| #2 | feat(apps): disable ads, tracking and unused preinstalled apps | closed | 2026-11-22 |
| #3 | feat(display): halve system animation durations | closed | 2026-11-22 |
| #8 | fix(tooling): scripts/tv.sh hides adb exit status | closed | 2026-11-22 |
| #10 | fix(launcher): AXS Home revert.sh restores baseline.txt instead of hard-coded state | closed | 2026-11-22 |
| #12 | fix(apps,display): #2 and #3 revert.sh verify every baseline value | closed | 2026-11-22 |
| #14 | feat(launcher): AXS Home redesign with clock, inputs sidebar, menu, rearrangeable apps and animated background | closed | 2026-11-22 |
| #15 | feat(apps): install TV Bro browser from its verified GitHub release | closed | 2026-11-22 |
| #18 | feat(launcher): inputs bar at the bottom of AXS Home with connection dots | closed | 2026-11-22 |
| #20 | fix(launcher): AXS Home clock in 24-hour format | closed | 2026-11-22 |
| #22 | feat(launcher): network status icon next to the AXS Home menu | closed | 2026-11-22 |
| #24 | feat(launcher): Home settings page with 12/24-hour clock choice | closed | 2026-11-22 |
| #25 | feat(launcher): built-in animated wallpapers Waves, Orbit, Pop and Bloom | closed | 2026-11-22 |
| #26 | feat(launcher): custom wallpapers from USB and Downloads (images, GIF/WebP, MP4) | closed | 2026-11-22 |
| #29 | feat(launcher): modern app tiles with console-style focus ring, centred row and sideways scrolling | closed | 2026-11-22 |
| #32 | feat(systemui): AXS Clock screensaver with large clock and date | closed | 2026-11-22 |
| #33 | feat(systemui): market and weather panel on the AXS Clock screensaver | closed | 2026-11-22 |
| #36 | feat(launcher): console-style Home settings page with frosted panel, categories and wallpaper previews | open | 2026-11-22 |
