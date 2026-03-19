# engineer-stream-10 Summary

**Timestamp:** 2026-03-19T09:28:26.171Z

141 tests, all passing. Here's a summary of the three fixes applied:

**Bug 1 — Duplicate `zeebe-snapshots` in `pom.xml`**: Removed the second redundant `<dependency>` block (lines 76–80). The first explicit declaration at lines 58–62 is retained.

**Bug 2 — `lastLogPosition++` in `handleNewSnapshotError`**: Removed the synthetic increment. Since log positions are strictly monotonically increasing, the next `notifyBatchWritten` call will always supply a strictly greater position. Resetting `entriesSinceLastSnapshot = 0` alone prevents a spin-loop; the synthetic increment was unnecessary and violated the field's contract.

**Design — `setCurrentTerm` returns `ActorFuture<Void>`**: Changed the return type from `void` to `ActorFuture<Void>` so callers can `.join()` before pushing batches, guaranteeing the term update lands before the threshold is crossed. Updated the `SetCurrentTerm` test accordingly — it now calls `.join()` on `setCurrentTerm(7L)` and then asserts `term=7` was passed to `newTransientSnapshot` (instead of the previous race-acknowledgement comment).
