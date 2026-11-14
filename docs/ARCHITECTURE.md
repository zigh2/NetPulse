# Architecture

```
MonitorService (foreground, specialUse)
 ├─ Pinger          ICMP ping → TCP 53/443 fallback, all servers in parallel
 ├─ Store (SQLite)  sessions + periods (online/offline spans)
 ├─ MonitorState    in-process live status (StateFlow) for the UI
 └─ Notification    colour-coded (green / red), chronometer, Stop action

UI (Jetpack Compose, Material 3)
 ├─ Dashboard   live status, stats, timeline
 ├─ History     filters, delete / clean, export
 └─ Settings    interval, timeout, servers, theme, reliability
```

**State machine.** A period starts when the state *changes*. Going offline needs N consecutive failed
checks (default 2) but is back-dated to the first failed check; going online is immediate.

**Deleting a period** lets the previous period absorb the gap; if that makes two periods of the same
state adjacent, they are merged. The running period cannot be deleted.

**Restarts.** If the process or device was down, the old session is closed at the last heartbeat and a new
session starts, so gaps are never counted as online or offline time.
