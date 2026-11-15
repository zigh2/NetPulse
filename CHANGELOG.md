# Changelog

All notable changes are listed here. The newest version is on top.
Release notes on GitHub are taken from the section matching the version.

## [0.04]

### Added
- **Alerts:** separate high-priority notification when the internet comes back, and when an outage lasts longer than N minutes.
- **Home-screen widget** (coloured status + live timer) and **Quick Settings tile** (status, start / stop).
- **Outage report (PDF):** outages only, with dates, durations, network and notes, plus uptime summary: made for talking to your provider.
- **Uptime windows:** session, last 24 h, 7 days, 30 days; ping-over-time chart (avg + max, lost checks marked).
- **Network type per period:** Wi-Fi / mobile / Ethernet, and cause: no network vs connected but no internet.
- **Quality metrics:** packet loss, jitter, min / max ping (per-minute history kept for N days, default 90).
- **Better checks:** per-server status table, optional HTTP check (detects captive portals) and DNS check.
- **Schedule:** monitor only inside a daily time window (session closes outside it).
- **Backup / restore** of all data; **notes** on any period (tap a card); exports include network, cause and note.
- **Dynamic colours** (Material You) and in-app **language** switch (System / English / العربية).

### Changed
- Database upgraded to v3 (existing data is kept).

## [0.03]

### Added
- Average ping: per period, per session, on the dashboard, and in CSV / XLSX / PDF exports.
- Dashboard cards for average ping and longest outage.

### Changed
- History: session number shown once as a header above its cards (with start time and average ping).
- History: the date is not repeated when a period starts and ends on the same day.
- History: delete moved to long-press on a card (with haptic feedback); the delete button is gone.
- History: the running period shows "Now" instead of "…".
- Releases: every zip dropped in `incoming/` gets a new version (auto-bumped if the tag exists) and the APK is attached directly, not zipped.
- Database upgraded to v2 (adds ping columns; existing data is kept).

## [0.02]

### Fixed
- CI: `setup-android` no longer requests the removed `tools` SDK package (build failed at "Set up Android SDK").

## [0.01]

### Added
- Background monitor (foreground service) that pings 8.8.8.8 / 1.1.1.1 / 9.9.9.9 every N seconds.
- Records test start, every online / offline period, and test stop.
- Persistent notification: green when online, red when offline, with a live timer and a Stop action.
- Dashboard with live status, uptime, outages and a session timeline.
- History with session / status filters; delete a period (neighbours merge) or remove all short periods.
- Export to CSV, XLSX and PDF.
- Light / dark / system theme; Arabic and English UI (RTL ready).
- Auto-resume after reboot, optional wake lock, battery-optimisation shortcut.
- GitHub workflow: drop a zip in `incoming/` to replace the project, build the APK and publish a release.
