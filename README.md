<div align="center">

# NetPulse · نبض الشبكة

**A tiny Android app that never sleeps: it pings reliable servers every few seconds and records exactly when your internet went online, offline, and when the test started and stopped.**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

</div>

---

## ✨ Features

| | |
|---|---|
| 🛰️ **Background monitor** | Foreground service pings `8.8.8.8`, `1.1.1.1`, `9.9.9.9` (editable) every N seconds. |
| 🟢🔴 **Persistent notification** | Coloured green when online, red when offline, with a live timer and a Stop button. |
| 🧾 **Precise log** | Test start, every online / offline period, test stop, average ping per period. |
| ✂️ **Edit the data** | Long-press a card to delete a short blip (neighbours merge) or remove all periods shorter than X seconds. |
| 📤 **Export** | CSV, XLSX (2 sheets) and PDF report, shared via the Android share sheet. |
| 🌗 **Dark mode** | System / light / dark. Arabic + English UI, RTL ready. |
| 🔋 **Built not to die** | Foreground service, `START_STICKY`, wake lock, resume after reboot, battery-optimisation shortcut. |
| 🪶 **Light** | No third-party libraries besides AndroidX / Compose. Hand-written XLSX writer, native PDF. |

## 📦 Install

Download the latest `NetPulse-vX.Y.apk` from the [Releases](../../releases) page and install it.

> **Tip:** open *Settings → Battery optimisation* inside the app and allow unrestricted background use.
> On some phones (Xiaomi, Huawei, Oppo, Samsung…) also enable *Autostart* / *Lock in recents* for NetPulse.

## 🚀 Release workflow (the `incoming/` folder)

1. Put the project zip in `incoming/` (e.g. `NetPulse-v0.02.zip`) and push to `main`.
2. GitHub Actions replaces the whole project with the zip contents, builds the APK, commits the result,
   deletes the zip and **publishes a new Release** with the plain `NetPulse-vX.Y.apk` (no zip) and SHA-256 sums.

The version comes from the `VERSION` file inside the zip (fallback: the number in the zip file name).
If that version was already released, it is **bumped automatically** (`0.02 → 0.03`) so every drop is a new release.

`.github/workflows/` is intentionally **not** replaced by the zip: GitHub does not let the built-in token
modify workflow files. Change workflows by committing them normally.

To make a zip from this repository: `scripts/pack.sh` → `dist/NetPulse-v<VERSION>.zip`.

## 🔐 Signing

The repo ships `app/netpulse.keystore` (password `netpulse`) so that every build is signed with the **same**
key and new APKs install over old ones. It is not secret. For your own key add these repository secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## 🛠️ Build locally

```bash
gradle assembleRelease          # Gradle 8.9+, JDK 17, Android SDK 35
```

## 🧠 How it decides "offline"

Each cycle probes all servers in parallel (ICMP ping, then TCP 53/443 as a fallback when ICMP is blocked).
One reply = online. Going offline needs *N* consecutive failed cycles (default 2) but is back-dated to the
first failure; going online is immediate. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## 📄 License

MIT — see [LICENSE](LICENSE).
