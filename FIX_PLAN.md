# Fix Plan: Update E2E Test for New Authorization Model

## Overview
Update the failing E2E test to match the new property-based authorization model where assignees can see their tasks without explicit READ permission.

## Changes Required

### 1. Update Failing Test (user-task-permission-management.spec.ts)

**Test Block:** "Assignee cannot see their task without READ permission" (lines 126-189)

**Current Behavior:**
- Creates Alice without READ permission
- Assigns task to Alice
- Expects Alice to NOT see the task

**New Expected Behavior:**
- Creates Alice without READ permission
- Assigns task to Alice
- Alice SHOULD see the task (property-based authorization)

**Changes:**
```typescript
test.describe.serial('Assignee can see their task with property-based authorization', () => {
  // ... setup remains the same ...

  test('should display the assigned task via property-based authorization', async ({
    page,
    loginPage,
    taskPanelPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.filterBy('Assigned to me');

    // NOW EXPECTS THE TASK TO BE VISIBLE
    await expect(async () => {
      await expect(
        taskPanelPage.availableTasks.getByText(TASK_NAME),
      ).toBeVisible();
    }).toPass({timeout: 30000});
  });
});
```

### 2. Add New Test for Truly Unauthorized Access

Add a new test block that verifies users with NO connection to the task cannot see it:

```typescript
test.describe.serial('User without any authorization cannot see task', () => {
  test.beforeAll(async ({request}) => {
    authorizationKeys.length = 0;
    createdUsernames.length = 0;

    await deploy(['./resources/user_task_api_test_process.bpmn']);
    await sleep(500);

    const instance = await createSingleInstance(PROCESS_ID, 1);
    const processInstanceKey = instance.processInstanceKey;

    // Create Alice - she will have NO connection to the task
    aliceUser = await createUser(request);
    createdUsernames.push(aliceUser.username);

    // Only give component authorization (to access the app)
    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', aliceUser.username),
      ),
    );

    // Create Bob who will be assigned the task
    bobUser = await createUser(request);
    createdUsernames.push(bobUser.username);

    authorizationKeys.push(
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', bobUser.username),
      ),
    );

    // Assign task to BOB, not Alice
    bobUserTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');
    const assignRes = await request.post(
      buildUrl('/user-tasks/{userTaskKey}/assignment', {
        userTaskKey: bobUserTaskKey,
      }),
      {
        headers: jsonHeaders(),
        data: {assignee: bobUser.username},
      },
    );
    expect(assignRes.status()).toBe(204);

    await sleep(3000);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, createdUsernames);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should not display task when user has no connection to it', async ({
    page,
    loginPage,
    taskPanelPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login(aliceUser.username, aliceUser.password);
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.filterBy('All open tasks');

    // Alice should not see Bob's task
    await expect(async () => {
      await expect(page.getByText('No tasks found')).toBeVisible();
      await expect(
        taskPanelPage.availableTasks.getByText(TASK_NAME),
      ).toBeHidden();
    }).toPass({timeout: 30000});
  });
});
```

### 3. Keep Existing Passing Tests

The following test blocks should remain unchanged as they already pass:
- "Task visible to assignee with READ permission" (lines 48-124)
- "Task not visible to user without READ permission" (lines 191-291) - This already tests the correct scenario
- "User with UPDATE_USER_TASK permission can claim an unassigned task" (lines 293-369)
- All other test blocks

## Testing Strategy

### Before Fix:
1. Run failing test to capture current error
2. Document the exact failure message

### After Fix:
1. Run updated test to verify it passes
2. Run all tests in the file to ensure no regressions
3. Verify test logic matches authorization implementation

### Commands:
```bash
# Run specific failing test
cd qa/c8-orchestration-cluster-e2e-test-suite
npm run test -- --project=tasklist-e2e --grep "should not display the assigned task without READ permission"

# Run entire test file
npm run test -- --project=tasklist-e2e tests/tasklist/user-task-permission-management.spec.ts

# Run all tasklist E2E tests
npm run test -- --project=tasklist-e2e
```

## Documentation Updates

### Test File Comments
Add clarifying comments to the updated test:

```typescript
// Property-based authorization allows assignees to see their tasks
// even without explicit READ permission on the process definition.
// This test verifies that assignment grants implicit read access.
```

### Investigation Document
Update `INVESTIGATION_FINDINGS.md` to mark the solution as implemented.

## Acceptance Criteria

- [ ] Failing test is updated and passes
- [ ] New test for unauthorized access is added and passes
- [ ] All other tests in the file still pass
- [ ] Test names accurately describe behavior
- [ ] Code comments explain authorization model
- [ ] Investigation document is updated

## Risk Assessment

**Risk Level:** Low

**Reasoning:**
- Only test changes, no production code changes
- Updates test to match actual system behavior
- Improves test coverage by adding edge case

**Rollback Plan:**
- Revert commit if tests fail
- Re-investigate if new understanding emerges
