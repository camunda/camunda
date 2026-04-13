---
name: c8-e2e-authoring
description: Guides writing and fixing Playwright E2E tests for the Camunda 8 orchestration cluster. Use when creating new test specs, adding page objects, or fixing broken tests following the Page Object Model pattern.
---

# C8 E2E Test Authoring Skill

Use this skill when writing new test specs, page objects, or fixing existing tests.

## Mandatory Conventions

### File header

Every new `.ts` file must start with the Camunda license header. Copy from any existing file:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. ...
 */
```

### Page Object Model

- **Selectors belong in page objects**, never in test specs.
- Page objects live in `pages/<PageName>.ts` (V2) or `pages/v1/<PageName>.ts` (V1).
- Each page object exposes high-level action methods: `fillForm()`, `submitTask()`, not raw locators.

### Test structure

```ts
import {test, expect} from '@fixtures';

test.describe('Feature name', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await loginPage.login('demo', 'demo');
    await page.goto('/tasklist');
  });

  test('should do something meaningful', async ({taskListPage}) => {
    // given
    await taskListPage.openTask('My Task');

    // when
    await taskListPage.submitTask();

    // then
    await expect(taskListPage.successBanner).toBeVisible();
  });
});
```

### Waiting rules

- **Never** use `sleep()` or `waitForTimeout()`.
- Use Playwright's built-in retry: `await expect(locator).toBeVisible()`.
- For process instances: use helpers from `utils/zeebeClient.ts`.

### Imports

- Use path aliases: `@pages/PageName`, `@fixtures`, `@setup`, `@requestHelpers`
- Import `{test}` from `@fixtures`, not directly from `@playwright/test`

### Setup hooks

- `test.beforeEach` — navigation and per-test clean state
- `test.beforeAll` — heavy one-time setup (process/decision deployment)

### Process and decision helpers (`utils/zeebeClient.ts`)

| Helper | Use case |
|---|---|
| `deploy(bpmnPath)` | Deploy a BPMN or DMN file |
| `deployWithSubstitutions(bpmnPath, subs)` | Deploy after replacing string placeholders for unique IDs |
| `createInstances(processId, count, vars)` | Create multiple process instances |
| `createSingleInstance(processId, vars)` | Create a single process instance |
| `cancelProcessInstance(instanceKey)` | Cancel an instance during cleanup |
| `createWorker(jobType, handler)` | Register a job worker for async job completion |

## Test Mode Separation

- **V2 (default):** tests in `tests/`, page objects in `pages/`
- **V1 (legacy):** tests in `tests/tasklist/v1/`, page objects in `pages/v1/`
- Never mix V1 and V2 in the same test file.

## Authoring Checklist

Before committing a new or modified test:

- [ ] License header present in every new file
- [ ] All selectors are in page objects, not in test files
- [ ] No `sleep()` or `waitForTimeout()` calls
- [ ] `test.beforeEach` handles navigation and cleanup
- [ ] Process/decision deployments use `deployWithSubstitutions` for unique IDs
- [ ] `npm run lint` passes in `qa/c8-orchestration-cluster-e2e-test-suite/`
- [ ] Test runs locally against the Docker Compose environment
- [ ] PR description links the corresponding TestRail test case (if modifying existing tests)
