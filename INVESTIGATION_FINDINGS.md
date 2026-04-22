# Investigation: Broken Tasklist E2E Test

## Test Information
**Failing Test:** `[tasklist-e2e] › tests/tasklist/user-task-permission-management.spec.ts:171:3 › Assignee cannot see their task without READ permission › should not display the assigned task without READ permission`

**Test File:** `qa/c8-orchestration-cluster-e2e-test-suite/tests/tasklist/user-task-permission-management.spec.ts`

## Root Cause Analysis

### Summary
The test failure is caused by a **mismatch between the test's expectations and the actual authorization implementation** in the recent refactoring (commit bdcadc4a). The test expects that a user assigned to a task CANNOT see it without explicit READ permission, but the service layer now allows assignees to see their tasks via property-based authorization.

### The Broken Test Scenario (Lines 126-189)
1. Creates user "Alice" WITHOUT READ permission on USER_TASK
2. Assigns a task to Alice
3. Alice logs in and filters by "Assigned to me"
4. **Expected:** Alice should NOT see the task (test expects "No tasks found")
5. **Actual:** Alice CAN see the task (service layer allows it)

### Technical Details

#### 1. Recent Changes (Commit bdcadc4a - April 22, 2026)
**PR:** "Allow for roles to be edited in the UI (#51296)"

This massive commit introduced:
- New V1 API controllers (`tasklist/webapp/.../v1/controllers/TaskController.java`)
- New V2 API controllers (`zeebe/gateway-rest/.../UserTaskController.java`)
- New permission service (`TasklistPermissionServices.java`)
- Restructured authorization architecture

#### 2. Authorization Models

**UserTaskServices (Service Layer)** - Lines 64-70:
```java
private static final AuthorizationCondition USER_TASK_AUTHORIZATIONS =
    AuthorizationConditions.anyOf(
        PROCESS_DEFINITION_READ_USER_TASK_AUTHORIZATION.withResourceIdSupplier(...),
        USER_TASK_READ_AUTHORIZATION.withResourceIdSupplier(...),
        USER_TASK_READ_BY_PROPERTIES_AUTHORIZATION);
```

A user can see a task if they have **ANY** of:
- **Option A:** READ_USER_TASK permission on the process definition
- **Option B:** READ permission on the specific user task key
- **Option C:** Are the assignee, candidate user, OR in a candidate group

**V2 API (Gateway REST)** - No controller-level check:
- Directly calls service layer at line 179
- Relies entirely on service-layer authorization

**V1 API (Tasklist Web App)** - Lines 119-122:
```java
if (!permissionServices.hasWildcardPermissionToReadUserTask()) {
  return ResponseEntity.ok(Collections.emptyList());
}
```
- Has an early gate requiring wildcard READ permission
- This is MORE restrictive than the service layer

#### 3. The Authorization Mismatch

| Scenario | Service Layer Result | V1 API Result | Test Expectation |
|----------|---------------------|---------------|------------------|
| User assigned to task, no READ permission | ✅ Returns task (Option C) | ❌ Empty list (gate blocks) | ❌ Empty list |
| User with READ permission | ✅ Returns task (Option A) | ✅ Returns task | ✅ Returns task |

**The Problem:**
- The E2E test uses the V2 API (via `/v2/user-tasks/search`)
- The V2 API allows assignees to see tasks (via service layer Option C)
- The test expects the old behavior (no assignee-based visibility without READ)

## Why This is Happening

The test was written based on the **old assumption** that READ permission was required to see ANY task, even if assigned to you. The new service layer implements a more **permissive model** where being assigned to a task grants implicit read access through property-based authorization (`USER_TASK_READ_BY_PROPERTIES_AUTHORIZATION`).

## Is This a Bug or Expected Behavior?

This appears to be **expected behavior** in the new authorization model. The property-based authorization (`USER_TASK_READ_BY_PROPERTIES_AUTHORIZATION`) explicitly allows:
- Assignees to see their assigned tasks
- Candidate users to see tasks they're candidates for
- Candidate groups to see tasks for their groups

This makes sense from a UX perspective - if you're assigned a task, you should be able to see it, even without explicit READ permission.

## Possible Solutions

### Option 1: Update the Test (Recommended)
**Change the test expectation** to match the new authorization model:
- Test should verify that assignee CAN see their task
- Add a NEW test for users who are neither assigned nor have READ permission

**Pros:**
- Aligns test with actual system behavior
- Documents the new permission model
- No code changes needed

**Cons:**
- Changes test semantics

### Option 2: Remove Property-Based Authorization from Search
**Modify service layer** to only use Options A and B, not Option C:
- Remove `USER_TASK_READ_BY_PROPERTIES_AUTHORIZATION` from search
- Keep it only for individual task retrieval

**Pros:**
- Test passes as-is
- More restrictive security model

**Cons:**
- Breaking change - assignees can't see their tasks without READ
- Poor UX - contradicts intuitive expectations
- Likely not the intended design

### Option 3: Add Configuration Flag
**Make property-based auth configurable**:
- Add a feature flag to enable/disable Option C
- Allow different deployments to choose their model

**Pros:**
- Flexible for different security requirements
- Backward compatible

**Cons:**
- Adds complexity
- May not be worth the engineering effort

## Recommendation

**Solution: Update the test (Option 1)**

The new behavior is more intuitive and aligns with user expectations. The test should be updated to:

1. **Fix the broken test**: Change expectation to verify assignee CAN see their task
2. **Add complementary test**: Verify users without any connection to the task (not assigned, not candidate, no READ) cannot see it
3. **Update test documentation**: Clarify that assignment grants implicit read access

## Next Steps

1. ✅ Investigate root cause
2. ✅ Identify the authorization mismatch
3. ⬜ Verify with product team that property-based auth is intended
4. ⬜ Update the failing test
5. ⬜ Add new test cases for edge scenarios
6. ⬜ Update documentation if needed
