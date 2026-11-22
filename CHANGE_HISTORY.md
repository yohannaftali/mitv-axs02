# Change History

Newest first. Never include the TV IP, Netflix ESN, serial, MACs or account
names — see `AGENTS.md` → Private data.

## [2026-11-22] — feat(systemui): market and weather panel on the AXS Clock screensaver
- Issue #33 created on GitHub (depends on #32)
- Scope: systemui
- Labels: enhancement, systemui

## [2026-11-22] — feat(systemui): AXS Clock screensaver with large clock and date
- Issue #32 created on GitHub
- Scope: systemui
- Labels: enhancement, systemui

## [2026-11-22] — feat(launcher): modern app tiles with focus ring, even row and sideways scrolling (#29)
- Issue #29
- Files: apps/home/src/.../HomeActivity.java, changes/1-fast-home/README.md
- Tiles: rounded, clipped banners with a soft shadow; focus draws a white ring outside the banner (visible on white banners), zooms 1.1x and shows the app name; amber ring while moving
- One row spread evenly; more apps scroll sideways; rearrange is LEFT/RIGHT only
- Fixed during test: the page and All apps layouts clipped the zoomed first tile at their padding
- Verified on TV: ring on all sides for the first and last tile, move mode, 9-app scroll (temporary config, then restored), All apps, cold start 564–593 ms (avg 575, +6%), wallpaper CPU ~13% of one core
- TV state left: applied

## [2026-11-22] — chore: rename repository to mitv-axso2
- GitHub repo renamed from `mitv-axs02` (zero) to `mitv-axso2` (letter O, matching the model); GitHub redirects the old URL
- Files: README.md (title, clone commands, model note), AGENTS.md (remote), planner and reviewer skills (repo slug)
- Local clones: `git remote set-url origin https://github.com/yohannaftali/mitv-axso2`

