# engineer-stream-3 Summary

**Timestamp:** 2026-03-23T10:34:13.246Z

Done. Here's the summary:

## Changes
- `zeebe/broker/.../GlobalListenerCfg.java`: Added overloaded `createGlobalListenerConfiguration(GlobalListenerType)` to force-set listener type
- `zeebe/broker/.../GlobalListenersCfg.java`: Pass explicit `USER_TASK`/`EXECUTION` types when converting config lists
- `dist/src/main/config/defaults.yaml`: Added `execution: null` under `global-listeners`
- `configuration/.../ClusterGlobalListenersTest.java`: Added 10 tests (8 execution-only + 2 combined)

## Verification
- Build: ✅ (broker + deps built successfully)
- Tests: ✅ (19/19 passed — 10 new + 9 existing)
- Lint: ✅ (spotless:apply ran clean)

## Notes
The critical fix was that `GlobalListenerCfg.listenerType` defaults to `USER_TASK`, so execution listeners bound from `camunda.cluster.global-listeners.execution[*]` would be silently mistyped. The fix adds an overloaded factory method accepting an explicit `GlobalListenerType`, and the container class passes the correct type based on which list each config belongs to.
