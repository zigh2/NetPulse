# Changelog

All notable changes are listed here. The newest version is on top.
Release notes on GitHub are taken from the section matching the version.

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
