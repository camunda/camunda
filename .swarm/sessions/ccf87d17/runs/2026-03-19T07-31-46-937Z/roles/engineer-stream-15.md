# engineer-stream-15 Summary

**Timestamp:** 2026-03-19T10:56:44.494Z

Done. Two fixes applied:

1. **Shutdown order** — `brokerRequestDispatcher.stop()` now runs before `partitionBootstrap.stop()`, closing the Netty handlers before RAFT teardown begins
2. **Missing test** — added `shouldReturnInvalidRequestOnInvalidPosition` to the `Poll` nested class, verifying `InvalidPositionException` maps to `INVALID_REQUEST` with the expected message prefix
