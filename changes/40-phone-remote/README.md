# 40 — Phone remote (AXS Remote)

Open **`http://<TV IP>:8077`** in a phone's browser (iPhone or Android):
it is a remote served by the TV itself — nothing to install on the phone.
☰ → **Phone remote** shows that address and a QR code for it, so you never
have to look the IP up. The address stays the same, so bookmark it or add
it to the phone's home screen (#46).

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

The page is served over **plain HTTP on port 8077** (ports below 1024 need
root) for as long as AXS Remote is the TV's keyboard: Android keeps the
keyboard running, and the server starts with it — no QR screen to open
first, no pairing, no time limit. Until #46 it was HTTPS with a certificate
made on the TV and a token in the QR code; iPhones opened the page but
Safari would not let its WebSocket use that self-signed certificate, so it
never connected.

## Safety

- **Owner's choice (#46): no pairing.** Any device on the same network can
  open the remote while AXS Remote is the keyboard, and typed text crosses
  the home network unencrypted. Don't type passwords from the phone on a
  network you don't trust; `revert.sh` turns it all off.
- A WebSocket opened by some other web page (its `Origin` is not the TV's
  address) is refused, so a site visited on the phone can't drive the TV.
- Only a fixed list of remote commands exists: no shell, no files, no app
  installs. The page has no external resources.
- The TV's IP is read at runtime and appears only on the TV screen and in
  the QR code; it is never written to logs or to this repository. AXS Home
  itself still has no network access — only AXS Remote does.

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

Also checked: volume up/down (and restored), HOME from inside apps, page
served.

#46 (2026-11-23): page served over HTTP with `ws://`; two clients at once
both connected; a WebSocket with a foreign `Origin` refused (403); keys sent
without any token; the server is back as soon as AXS Remote is the keyboard
again (switching keyboards away and back). Not checked here: a real iPhone
(owner), a reboot.

## Use

```sh
sh changes/40-phone-remote/apply.sh     # build, install, set as keyboard, add the ☰ item
sh changes/40-phone-remote/check.sh     # 0 = installed, the keyboard, and port 8077 answers
sh changes/40-phone-remote/revert.sh    # Gboard back, AXS Remote removed (☰ item disappears)
```

## Baseline and revert

`baseline.txt` records `default_input_method` and `enabled_input_methods`
(Gboard only) and that AXS Remote was not installed. `revert.sh` sets Gboard
back, uninstalls AXS Remote, restores
both settings and compares them with the baseline (exit 1 on a difference,
2 on a failed TV step). `VERIFY_ONLY=1` only compares.

## Lock-out risk

Low. If AXS Remote misbehaved, typing on the TV could be affected; the
physical remote still navigates, Settings → Device Preferences → Keyboard
can switch back to Gboard, and `revert.sh` (or
`sh scripts/tv.sh shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`)
restores it. ADB, the home screen and the network are not touched.
