# engineer-stream-2 Summary

**Timestamp:** 2026-03-23T10:18:18.119Z

## Changes
- `zeebe/engine/.../BpmnStreamProcessor.java`: Replaced 4 `ExecutableFlowNode::get*ExecutionListeners` method references with lambdas delegating to `executionListenerBehavior` — merging global + BPMN-level listeners at runtime

## Verification
- Build: ✅ (compiles clean)
- Tests: ✅ (16/16 `BpmnExecutionListenerBehaviorTest` pass)
- Lint: ✅ (spotless applied)

## Notes
Surgical 4-line change. The `BpmnExecutionListenerBehavior`, `GlobalExecutionListenerMatcher`, and state infrastructure were already fully implemented — the only missing piece was wiring them into `BpmnStreamProcessor`. Global listeners now merge before/after BPMN-level listeners based on `afterNonGlobal` flag and `priority` ordering.
