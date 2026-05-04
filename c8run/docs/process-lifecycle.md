# Process Lifecycle

## PID File Locking

Every PID file operation is protected by a corresponding `.lock` file using OS-level file locks. This is not optional — bypassing the lock mechanism causes race conditions when multiple processes access the same PID file.

- **Read operations:** must acquire a shared read lock (`acquireRLock`) before reading
- **Write operations:** must acquire an exclusive lock (`acquireLock`) before writing or deleting

Do not add PID file reads or writes that skip lock acquisition.

## Process Restart State Machine

`AttemptToStartProcess` in `internal/processmanagement/processhandler.go` implements a 4-state decision:

| State | Condition | Action |
|---|---|---|
| **No PID** | PID file does not exist | Start process |
| **Running + healthy** | PID file exists, process alive, health check passes | Do nothing (skip start) |
| **Running + unhealthy** | PID file exists, process alive, health check fails | Kill process, clean up PID, restart |
| **Stale PID** | PID file exists, process is dead | Clean up PID file, start process |

The health check only runs if the process is confirmed running. An unhealthy running process triggers a kill + cleanup before restart — not a graceful shutdown. Changes to restart logic must be tested against all four states.

## Signal Handling

C8Run registers handlers for `SIGINT` and `SIGTERM` (and `os.Interrupt`). On receiving a signal, `ShutdownProcesses` is called and the main goroutine waits for the shutdown WaitGroup before exiting. `SIGKILL` cannot be caught by any process and bypasses the graceful shutdown path entirely.

Graceful shutdown flow:
1. Signal received → `ShutdownProcesses` called
2. Connectors process stopped first
3. Camunda process stopped
4. PID files and lock files cleaned up
5. WaitGroup completes → process exits

`Ctrl+C` in foreground mode triggers the same shutdown path as `./c8run stop`.

## Foreground vs. Background Start

When `c8run start` runs in the foreground, `Ctrl+C` initiates graceful shutdown via the signal handler.

The `--detached` flag is accepted by the CLI but **not yet implemented** — it is a stub. Backgrounding with `c8run start &` is a known limitation: without a lock file to coordinate between start and stop, zombie processes can result if the start command is interrupted mid-startup. Do not remove the TODO comment at this site without implementing the lock mechanism.

## Kill Implementation

Kill behavior differs by platform — see [Platform Differences](platform-differences.md) for specifics on how Unix and Windows each terminate the process tree.
