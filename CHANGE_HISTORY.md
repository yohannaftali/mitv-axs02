# Change History

Newest first. Never include the TV IP, Netflix ESN, serial, MACs or account
names — see `AGENTS.md` → Private data.

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
