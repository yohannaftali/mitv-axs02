# Change History

Newest first. Never include the TV IP, Netflix ESN, serial, MACs or account
names — see `AGENTS.md` → Private data.

## [2026-11-22] — chore(skills): adopt planner, coder, tester, reviewer from telepad
- Skills copied from the telepad project and adapted to this repo (GitHub,
  ADB-driven UI changes on MiTV-AXS02, Android 11, patch 2024-02-05, kernel 4.9.262)
- Added `scripts/tv.sh`, `scripts/redact.sh`, `.githooks/pre-commit`, `.env.example`
- Verified: redactor on synthetic input; TV reachable on the LAN
- Verified: ADB session authorised; `scripts/tv.sh info` read device facts into AGENTS.md
- Fixed: `tv.sh` redact helper ran `adb shell` without `</dev/null`, which swallowed piped output
