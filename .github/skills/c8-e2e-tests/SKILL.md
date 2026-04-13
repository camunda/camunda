---
name: c8-e2e-tests
description: Runs and debugs Playwright E2E tests for the Camunda 8 orchestration cluster. Use when running the full test suite, a filtered subset, or debugging test failures via traces and HTML reports.
---

# C8 E2E Tests Skill

Use this skill to execute and debug E2E tests. Always verify the environment is running
(use the `c8-e2e-environment` skill) before calling `run-tests.sh`.

## Test Suite Location

`qa/c8-orchestration-cluster-e2e-test-suite/`

## Test Structure

| Path | Contents |
|---|---|
| `tests/tasklist/` | Tasklist V2 UI tests |
| `tests/tasklist/v1/` | Tasklist V1 legacy UI tests |
| `tests/operate/` | Operate UI tests |
| `tests/identity/` | Identity UI tests |
| `tests/common-flows/` | Cross-app flow tests (V2) |
| `tests/api/` | API tests |
| `tests/api/v2/` | REST API V2 tests |
| `pages/` | Page object classes (V2, default) |
| `pages/v1/` | Page object classes (V1) |
| `utils/` | Shared utilities and Zeebe client helpers |
| `resources/` | BPMN and decision files for test data |

## Running Tests

### Full suite

```bash
bash .github/skills/c8-e2e-tests/scripts/run-tests.sh
```

### Filtered by project

```bash
bash .github/skills/c8-e2e-tests/scripts/run-tests.sh --project=api-tests
bash .github/skills/c8-e2e-tests/scripts/run-tests.sh --project=chromium
```

### Filtered by file

```bash
bash .github/skills/c8-e2e-tests/scripts/run-tests.sh tests/tasklist/task-details.spec.ts
```

### Filtered by test name

```bash
bash .github/skills/c8-e2e-tests/scripts/run-tests.sh -g "should display task details"
```

### Interactive UI mode

```bash
bash .github/skills/c8-e2e-tests/scripts/run-tests.sh --ui
```

## Debugging Failures

After a failed run, open the HTML report and list available traces:

```bash
bash .github/skills/c8-e2e-tests/scripts/show-failures.sh
```

### Root cause decision tree

| Symptom | Action |
|---|---|
| Element not found / wrong selector | Update `pages/<PageName>.ts` — never put selectors in test files |
| Race condition / flakiness | Add `await expect(locator).toBeVisible()` before the action |
| Wrong test data | Check `resources/` BPMN files and `utils/zeebeClient.ts` calls |
| Real product regression | Document the failure; do not mask it |

## Iteration Loop

1. Run the failing test in isolation.
2. Open trace: `npx playwright show-trace test-results/<test>/trace.zip`
3. Identify root cause using the table above.
4. Fix in `pages/` or `tests/`.
5. Lint: `npm run lint` (in `qa/c8-orchestration-cluster-e2e-test-suite/`)
6. Re-run the fixed test, then the full suite.

## Reports and Artifacts

- HTML report: `qa/c8-orchestration-cluster-e2e-test-suite/html-report/`
- Traces: `qa/c8-orchestration-cluster-e2e-test-suite/test-results/`
- JUnit XML: `qa/c8-orchestration-cluster-e2e-test-suite/test-results/junit-report.xml`
