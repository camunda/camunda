# engineer-stream-15 Summary

**Timestamp:** 2026-03-23T12:28:01.276Z

All tests pass and formatting is clean.

## Changes
- `zeebe/engine/src/test/java/.../GlobalListenersStateTest.java`: Fixed `shouldIsolateListenersByType` — assert each result immediately before the next `get()` call, since zb-db returns mutable singletons that get overwritten

## Verification
- GlobalListenersStateTest: ✅ 13 tests (8 existing + 5 new)
- GlobalListenerValidatorTest: ✅ 17 tests (all new)
- JsonSerializableToJsonTest: ✅ 118 tests (117 existing + 1 new)

## Notes
The root cause was the zb-db mutable singleton pattern: `ColumnFamily.get()` reads into a shared value instance. Calling `getGlobalListener(EXECUTION, ...)` followed by `getGlobalListener(USER_TASK, ...)` made both references point to the same internal `GlobalListenerRecord`, which contained USER_TASK data from the last read. The fix asserts on each retrieved value before the next `get()` call.
