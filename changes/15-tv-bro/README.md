# #15 — TV Bro browser

Installs [TV Bro](https://github.com/truefedex/tv-bro), an open-source
browser made for TVs: a cursor you move with the remote, a desktop-site mode,
tabs, bookmarks and downloads. It appears as **Browser** on AXS Home
(`apps/home/config/home.conf`).

The build is `tvbro-3.0.7-generic-geckoExcluded.apk` (3.8 MB). It uses the
TV's own Android System WebView (Chromium 139 on this TV), so pages render
with the same engine as Chrome, without the 156 MB Firefox-engine build.

## Apply / check / revert

```sh
sh changes/15-tv-bro/apply.sh                     # download, verify, install
sh changes/15-tv-bro/check.sh                     # 0 installed, 1 not, 2 TV unreachable
sh changes/15-tv-bro/revert.sh                    # back to baseline.txt (uninstall)
VERIFY_ONLY=1 sh changes/15-tv-bro/revert.sh      # read-only comparison
```

`apply.sh` downloads the exact release asset named in
[`pinned.txt`](pinned.txt) with `gh` into `.cache/` (git-ignored), and installs
it only if:

- its SHA-256 equals the pinned value (the same digest GitHub publishes for
  that asset), and
- `apksigner` verifies it and it has exactly one signer, whose certificate
  SHA-256 equals the pinned one (the developer key, identical on 3.0.6 and
  3.0.7).

To move to a newer release, update `tag`, `asset` and `sha256` in
`pinned.txt`. If the certificate ever changes, find out why before changing
`cert_sha256`.

## Permissions

`adb install` grants none of the app's runtime permissions. Android asks on
the TV if a website wants the microphone, camera or location. TV Bro's
built-in updater needs "Install unknown apps", which stays off unless you
enable it in Settings.

## Manual

```sh
gh release download v3.0.7 -R truefedex/tv-bro -p 'tvbro-3.0.7-generic-geckoExcluded.apk'
sha256sum tvbro-3.0.7-generic-geckoExcluded.apk      # compare with pinned.txt
apksigner verify --print-certs tvbro-3.0.7-generic-geckoExcluded.apk
adb install -r tvbro-3.0.7-generic-geckoExcluded.apk
adb uninstall com.phlox.tvwebbrowser                  # undo
```

Lock-out risk: none (a normal user app).
