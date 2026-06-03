# C8 Orchestration Cluster E2E Test Suite (stable/8.7)

Playwright + TypeScript end-to-end tests for the Camunda 8.7 orchestration cluster
(Zeebe/Operate/Tasklist). Lives inside the `camunda/camunda` monorepo at
`qa/c8-orchestration-cluster-e2e-test-suite/`. PRs follow the `camunda/camunda`
contribution workflow; cross-link related Helm or cross-component E2E PRs when applicable.

**Scope:** orchestration-cluster apps only — Zeebe, Operate, Tasklist.
For multi-app journeys spanning Modeler, Optimize, Connectors, or Console (SaaS/SM/C8Run),
use the sibling [`c8-cross-component-e2e-tests`](https://github.com/camunda/c8-cross-component-e2e-tests)
repository instead.

## Tech Stack

- **Framework**: `@playwright/test` ^1.51.0
- **Language**: TypeScript 5.8.3
- **Node.js**: 23.9.0 (pinned in `.nvmrc` — use `nvm use` or `asdf install`)
- **Pattern**: Page Object Model (POM) with Playwright fixtures
- **API client**: `@camunda8/sdk`
- **Linting**: ESLint + Prettier (enforced via `npm run lint`)

> Tests are **not** version-segregated inside a single checkout. Each `camunda/camunda`
> branch (`main`, `stable/8.10`, `stable/8.9`, `stable/8.8`, `stable/8.7`) carries its own
> copy of this directory. **This file documents the `stable/8.7` copy.**

## Directory Structure

```
tests/
  api/           # REST API tests (operate-api-tests, tasklist-api-tests)
  common-flows/  # Cross-component user-flow tests
  operate/       # Operate UI tests
  tasklist/      # Tasklist UI tests
fixtures.ts      # Playwright fixture definitions (page object DI) — at root, not under fixtures/
test-setup.ts    # Shared setup helpers (captureScreenshot, captureFailureVideo)
pages/           # Page object classes
utils/           # Helpers: zeebeClient, waitForAssertion, apiHelpers, sleep, etc.
resources/       # Test data: BPMN files, forms
config/
  docker-compose.yml      # Local Camunda stack with Elasticsearch
  envs/
    .env.database.elasticsearch   # Per-database env template (Elasticsearch only on 8.7)
```

## TypeScript Path Aliases

Defined in `tsconfig.json`:

| Alias       | Resolves to    | Use for                                         |
|-------------|----------------|-------------------------------------------------|
| `@fixtures` | `fixtures.ts`  | `import {test} from 'fixtures';`                |
| `@setup`    | `test-setup.ts`| `captureScreenshot`, `captureFailureVideo`      |
| `@pages/*`  | `pages/*`      | Page object classes                             |

Example test imports:

```typescript
import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {OperateProcessesPage} from '@pages/OperateProcessesPage';
```

## Setup

```bash
# From qa/c8-orchestration-cluster-e2e-test-suite/
npm install
npx playwright install
```

Create a `.env` file in this directory — never commit it:

```env
LOCAL_TEST=true
CORE_APPLICATION_TASKLIST_URL=http://localhost:8080
CORE_APPLICATION_OPERATE_URL=http://localhost:8081
CAMUNDA_AUTH_STRATEGY=BASIC
CAMUNDA_BASIC_AUTH_USERNAME=demo
CAMUNDA_BASIC_AUTH_PASSWORD=demo
ZEEBE_REST_ADDRESS=http://localhost:8089
```

Ensure the ports in your `.env` match those used in the local stack.

### Environment variables read by the suite

| Variable                  | Purpose                                                                    |
|---------------------------|----------------------------------------------------------------------------|
| `LOCAL_TEST`              | Required by `npm run test:local` — gates the interactive runner            |
| `CORE_APPLICATION_TASKLIST_URL` | Base URL for Tasklist (default `http://localhost:8080`)            |
| `CORE_APPLICATION_OPERATE_URL`  | Base URL for Operate (default `http://localhost:8081`)             |
| `PLAYWRIGHT_BASE_URL`     | Overrides the default base URL for all projects                            |
| `CAMUNDA_AUTH_STRATEGY`   | Auth mode: `BASIC` for local                                               |
| `CAMUNDA_BASIC_AUTH_USERNAME` / `CAMUNDA_BASIC_AUTH_PASSWORD` | Credentials for BASIC auth |
| `ZEEBE_REST_ADDRESS`      | Zeebe REST gateway address                                                 |
| `VERSION`, `DB_NAME`      | Reporter labels (used in Slack title)                                      |
| `INCLUDE_SLACK_REPORTER`  | `true` activates the Slack reporter (set by CI only)                       |

## Running Tests

```bash
# Interactive local test runner (reads .env, prompts for category/browser/headless)
npm run test:local

# Single spec file
npx playwright test --project=chromium tests/operate/your-test.spec.ts

# All Tasklist tests
npx playwright test --project=tasklist-e2e

# API tests only
npx playwright test --project=api-tests

# Interactive UI mode
npx playwright test --ui

# View the HTML report after a run
npx playwright show-report html-report
```

### Output artifacts

| Path                            | Contents                                                  |
|---------------------------------|-----------------------------------------------------------|
| `html-report/`                  | Latest Playwright HTML report (open `index.html`)         |
| `test-results/`                 | Per-test traces (`trace.zip`), screenshots — on failure   |
| `test-results/junit-report.xml` | JUnit XML (consumed by TestRail)                          |
| `json-report/results.json`      | JSON results (parsed by the flakiness agent and CI)       |

Inspect a failing test's trace with `npx playwright show-trace test-results/<test-dir>/trace.zip`,
or upload the `.zip` to https://trace.playwright.dev.

## Playwright Projects

| Project           | Test match                        | Notes                                        |
|-------------------|-----------------------------------|----------------------------------------------|
| `chromium`        | `tests/**/*.spec.ts`              | Excludes `task-panel.spec.ts` and `tests/api/`; teardown: `chromium-subset` |
| `chromium-subset` | `task-panel.spec.ts`              | Teardown project of `chromium`               |
| `firefox`         | Same as `chromium`                | Cross-browser variant; teardown: `firefox-subset` |
| `firefox-subset`  | `task-panel.spec.ts`              | Teardown project of `firefox`                |
| `msedge`          | Same as `chromium`                | Cross-browser variant; teardown: `msedge-subset` |
| `msedge-subset`   | `task-panel.spec.ts`              | Teardown project of `msedge`                 |
| `tasklist-e2e`    | `tests/tasklist/*.spec.ts`        | Tasklist-scoped run                          |
| `api-tests`       | `tests/api/*.spec.ts`             | REST API tests                               |

Global settings: timeout 12 min, 4 workers, 1 retry, screenshot retained on failure.

## Starting a Local Environment

```bash
# From config/ — Elasticsearch only (only backing database supported on 8.7)
DATABASE=elasticsearch docker compose up -d tasklist operate
```

This launches Tasklist and Operate with Elasticsearch. Ensure ports in your `.env` match
(defaults: Tasklist 8080, Operate 8081, Zeebe REST 8089).

## Linting

```bash
npm run lint          # tsc + eslint (CI gate)
npm run lint:fix      # auto-fix eslint issues
```

Always run `npm run lint` before committing. Fix all errors — do not commit with lint failures.

## Key Utilities

| File                          | Purpose                                          |
|-------------------------------|--------------------------------------------------|
| `utils/zeebeClient.ts`        | Zeebe gRPC/REST client wrapper                   |
| `utils/waitForAssertion.ts`   | Retry wrapper for async assertions               |
| `utils/apiHelpers.ts`         | HTTP helpers for REST API tests                  |
| `utils/sleep.ts`              | Async sleep helper                               |
| `utils/incidentsHelper.ts`    | Incident-related test helpers                    |
| `utils/operations.helper.ts`  | Operate operations helpers                       |
| `utils/constants.ts`          | Shared test constants                            |
| `fixtures.ts`                 | Playwright fixture definitions extending base test |

## CI Workflows

| Workflow                                             | Trigger                | Link                                                                                                                     |
|------------------------------------------------------|------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `c8-orchestration-cluster-e2e-tests-nightly.yml`     | Nightly (all versions) | [Actions](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-nightly.yml)           |
| `c8-orchestration-cluster-e2e-tests-on-demand.yml`   | Manual                 | [Actions](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml)         |

Nightly results post to Slack `#c8-orchestration-cluster-e2e-test-results` and TestRail.

## Branching and Backports

This file lives on `stable/8.7`. Each supported version has its own branch:

| Version | Branch                     |
|---------|----------------------------|
| Next    | `main`                     |
| 8.10    | `stable/8.10`              |
| 8.9     | `stable/8.9`               |
| 8.8     | `stable/8.8`               |
| 8.7     | `stable/8.7` (this branch) |

- **New tests / refactors** — land on `main` first, then backport to each affected `stable/8.x`
  branch using the repo's `backport stable/8.x` label after the original PR merges.
- **Nightly failure fixes on 8.7** — branch off `stable/8.7`, fix there, then forward-port to
  newer branches so the fix doesn't regress.
- Each PR should reference the originating nightly run URL in the description so the next
  on-call can audit the trail.

## Contributing

- Follow the Page Object Model: page interactions belong in `pages/`, not in spec files.
- Every test must involve at least one core component (Operate, Tasklist, Zeebe).
- Reviewers must include someone from the Test Automation Team and a product team developer.
- **Run the on-demand workflow against your branch before requesting review.** PRs without a
  completed run will be returned. Document any pre-existing failures in the PR description.
- Link the [TestRail test case suite](https://camunda.testrail.com/index.php?/suites/view/17050)
  in the PR description if any test or page file is modified.
- Track work on the [project board](https://github.com/orgs/camunda/projects/178/views/1).
- Avoid introducing new `test.skip()` or `test.fixme()` calls. If a skip is genuinely
  unavoidable (e.g. a confirmed upstream bug), it must include a linked issue and a rationale
  comment, and a plan to re-enable it must be tracked on the project board.

## Commit Conventions

Follows the monorepo standard (Conventional Commits, no scope):

```
test: add operate batch cancel assertion
fix: retry flaky identity role assignment check on stable/8.7
```
