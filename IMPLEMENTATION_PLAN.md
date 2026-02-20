# Implementation Plan: Fix User Task Action Correction in Listener

## Problem Summary
The `action` field cannot be corrected in user task listeners. When task listeners complete with corrections, the `CORRECTED` event is written but the `action` field is not being preserved from the intermediate state.

## Root Cause Analysis
In `UserTaskProcessor.java:114-143`, the `processCompleteTaskListener` method:
1. Retrieves the intermediate user task record from state (line 120)
2. Wraps changed attributes if values changed (line 126)
3. Writes the CORRECTED event (line 130)

**Issue**: The `action` field from the intermediate state is not being explicitly preserved when writing the CORRECTED event. The intermediate state should contain the action from the original command, but it needs to be properly transferred to the corrected record.

## Expected Behavior
According to `TaskListenerCorrectionsTest.java:208`:
- The CORRECTED event should have the same action as the original user task command
- Examples: "assign", "complete", "update", "claim", etc.
- Empty string for system-triggered updates (e.g., variable updates)

## Solution Approach

### Step 1: Understand Current Flow
1. Read `UserTaskProcessor.java` and understand how intermediate state is managed
2. Check how the action field is set in the intermediate state
3. Verify which fields are currently being preserved in CORRECTED events

### Step 2: Identify the Fix Location
The fix should be in `UserTaskProcessor.processCompleteTaskListener()` method around line 126-130:
```java
if (intermediateUserTaskRecord.hasChangedAttributes()) {
  stateWriter.appendFollowUpEvent(
      command.getKey(), UserTaskIntent.CORRECTED, intermediateUserTaskRecord);
}
```

The `intermediateUserTaskRecord` already contains the action field from the intermediate state. We need to ensure it's properly preserved.

### Step 3: Investigate UserTaskRecord
1. Check if `UserTaskRecord.action` is part of `wrapChangedAttributesIfValueChanged`
2. Verify if action is included in the attributes that can be corrected
3. Determine if action needs special handling or if it should be automatically preserved

### Step 4: Implement the Fix
Based on investigation, likely need to:
1. Ensure the action field is preserved when creating CORRECTED events
2. The action should come from the intermediate state, not from corrections
3. Add action to the list of correctable attributes if needed (though action should be preserved, not corrected)

### Step 5: Verify with Tests
1. Run `TaskListenerCorrectionsTest` to verify the fix
2. Ensure all test cases pass, especially around line 208 assertions
3. Check for any other tests that might be affected

### Step 6: Format and Finalize
1. Run `./mvnw spotless:apply license:format -T1C` to format code
2. Commit the changes
3. Run full test suite to ensure no regressions

## Files to Modify
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/usertask/UserTaskProcessor.java`

## Files to Review
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/bpmn/activity/listeners/task/TaskListenerCorrectionsTest.java`
- Protocol definition files for UserTaskRecord

## Testing Strategy
1. Run targeted test: `TaskListenerCorrectionsTest`
2. Run all user task processor tests
3. Run integration tests for user task processing

## Notes
- The action field should be preserved from the original command, not corrected by listeners
- Empty string is valid for system-triggered actions
- Do not add action to the list of correctable attributes - it should be preserved automatically
