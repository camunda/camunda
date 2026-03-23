# engineer-stream-4 Summary

**Timestamp:** 2026-03-23T10:55:25.231Z

Both defects fixed. Here's the summary:

## Changes
- `ProcessInstanceElementTerminatedV3Applier.java` (NEW): Copies V2 logic + adds `unpinGlobalExecutionListenersConfig()` before `removeInstance()` — fixes the config leak on element termination
- `EventAppliers.java`: Registered TERMINATED V3 with `globalListenersState` dependency
- 5 golden files created in `state/appliers/golden/`: `ACTIVATING_v4`, `ACTIVATED_v2`, `COMPLETING_v2`, `COMPLETED_v3`, `TERMINATED_v3`

## Verification
- EventAppliersTest: ✅ (257 tests, 0 failures)
- BpmnExecutionListenerBehaviorTest: ✅ (16 tests, 0 failures)
- Formatting: ✅ (spotless applied)

## Notes
**Defect 1** was caused by golden files being copied before `spotless:apply` reordered imports. Fixed by re-copying after formatting. **Defect 2** was a real resource leak — terminated elements never unpinned their config, so pinned versions accumulated forever. The new V3 applier calls `unpinGlobalExecutionListenersConfig()` *before* `removeInstance(key)`, matching the CompletedV3 pattern exactly.
