# 40 — Phone remote (AXS Remote)

☰ → **Phone remote** shows a QR code. Scan it with an iPhone (Camera →
Safari) or an Android phone: the browser opens a remote served by the TV
itself — nothing to install on the phone.

- **D-pad, OK, Back, Home, Menu**, volume −/mute/+, rewind / play-pause /
  fast-forward. Holding an arrow repeats it.
- **Typing:** when a text box is focused on the TV (YouTube search, TV Bro,
  web forms, Settings), a text bar appears on the phone with the field's
  hint; tap it and type with the phone keyboard — every change is sent to
  the TV, Enter submits. (Phones only open their keyboard after a tap.)
- **Aerial TV** panel (tap *Aerial TV ▾*): CH +/−, Guide, Info and a 0–9
  number pad, for Live TV with an antenna.
- On the TV, AXS Remote is also a small on-screen keyboard for the physical
  remote (arrows + OK), because Gboard is not the active keyboard while this
  change is applied.

## How it works

Android 11 does not let an app send keys to other apps. The active
**keyboard (input method)** can, so AXS Remote (`io.github.yohannaftali.axsremote`,
37 KB, plain Java, no libraries) is installed as the TV's keyboard. Arrow
keys are sent as D-pad events (Android TV's own lists ignore keyboard-style
events), text goes straight into the focused field, volume uses the audio
service, media keys go to the playing app, and HOME starts the home screen
(allowed by the `SYSTEM_ALERT_WINDOW` app-op).

The page is served over **HTTPS on port 8077** (ports below 1024 need root).
The TV makes its **own self-signed certificate** the first time (EC P-256,
the TV's IP as subjectAltName, serverAuth, 397 days — iOS rules for server
certificates), kept only in the app's private storage. Your phone warns
once that the connection is not private; compare the fingerprint shown on
the TV, then *Show details → visit this website* (iPhone) or *Advanced →
Proceed* (Android).

## Safety

- The QR code carries a random token; a new one is made every time the QR
  screen opens and older ones are refused (HTTP 403).
- Only a fixed list of remote commands exists: no shell, no files, no app
  installs. The page has no external resources.
- The server starts only when the QR screen is opened and stops after
  10 minutes without a command or a connected phone.
- The TV's IP is read at runtime and appears only in the QR code on screen;
  it is never written to logs or to this repository. AXS Home itself still
  has no network access — only AXS Remote does.

## Tested on this TV (2026-11-22)

Driven from a PC acting as the phone (same protocol as the page):

| App | Arrows / OK / Back | Typing | Notes |
|---|---|---|---|
| AXS Home | ✅ | – | menu, tiles, inputs |
| YouTube | ✅ | not tested | account picker reacts to arrows |
| TV Bro | ✅ | ✅ address bar and web page fields | Enter loads the page |
| TV Settings | ✅ (after sending D-pad-type events) | ✅ on-screen keyboard with the remote | |
| Live TV | opens | – | no antenna here: CH keys not tested |
| Netflix | opens | – | Netflix blocks screenshots and exposes no UI tree: **please check with your phone** |

Also checked: volume up/down (and restored), HOME from inside apps, old QR
code rejected, page served, wrong token 403. Not checked: a real iPhone /
Android phone accepting the certificate, the 10-minute auto-stop, a reboot.

## Use

```sh
sh changes/40-phone-remote/apply.sh     # build, install, set as keyboard, add the ☰ item
sh changes/40-phone-remote/check.sh     # 0 = AXS Remote installed and the keyboard
sh changes/40-phone-remote/revert.sh    # Gboard back, AXS Remote removed (☰ item disappears)
```

## Baseline and revert

`baseline.txt` records `default_input_method` and `enabled_input_methods`
(Gboard only) and that AXS Remote was not installed. `revert.sh` sets Gboard
back, uninstalls AXS Remote (its certificate and key go with it), restores
both settings and compares them with the baseline (exit 1 on a difference,
2 on a failed TV step). `VERIFY_ONLY=1` only compares.

## Lock-out risk

Low. If AXS Remote misbehaved, typing on the TV could be affected; the
physical remote still navigates, Settings → Device Preferences → Keyboard
can switch back to Gboard, and `revert.sh` (or
`sh scripts/tv.sh shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`)
restores it. ADB, the home screen and the network are not touched.