## [2026-11-22] — docs: README screenshots of AXS Home
- Files: README.md ("What it looks like"), docs/screenshots/*.webp (Waves, Orbit, Pop, Bloom, Home settings; 1280 px, 17–33 KB each), AGENTS.md (rule 4 exception for reviewed AXS Home screenshots, owner's decision)
- Each screenshot reviewed: only AXS Home tiles, clock, date, inputs and the network icon; no account, profile or network name
- Menu and rearrange screenshots not added: the TV went to standby (ADB port closed) during capture

## [2026-11-22] — feat(launcher): Home settings, built-in and own wallpapers (#24, #25, #26)
- Issues #24, #25, #26 (one branch and PR, because they share the settings screen and wallpaper code)
- Files: apps/home/src/…/SettingsPanel.java, StyleView.java, MediaBackdrop.java, Backdrop.java (new; WaveView.java replaced by StyleView), HomeActivity.java, AndroidManifest.xml (READ_EXTERNAL_STORAGE), config/home.conf, changes/1-fast-home/README.md, README.md
- Home settings (☰): clock 24-hour / 12-hour AM-PM; wallpaper Waves / Orbit / Pop / Bloom / own file; play sound of video wallpapers (off by default)
- Own files: on-TV browser for USB drives and Download / Pictures / Movies / DCIM (this TV has no system file picker, only a stub); file copied into the app folder; PNG, JPEG, WebP, animated GIF, animated WebP (AnimatedImageDrawable), MP4 (MediaPlayer on a TextureView, centre-cropped); a file that cannot be played is deleted and the default style returns
- Verified on the TV: settings page and D-pad; 12-hour shows 3:45 PM, kept after app restart, back to 24-hour; each built-in style visually and at 11–12% of one core on home, 0 with YouTube open; storage permission prompt; PNG (0% CPU), GIF, animated WebP and MP4 animate (13–16% of one core) and stop with YouTube open; broken MP4 falls back without a crash; cold start median about 542 ms; no crashes; test files removed from the TV afterwards
- Not verified: hearing video sound (the switch toggles; audio is for the owner to check); choices across a reboot (same SharedPreferences mechanism as the app order, verified in #14)
- Limitation: picking a broken file replaces the previous own file, so the fallback is the default style, not the previous file
- TV state left: Waves, 24-hour, video sound off

## [2026-11-22] — feat(launcher): network status icon next to the menu (#22)
- Issue #22
- Files: apps/home/src/…/NetIcon.java (new), HomeActivity.java, apps/home/AndroidManifest.xml (ACCESS_NETWORK_STATE), changes/1-fast-home/README.md, README.md
- Default-network callback while home is on screen; Ethernet / Wi-Fi (0–4 bars) icon; white validated, amber not validated, grey slashed offline; no SSID used
- Verified on the TV: icon reports "Ethernet, online"; only permission ACCESS_NETWORK_STATE (granted), no INTERNET; cold start 513–521 ms; no crashes
- Not verified on the TV: amber and offline states (disconnecting the network would also cut ADB)
- TV state left: applied

## [2026-11-22] — fix(launcher): AXS Home clock in 24-hour format (#20)
- Issue #20
- Files: apps/home/src/…/HomeActivity.java, apps/home/config/home.conf (clock line; AV label restored, owner preference), changes/1-fast-home/README.md, README.md
- The TV has no 12/24-hour preference (time_12_24 unset), so the English default was 12-hour; AXS Home now uses the home.conf format (default HH:mm) for both modes
- Verified on the TV: clock 15:31 matching the TV time (24-hour), date unchanged, input label AV; no crashes
- TV state left: applied

## [2026-11-22] — feat(launcher): inputs bar at the bottom with connection dots (#18)
- Issue #18
- Files: apps/home/src/…/HomeActivity.java, apps/home/config/home.conf (AV renamed RCA), changes/1-fast-home/README.md, README.md
- Inputs moved from the right sidebar to a row of four cards at the bottom; apps now one row of five; status dot per input from TvInputManager.getInputState + onInputStateChanged (green = CONNECTED, grey otherwise); long labels auto-shrink
- Verified on the TV: layout screenshots; DOWN/UP between apps and inputs; OK tunes HW5/HW6/HW7/HW1; all dots grey with consoles off (all inputs report standby); cold start 502–531 ms; about 13% of one core on home; no crashes
- Not verified: a dot turning green with a console on (owner to check)
- TV state left: applied

## [2026-11-22] — feat(launcher): AXS Home redesign (#14)
- Issue #14
- Files: apps/home/src/…/HomeActivity.java (rewritten), WaveView.java and IconView.java (new), apps/home/config/home.conf (new format), changes/1-fast-home/README.md, README.md
- Clock + date top left, ☰ menu top right (also MENU key; ALL_APPS key opens All apps), 3-column apps grid rearrangeable with long-press OK (order in SharedPreferences, Reset app order in the menu), inputs sidebar with label + port and original gamepad/HDMI/AV icons (HDMI 1–3 and AV), animated waves
- Waves: first version drew 4 anti-aliased paths per frame and cost about 1.3 CPU cores; replaced by layers pre-rendered once off the UI thread at half resolution and slid at 20 fps: about 13% of one core with home on screen, 0 with another app open
- Verified on the TV: layout screenshots; HDMI 1/2/3 and AV switch from the sidebar (doTune HW5/HW6/HW7/HW1); menu items and BACK; All apps; move with long-press, arrows and OK; order kept after app restart and reboot; reset order; cold start 552–560 ms (before ~681 ms); 26 MB PSS; YouTube opens from home with waves running; boot lands on AXS Home; checks of #1, #2, #3, #15 pass; no crashes; APK 29 KB, no permissions
- Not re-run: changes/1-fast-home/revert.sh (unchanged since #10, where it was tested)
- TV state left: new AXS Home applied, default app order

## [2026-11-22] — feat(apps): install TV Bro browser from its verified GitHub release (#15)
- Issue #15
- Files: changes/15-tv-bro/ (apply, check, revert, baseline, pinned.txt, README), apps/home/config/home.conf (Browser tile), README.md, .gitignore (.cache/)
- Pinned: v3.0.7 generic-geckoExcluded (3.8 MB, uses the TV's WebView, Chromium 139); SHA-256 equals GitHub's published digest; signing cert identical on 3.0.6 and 3.0.7
- Verified: tampered APK and wrong-signer APK refused; unreachable TV gives check/revert 2; on the TV install twice, Browser tile opens TV Bro, desktop Wikipedia loads, cursor and scrolling work with the D-pad; revert uninstalls (check 1, verify-only 0), revert again, re-install, tile back; no crashes
- Not verified: text entry with the remote (automated cursor placement too imprecise); merged by owner decision, to be checked by the owner
- TV state left: TV Bro installed, Browser tile on AXS Home

## [2026-11-22] — Issues #14 and #15 filed: AXS Home redesign, TV Bro browser
- #14 feat(launcher): AXS Home redesign (clock/date, inputs sidebar with HDMI 1–3 + AV, ☰ menu, on-TV app reordering, async wave background) — labels enhancement, launcher
- #15 feat(apps): install TV Bro (geckoExcluded build, verified SHA-256 and signing cert) — labels enhancement, apps
- Decisions: TikTok skipped; original gamepad/HDMI/AV icons instead of console logos

## [2026-11-22] — fix(apps,display): #2 and #3 revert.sh verify every baseline value (#12)
- Issue #12
- Files: changes/2-debloat/revert.sh, changes/3-animations/revert.sh, changes/2-debloat/README.md, changes/3-animations/README.md, AGENTS.md (new value-by-value revert rule), .claude/skills/coder/SKILL.md
- revert.sh: validates baseline values before any TV command; TV steps record every failure (exit 2); reads every package state, the screensaver and the three scales back and lists differences (exit 1); VERIFY_ONLY=1 compares read-only; #2 handles disabled/absent baseline entries and an unset screensaver
- Verified: verify-only lists all 15 / 3 differences on the applied TV and passes against a matching baseline; injected or missing baseline values exit 1 before any TV command; unreachable TV exits 2; on the TV revert #3 and #2 (equal baseline, check 1), revert again, verify-only 0, re-apply (check 0); #1 untouched; no crashes
- TV state left: #1–#3 applied

## [2026-11-22] — fix(apps,display): #2 and #3 revert.sh verify every baseline value
- Issue #12 created on GitHub (from the post-merge review of #8 / #10: #8 pass with notes, #10 pass)
- Scope: apps, display
- Labels: bug, apps, display

## [2026-11-22] — fix(launcher): AXS Home revert.sh restores baseline.txt (#10)
- Issue #10 created on GitHub (from the post-merge review of #1) and fixed
- Files: changes/1-fast-home/revert.sh
- revert.sh reads home_activity, google_tvlauncher_enabled and axshome_installed from baseline.txt, validates them before any TV command, re-enables the Google launcher / uninstalls AXS Home only as the baseline says, and confirms HOME equals the baseline
- Verified: missing keys and an injected value abort before any TV command; unreachable TV exits 2 with a message; on the TV revert (HOME = baseline, launcher enabled, AXS Home uninstalled, check 1), revert again, re-apply (check 0); #2 and #3 untouched
- TV state left: #1–#3 applied

## [2026-11-22] — fix(tooling): scripts/tv.sh returns adb exit status (#8)
- Issue #8
- Files: scripts/tv.sh; changes/{1-fast-home,2-debloat,3-animations}/check.sh and revert.sh; changes/2-debloat/apply.sh; AGENTS.md; README.md; .claude/skills/coder/SKILL.md; .claude/skills/reviewer/SKILL.md (kernel 4.9.269)
- tv.sh: adb output captured to a temp file, redacted, adb status returned; `connect` fails when adb could not connect; `screenshot` fails on an empty capture; `TV_ADB_PORT` env override
- check.sh: 0 applied, 1 not applied, 2 cannot check; revert.sh never reports success unless check.sh says 1
- Verified: exit 3 / 0 / 1 propagate; stdout and stderr still redacted; unreachable TV (closed port) gives check 2 and every apply/revert fails without a success message; set -e stops at a failing step; on the TV, revert 3→2→1 (checks 1) and apply 1→2→3 (checks 0), no crashes
- TV state left: all three applied

## [2026-11-22] — fix(tooling): scripts/tv.sh hides adb exit status
- Issue #8 created on GitHub (from the post-merge review of #1–#3: #1 and #2 pass with notes, #3 passes)
- Scope: tooling
- Labels: bug, tooling

## [2026-11-22] — docs: model name is MiTV-AXSO2 (letter O)
- README.md, AGENTS.md, skill descriptions and scripts/tv.sh corrected; repo name `mitv-axs02` unchanged

## [2026-11-22] — feat(display): halve system animation durations (#3)
- Issue #3
- Files: changes/3-animations/
- Verified on TV: apply (twice), check 0; revert deletes the unset key and matches baseline, check 1; revert twice; survives reboot
- TV state left: applied

## [2026-11-22] — feat(apps): disable ads, tracking and unused preinstalled apps (#2)
- Issue #2, merged in PR #5
- Files: changes/2-debloat/ (packages.txt lists the 14 packages)
- Verified on TV: all disabled; Netflix, YouTube, Prime Video, Settings, Play Store open; HDMI switch; no crashes; screensaver moved to Colors; revert matches baseline; idempotent; survives reboot; boot now opens AXS Home directly (no PatchWall / Mi TV+ splash); MemAvailable after boot ~1008 MB (stock ~933 MB)
- Not verified: physical remote buttons (ADB key events only)
- TV state left: applied

## [2026-11-22] — #1 closed: tested and merged in PR #4

## [2026-11-22] — feat(launcher): AXS Home with HDMI shortcuts (#1)
- Issue #1
- Files: apps/home/ (AndroidManifest.xml, res/, src/…/HomeActivity.java, config/home.conf, build.sh), changes/1-fast-home/, scripts/tv.sh (install, push, MSYS path fix), README.md, .gitignore
- Verified on TV: build (21 KB, no permissions); apply; HOME shows AXS Home; PS5/Xbox/Switch tiles tune HDMI 1/2/3 (DroidLogic doTune log); Netflix, YouTube, Prime Video open; HOME resets focus to PS5; row-end focus stays in row; bogus config entries skipped without crash; config change applied without rebuild; warm 456 ms / cold 759 ms; 25 MB PSS; revert restores baseline; revert and apply idempotent; survives reboot
- Plan change: Google TV launcher disabled (`pm disable-user`), Android TV 11 ignores set-home-activity while it is enabled (owner approved)
- Not fixed here: after boot the firmware opens PatchWall then the Mi TV+ splash; handled by #2. YouTube Music forwards to the YouTube app by itself (firmware/app behaviour)
- TV state left: applied

## [2026-11-22] — Issues #1–#3 filed: new home screen, debloat, animations
- #1 feat(launcher): fast minimal home screen with HDMI shortcuts for PS5, Xbox and Switch — labels enhancement, launcher
- #2 feat(apps): disable ads, tracking and unused preinstalled apps — labels enhancement, apps (depends on #1)
- #3 feat(display): halve system animation durations — labels enhancement, display

## [2026-11-22] — chore(skills): adopt planner, coder, tester, reviewer from telepad
- Skills copied from the telepad project and adapted to this repo (GitHub,
  ADB-driven UI changes on MiTV-AXS02, Android 11, patch 2024-02-05, kernel 4.9.262)
- Added `scripts/tv.sh`, `scripts/redact.sh`, `.githooks/pre-commit`, `.env.example`
- Verified: redactor on synthetic input; TV reachable on the LAN
- Verified: ADB session authorised; `scripts/tv.sh info` read device facts into AGENTS.md
- Fixed: `tv.sh` redact helper ran `adb shell` without `</dev/null`, which swallowed piped output
