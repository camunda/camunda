# engineer-stream-21 Summary

**Timestamp:** 2026-03-19T09:18:11.169Z

All changes are clean (the module is untracked, not modified from a prior commit). All 49 tests pass. Here's a summary of every fix:

---

## Changes

- `Consumer.java` — **Bug 1**: replaced immediate `nextPositions.put()` per partition with a `pendingPositions` local map that is committed to `nextPositions` only after the full poll loop completes without throwing. On `REBALANCE_IN_PROGRESS`, the pending map is abandoned — ensuring the discarded events are re-delivered on the next poll.
- `Consumer.java` — **Bug 2**: `sendHeartbeat()` now parses the `generation` field from the 200 response body and updates `this.generation`. A changed generation signals a rebalance; subsequent polls and commits will carry the updated value.
- `Consumer.java` — **Bug 3**: `commitOffset()` now appends `&generation=<current>` to the request URL so the broker can reject stale commits with `409 Conflict`.
- `ConsumerTest.java` — added 4 new regression tests: `shouldNotAdvanceNextPositionForPartitionsPolleddBeforeRebalance`, `shouldSendCurrentGenerationInCommitRequest`, `shouldUpdateGenerationFromHeartbeatResponse`, `shouldKeepGenerationUnchangedWhenHeartbeatResponseOmitsIt`.

## Verification
- Tests: ✅ 49/49 pass (was 45)
- Formatting: ✅ Spotless applied cleanly
