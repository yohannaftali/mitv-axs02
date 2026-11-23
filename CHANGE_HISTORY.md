# Change History

Newest first. Never include the TV IP, Netflix ESN, serial, MACs or account
names — see `AGENTS.md` → Private data.

## [2026-11-23] — feat(apps): on-screen keyboard hide key, suggestions and phone QR (#47)
- Issue #47
- Files: apps/remote/src/.../RemoteIme.java (rows built per field: suggestion row for e-mail / web e-mail / URI fields, numbers, letters or #+= symbols, bottom row ⇧ #+= space ⌫ Hide 📱 done; UP from the top row hides; 📱 QR panel with the address, OK/BACK return; "@domain" chips replace a typed domain), AndroidManifest.xml (1.2), changes/40-phone-remote/README.md, README.md, docs/screenshots/keyboard.webp (address blurred)
- Deviation from the issue: the "123" key became "#+=" (numbers already have their own row); the hide key is labelled "Hide ▾"
- Verified on TV (TV Bro, local test form): e-mail chips and @-replacement, URL chips, no chips in password/plain fields, Hide and UP-to-hide then D-pad reaches the app, 📱 panel and BACK, symbols, phone text into the field; revert → check 1, apply → check 0; no crash
- Not verified: YouTube search and TV Settings with the new layout, reboot
- TV state left: applied

## [2026-11-23] — fix(apps): phone remote at a fixed address without token or time limit (#46)
- Issue #46
- Files: apps/remote/ (RemoteServer: plain HTTP + WebSocket on 8077 bound to all interfaces, started by RemoteIme.onCreate, no token, no idle stop, foreign-Origin WebSocket refused; SelfSignedCert removed; QrActivity shows the address and QR; Qr.bitmap; remote.html ws://, keeps reconnecting, reconnects when the page is shown again; version 1.1), changes/40-phone-remote/check.sh (port 8077 must answer HTTP, retries while the keyboard rebinds), changes/40-phone-remote/README.md, README.md, docs/screenshots/phone-remote.webp (QR pixelated, address blurred)
- Found: `am force-stop` of AXS Remote makes Android switch the keyboard back to Gboard (re-set with `ime set`); don't force-stop it
- Verified on TV (PC as the phone): page over HTTP, two clients at once, foreign Origin 403, keys without token, server back when AXS Remote becomes the keyboard again; revert → check 1, revert idempotent, apply twice → check 0; no crash
- Not verified: a real iPhone (owner), reboot
- TV state left: applied

## [2026-11-23] — feat(systemui): Screensaver settings in Home settings with selectable market data sources and fresher values
- Issue #48 created on GitHub
- Scope: systemui (AXS Clock) and launcher (Home settings)
- Labels: enhancement, systemui
- Owner's report: market data looked old (ECB FX is daily; feed only runs while the screensaver shows)
- Yahoo Finance and keyed providers opt-in by owner's choice; TradingView login left out (no public API, scraping against its terms, password on the TV)

## [2026-11-23] — feat(apps): on-screen keyboard hide key, email/URL suggestions and phone-typing QR
- Issue #47 created on GitHub
- Scope: apps
- Labels: enhancement, apps
- Depends on: #46

## [2026-11-23] — fix(apps): phone remote at a fixed address without token or time limit
- Issue #46 created on GitHub
- Scope: apps
- Labels: bug, apps
- Owner's report: QR code does not work on iPhone; owner's choice: no session, token or limit, QR only gives the address (LAN trade-off accepted)

## [2026-11-22] — feat(launcher): console-style enlarged focus in the apps row (#44)
- Issue #44 (owner's request)
- Files: apps/home/src/.../HomeActivity.java (focusRow: 1.3x from the left edge, 10 dp nudge, following tiles slide, shared name label centred under the tile, settle and scroll back; row packed from the left), changes/1-fast-home/README.md, docs/screenshots/home-*.webp
- Iterations with the owner on the TV: name beside the tile, then under it left-aligned, then centred (final)
- Verified on TV: first, middle and last tile (all on screen with five apps), name placement, settle when moving to the inputs, move mode (amber, enlarged), cold start 609–630 ms, no crash
- Not verified: reboot
- TV state left: applied

## [2026-11-22] — feat(launcher): Aerial TV card and tighter home layout (#42)
- Issue #42
- Files: apps/home/src/.../HomeActivity.java (tuner card opens TvContract.buildChannelsUriForInput, no dot for tuners; two-line auto-sized card names; equal 24 dp page margins; clock 60 sp / date 22 sp without font padding; ☰ and network icon in the top-right corner; apps row vertically centred; tiles and input cards aligned to the page margin; "Inputs" label removed; 12 dp card gaps), IconView.java (antenna icon), apps/home/config/home.conf (Aerial TV line, documented), README.md, changes/1-fast-home/README.md, docs/screenshots/home-{waves,orbit,pop,bloom}.webp
- Found on the TV: the tuner input is com.droidlogic.tvinput/.services.ADTVInputService/HW16; with no channels Live TV starts a channel scan (twice during testing, both stopped; one had been started by Live TV itself when the phone remote opened it during #40)
- Owner's layout requests during the work: narrower side margins, top bar in the corners, bigger clock/date, centred apps row, inputs aligned with apps and bottom margin, no "Inputs" label, wider cards
- Verified on TV: Aerial TV card opens Live TV on the tuner (channel scan as there are no channels), HDMI 1 still switches, all five names fit, ☰ focus ring inside the screen, cold start 601–637 ms, no crash, no INTERNET, check.sh 0
- Not verified: reboot; a TV with scanned channels (last-channel playback)
- TV state left: applied

## [2026-11-22] — feat(launcher): Aerial TV card before HDMI 1 in the inputs bar
- Issue #42 created on GitHub
- Scope: launcher
- Labels: enhancement, launcher
- Tuner input on this TV: com.droidlogic.tvinput/.services.ADTVInputService/HW16 (analog + digital)

## [2026-11-22] — feat(apps): Phone remote — browser remote and keyboard via QR code (#40)
- Issue #40
- Files: apps/remote/ (new AXS Remote app: AndroidManifest.xml, build.sh, res/, assets/remote.html, src/.../RemoteIme.java, RemoteServer.java, SelfSignedCert.java, Qr.java, QrActivity.java), changes/40-phone-remote/ (apply, revert, check, baseline, README), apps/home/config/home.conf (☰ item), README.md, docs/screenshots/phone-remote.webp
- Design (owner's choices): AXS Remote is the TV's input method and sends D-pad/OK/Back/Menu/number/channel keys and text to the focused app; HOME via home intent (SYSTEM_ALERT_WINDOW app-op); volume via the audio service; media keys to the active session. HTTPS + WebSocket on port 8077 with a self-signed EC certificate made on the TV (IP SAN, serverAuth, 397 days); pairing token in the QR code, renewed on each open; server only on demand, 10-minute idle stop. QR encoder and certificate builder written in plain Java (QR verified against zxing for versions 1–10). Aerial TV panel added on owner's remark.
- Fixed during test: crash from a network write on the main thread (focus change); TV Settings ignored keyboard-type events (keys now sent as D-pad source); on-screen keyboard hidden because the remote counts as a hardware keyboard; ▼ button stuck "pressed" (CSS class clash)
- Verified on TV (PC acting as the phone): page served; wrong and old tokens 403; AXS Home, YouTube, TV Bro, TV Settings react to arrows/OK/Back; typing into TV Bro address bar and a web page field; HOME from apps; volume up/down restored; Live TV opens; on-screen keyboard typing with the remote; revert restores Gboard and removes the app and ☰ item; revert and apply idempotent; no crash; AXS Home still has no INTERNET; README image has the QR pixelated (zxing reads nothing)
- Not verified: Netflix (blocks screenshots, no UI tree — owner to check with a phone); a real phone accepting the certificate; CH keys with an antenna; 10-minute idle stop; reboot
- TV state left: applied

## [2026-11-22] — feat(apps): Phone remote — QR code opens a browser remote and keyboard served by the TV
- Issue #40 created on GitHub
- Scope: apps (new AXS Remote APK, IME) and launcher (menu item)
- Labels: enhancement, apps
- Owner's choices: control through an AXS input method (no power button), HTTPS with a self-signed certificate on port 8077

## [2026-11-22] — feat(launcher): System monitor page and `tv.sh top` (#38)
- Issue #38
- Files: apps/home/src/.../MonitorPanel.java (new), HomeActivity.java (menu type `monitor`, closes on HOME), AndroidManifest.xml (PACKAGE_USAGE_STATS, QUERY_ALL_PACKAGES), apps/home/config/home.conf (menu line), changes/1-fast-home/apply.sh (`appops set … GET_USAGE_STATS allow`) and README.md, scripts/tv.sh (`top [cpu|mem] [once]`), README.md, AGENTS.md, docs/screenshots/system-monitor.webp
- Found on the TV: an app is denied /proc/stat, /proc/loadavg and thermal_zone temp (SELinux), and `dumpsys cpuinfo/meminfo` from the app says "Can't find service" even with DUMP granted. Owner chose: page shows what apps can read + PC-side htop; DUMP dropped
- Page: CPU clock (average core speed from cpufreq time_in_state, graph), memory, storage (internal + USB), uptime every 2 s; apps used today (time on screen, last used) every 30 s; model/Android/patch/kernel line; nothing runs while closed
- `tv.sh top`: two top samples 2 s apart, last one sorted by CPU (or `mem`) on the PC, redacted, refreshed until Ctrl-C (`once` for one shot)
- Verified on TV: opens from ☰, values sane, sort and refresh keys, BACK closes, monitor thread gone after close, CPU with page open ~10% of one core (mostly the wallpaper), no crash, still no INTERNET; `tv.sh top` and `top mem once` work; screenshot reviewed (app names and usage times only)
- Not verified: reboot (appops setting persists by design); USB storage line (no drive plugged in)
- TV state left: applied

## [2026-11-22] — feat(launcher): System monitor page with live CPU, RAM, storage and a top-style app list
- Issue #38 created on GitHub
- Scope: launcher
- Labels: enhancement, launcher

## [2026-11-22] — feat(launcher): console-style Home settings page (#36)
- Issue #36
- Files: apps/home/src/.../SettingsPanel.java (rewritten layout; settings keys, file copy and storage logic unchanged), Glyph.java (new, line icons drawn in code), HomeActivity.java (hide home content while settings is open, accent colour, previews, close settings on HOME), StyleView.java (wave layers end exactly on the bitmap edge: fixes a thin seam where the tiled copies meet), changes/1-fast-home/README.md, README.md, docs/screenshots/home-settings.webp
- Layout: transparent dialog over the live wallpaper (home tiles hidden), dark scrim, categories Clock / Wallpaper / Sound with icons, translucent panel with cards (white ring outside the card, 1.05x zoom, accent border and check for the selected one), wallpaper preview cards (focused one animates), toggle switch for video sound, file browser rows in the same style, soft fading scroll edges
- Fixed during test: time centring in clock cards; cards drawing outside the panel when scrolled; duplicated cards when the focused card was removed during a re-render (focus jumped to a category and re-rendered re-entrantly); settings stacking when HOME was pressed with it open
- Verified on TV: open from ☰; D-pad between categories and cards; file browser and BACK; Orbit picked and shown behind at once, accent follows, Waves restored; sound toggle (owner's value On kept); 12-hour shows on home, 24-hour restored; BACK/BACK closes and home reappears with focus on ☰; no crashes; no INTERNET; cold start 602–630 ms (avg 616, +7%); screenshot reviewed (AXS Home only)
- Not verified: reboot; picking an own file from USB in the new browser (copy logic unchanged from #26)
- TV state left: applied

## [2026-11-22] — feat(launcher): console-style Home settings page with frosted panel, categories and wallpaper previews
- Issue #36 created on GitHub
- Scope: launcher
- Labels: enhancement, launcher

## [2026-11-22] — main deployed to the TV (50290f4)
- Built AXS Home and AXS Clock 1.1 from main and applied #1 and #33; check.sh 0 for #1, #2, #3, #15, #32, #33
- Home screen and screensaver checked on screen; no crashes for either app; secret scan of #32..#33 clean
- Test notes posted on #32 and #33
- TV state left: all changes applied

## [2026-11-22] — feat(systemui): market and weather panel on the AXS Clock screensaver (#33)
- Issue #33
- Files: apps/clock/ (AndroidManifest.xml 1.1 with INTERNET, ACCESS_NETWORK_STATE, ACCESS_COARSE_LOCATION; config/clock.conf; src/.../ClockDream.java, Conf.java, Feed.java, StyleView.java and Backdrop.java copied from AXS Home), changes/33-market-panel/ (apply, revert, check, baseline, README), README.md, .env.example
- Sources (free, keyless, credited on screen): CoinGecko (BTC/ETH/SOL), goldprice.dev (XAU, 15 min delayed), ECB via Frankfurter (IDR/EUR/GBP/JPY, daily), Open-Meteo (weather). NASDAQ, S&P 500, Dow, IHSG and oil left out: no free source allows it (owner chose clean sources only)
- Owner requests during the work: dimmed animated background (Waves), city name on the weather line, update and FX dates in the footer; weather location automatic from the TV's network location (rounded to ~1 km), city fallback from `.env` pushed to the TV only
- Verified on TV: apply, check 0, all 8 tickers and weather load, cached values show instantly, city name and dates shown, background animates; CPU ~9.5% of one core while showing, ~0.15% after waking; fetch thread gone after a key press; city fallback works with location revoked (test city, then restored); revert rebuilds the offline #32 app from git and matches baseline, check 1, #32 check still 0; revert and apply idempotent; AXS Home still has no INTERNET
- Not verified: reboot; gold day change (needs a sample from the previous day); offline display with the network unplugged
- TV state left: applied

## [2026-11-22] — feat(systemui): AXS Clock screensaver with large clock and date (#32)
- Issue #32
- Files: apps/clock/ (AndroidManifest.xml, build.sh, config/clock.conf, res/drawable/icon.xml, src/.../ClockDream.java), changes/32-clock-screensaver/ (apply, revert, check, baseline, README), README.md
- New 13 KB app io.github.yohannaftali.axsclock: a DreamService with no requested permissions; time 150 sp and date on black, moves once a minute
- Verified on TV: apply, check 0, dream via Somnambulator shows clock and date, moves after a minute, CPU ~0.3% of one core, a key wakes back to AXS Home, Settings → Screen saver lists AXS Clock, revert matches baseline (Colors, app removed), check 1, revert and apply idempotent; AXS Home still has no INTERNET
- Not verified: reboot (both the setting and the app are persistent by design); real 15-minute idle start (used Start now / Somnambulator)
- TV state left: applied

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
