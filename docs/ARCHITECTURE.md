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

**Data.** `periods` hold online/offline spans (ping totals, network type, cause, note). `buckets` hold per-minute
check statistics (checks, fails, ping sum/min/max, jitter) used for loss, jitter and the chart; they are pruned
after the retention period. Backup copies the SQLite file; restore validates it first.

**State machine.** A period starts when the state *changes*. Going offline needs N consecutive failed
checks (default 2) but is back-dated to the first failed check; going online is immediate.

**Deleting a period** lets the previous period absorb the gap; if that makes two periods of the same
state adjacent, they are merged. The running period cannot be deleted.

**Schedule.** Outside the daily window the session is closed and nothing is recorded; a new session starts when the
window opens.

**Restarts.** If the process or device was down, the old session is closed at the last heartbeat and a new
session starts, so gaps are never counted as online or offline time.
