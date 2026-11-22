# mitv-axs02

Reversible user-interface customisation for the **Xiaomi Mi TV MiTV-AXSO2**
(Android TV 11), done entirely over ADB from a PC — no root, no flashing.
Launcher, system settings, animations, display density, hidden or disabled
preinstalled apps: every change ships with an `apply`, a `revert` and a
`check` script so it can always be undone.

## Target device

| | |
|---|---|
| Model | MiTV-AXSO2 (letter O; the repo name `mitv-axs02` uses a zero) |
| Android | 11 (API 30), Android TV |
| Security patch | 2024-02-05 |
| Kernel | 4.9.269 |
| Default launcher | Google TV launcher (`com.google.android.tvlauncher`) |

Other firmware versions may work, but package names and menus can differ.
Check yours with `sh scripts/tv.sh info` (see below).

---

## 1. Requirements (PC)

- **Android SDK Platform-Tools** (provides `adb`):
  https://developer.android.com/tools/releases/platform-tools
  - Windows: unzip it, e.g. to `C:\platform-tools`, and add that folder to
    `PATH` — or install Android Studio, which puts it in
    `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
  - macOS: `brew install android-platform-tools`
  - Linux: `sudo apt install adb` (or your distro's equivalent)
- **Git** (on Windows, Git for Windows includes Git Bash, used to run the
  scripts).
- The PC and the TV on the **same local network**.

Check it: `adb version`

---

## 2. Enable Developer options on the TV

Use the TV remote:

1. Press the **Settings** (gear) button, or go Home → ⚙ **Settings**.
2. Open **Device Preferences** → **About**.
3. Scroll to **Build** and press **OK** on it **7 times**.
   A message counts down and then shows *"You are now a developer!"*.
   If asked, enter the TV's PIN.
4. Press **Back**. **Developer options** now appears under
   **Device Preferences** (just below *About*).

## 3. Enable ADB (USB debugging)

1. **Settings** → **Device Preferences** → **Developer options**.
2. Turn on **USB debugging**. Confirm **OK** in the warning dialog.
   On this TV, USB debugging also turns on **network ADB on port 5555** —
   no USB cable is needed. (If your firmware shows a separate
   *Network debugging* / *ADB over network* switch, turn that on too.)

## 4. Find the TV's IP address

**Settings** → **Network & Internet** → select your connected Wi-Fi or
Ethernet network → note the **IP address** (e.g. `192.168.1.50`).

Tip: reserve that address for the TV in your router (DHCP reservation) so it
does not change.

## 5. Connect from the PC and choose "Always allow"

1. On the PC:
   ```sh
   adb connect <TV-IP>:5555
   ```
   The first time, adb prints `failed to authenticate` / `unauthorized`.
   That is expected.
2. Look at the **TV screen**. A dialog appears:
   **"Allow USB debugging?"** showing your PC's RSA key fingerprint.
3. Tick **"Always allow from this computer"**, then select **OK**.
   (Without the tick you must accept the dialog after every reconnect.)
4. Back on the PC:
   ```sh
   adb devices
   ```
   should list `<TV-IP>:5555    device`. If it says `unauthorized`, run
   `adb disconnect` and `adb connect <TV-IP>:5555` again and watch the TV for
   the dialog.

You are connected. Only accept this dialog for computers you trust — an
approved computer has shell access to the TV.

---

## 6. Using this project

### Option A — with the project scripts (recommended)

```sh
git clone https://github.com/yohannaftali/mitv-axs02
cd mitv-axs02
cp .env.example .env              # then edit .env
git config core.hooksPath .githooks
```

`.env` (never committed — it is in `.gitignore`):

| Key | Value |
|---|---|
| `tv_private_ip_address` | the TV's IP from step 4 |
| `netflix_esn` | optional; only used so the redactor can hide it |
| `TV_ADB_PORT` | optional, default `5555` (an environment variable of the same name overrides it) |
| `ADB` | optional full path to `adb` if it is not on `PATH` |

Then, from Git Bash (Windows) or any POSIX shell:

```sh
sh scripts/tv.sh connect                 # adb connect using .env
sh scripts/tv.sh info                    # model, Android, patch, kernel, launcher
sh scripts/tv.sh shell 'pm list packages -s'
sh scripts/tv.sh screenshot              # saved to captures/ (git-ignored)
```

`scripts/tv.sh` never prints the TV's IP and passes all output through
`scripts/redact.sh`, which masks IP addresses, MAC addresses, the serial
number, Android ID and Netflix ESN.

Applying a change (each lives in `changes/<id>-<slug>/`):

```sh
sh changes/<id>-<slug>/apply.sh     # make the change
sh changes/<id>-<slug>/check.sh     # exit 0 = change is in place
sh changes/<id>-<slug>/revert.sh    # undo it, back to baseline
```

Read the change's `README.md` first — it says what you will see and how to
recover if something goes wrong.

| Change | What it does |
|---|---|
| [`changes/1-fast-home`](changes/1-fast-home/README.md) | **AXS Home**: 46 KB home screen with clock (12/24-hour), network status, wallpapers (4 built-in or your own image/GIF/video), rearrangeable apps row, inputs bar at the bottom with connection dots (PS5, Xbox, Switch, AV), ☰ menu and animated waves; layout in [`apps/home/config/home.conf`](apps/home/config/home.conf) |
| [`changes/2-debloat`](changes/2-debloat/README.md) | Disables 14 ad, tracking and unused preinstalled apps (needs #1); screensaver to built-in Colors |
| [`changes/3-animations`](changes/3-animations/README.md) | System animations at 0.5x |
| [`changes/15-tv-bro`](changes/15-tv-bro/README.md) | TV Bro browser, installed from its verified GitHub release |

Building AXS Home needs the Android SDK (build-tools and a platform) and a JDK;
`sh apps/home/build.sh` uses them directly, with no Gradle.

### Option B — manually with plain adb

Everything the scripts do can be done by hand. Replace `<TV-IP>`:

```sh
adb connect <TV-IP>:5555
adb -s <TV-IP>:5555 shell getprop ro.product.model
adb -s <TV-IP>:5555 shell getprop ro.build.version.release
adb -s <TV-IP>:5555 shell getprop ro.build.version.security_patch
adb -s <TV-IP>:5555 shell uname -r
```

Common reversible UI tweaks:

```sh
# Faster animations (revert: set back to 1)
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5

