# engineer-stream-1 Summary

**Timestamp:** 2026-03-19T08:33:17.432Z

All green. Here's what was fixed:

**Bug fixes:**

1. **Race condition / memory leak in `PublishActor.awaitRecords`** (High) — The timer callback called `pendingAwaiters.remove(result)` where `result` is a `CompletableActorFuture`, but the list holds `LogRecordAwaiter` objects — the remove was a no-op, leaking the awaiter. Fixed by declaring `awaiter` *before* the timer is scheduled so it can be captured in the timer closure, and changing the remove call to `pendingAwaiters.remove(awaiter)`.

2. **Missing null guard in `PublishActor.getLatestPosition`** (Medium) — Called `logStreamReader.seekToEnd()` without a null check, unlike the sibling methods. Added the same guard pattern used in `publishBatch` and `awaitRecords`.

**Cosmetic/clarity fixes:**

3. **SBE field IDs renumbered** — `PublishBatchResponse`, `PollResponse`, and `SubscribeResponse` had non-sequential field IDs (e.g. id=2 declared after id=3). Declaration order was actually valid SBE (fields → groups → data), so the build never failed, but the out-of-order IDs were confusing. Renumbered to be sequential.
