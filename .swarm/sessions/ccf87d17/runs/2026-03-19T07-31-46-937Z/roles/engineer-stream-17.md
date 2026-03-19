# engineer-stream-17 Summary

**Timestamp:** 2026-03-19T09:45:48.098Z

All three review issues are resolved:

1. **Bug fixed** — `unavailable(ErrorCode, String)` overload added; coordinator methods (`subscribe`, `heartbeat`, `commitOffset`, `fetchAssignment`) now return `COORDINATOR_UNAVAILABLE`. Three new tests cover each affected operation.

2. **Javadoc + debug log** — class-level Javadoc explains the empty-address retry behaviour; the `LOG.debug` call includes `(no I/O attempted)` to make it visible in logs.

3. **Retry delay** — `RETRY_DELAY_MS = 50` constant + `delayedRetry()` helper using `CompletableFuture.delayedExecutor`; all three retry branches route through it.

**28/28 tests pass**, build clean, code formatted.