# Hide a preinstalled app for the current user (revert: pm enable <package>)
adb shell pm disable-user --user 0 <package>
adb shell pm enable <package>

# List disabled packages
adb shell pm list packages -d

# Change UI density (revert: wm density reset)
adb shell wm density 280
adb shell wm density reset

# See which launcher is the home screen
adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN

# Screenshot
adb exec-out screencap -p > screen.png

# Remote-control buttons
adb shell input keyevent KEYCODE_HOME
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_BACK
```

(With only one device connected, `-s <TV-IP>:5555` can be left out.)

Before changing anything, write down the current value
(`settings get …`, `pm list packages -d`, `wm density`) so you can put it back.

**Do not** disable `com.android.systemui`, the settings app, the package
installer, the network stack, or your only launcher — the TV can become
unusable until a factory reset. Never factory-reset, flash or root the TV
as part of this project.

---

## 7. Turning ADB off again

- Temporarily: **Settings** → **Device Preferences** → **Developer options**
  → turn off **USB debugging**.
- Forget approved computers: **Developer options** →
  **Revoke USB debugging authorizations**.
- Hide Developer options entirely: turn off the switch at the top of
  **Developer options**.

---

## 8. Troubleshooting

| Symptom | Fix |
|---|---|
| `cannot connect … actively refused it (10061)` | USB debugging is off, or the TV just rebooted. Turn it on (step 3) and retry. |
| `failed to authenticate` / `unauthorized` | Accept the dialog on the TV (step 5); tick *Always allow*. |
| No dialog appears on the TV | `adb kill-server`, then `adb connect <TV-IP>:5555` again with the TV on the Home screen. |
| `no route to host` / timeout | Wrong IP or the TV is on another network/VLAN; recheck step 4. |
| `adb: command not found` | Install Platform-Tools (step 1) or set `ADB=` in `.env`. |
| `device offline` | `adb disconnect`, then connect again; if it persists, toggle USB debugging. |
| A `check.sh` prints `cannot check` and exits 2 | The TV is off, asleep or ADB is not authorised; nothing was changed or verified. Run `sh scripts/tv.sh connect` and retry. |

---

## Contributing / development workflow

This repo uses four Claude Code skills in `.claude/skills/`:

- **planner** files GitHub issues (each states how to apply and how to revert).
- **coder** implements an issue as `changes/<id>-<slug>/`.
- **tester** applies, checks, screenshots, reverts on the TV, then merges.
- **reviewer** audits the merged change.

Conventions and guardrails are in [`AGENTS.md`](AGENTS.md); history in
[`CHANGE_HISTORY.md`](CHANGE_HISTORY.md).

## Privacy

This repository is **public**. Never commit `.env`, IP addresses, the Netflix
ESN, serial numbers, MAC addresses, account names or screenshots. The
pre-commit hook (`git config core.hooksPath .githooks`) blocks the common
cases, and `scripts/redact.sh` cleans any output before you paste it into an
issue.
