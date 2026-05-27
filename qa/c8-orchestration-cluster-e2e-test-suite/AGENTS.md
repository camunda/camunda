# C8 Orchestration Cluster E2E Test Suite (stable/8.9)

Playwright + TypeScript end-to-end tests for the Camunda 8.9 orchestration cluster
(Zeebe/Operate/Tasklist/Identity). Lives inside the `camunda/camunda` monorepo at
`qa/c8-orchestration-cluster-e2e-test-suite/`. PRs follow the `camunda/camunda`
contribution workflow; cross-link related Helm or cross-component E2E PRs when applicable.

**Scope:** orchestration-cluster apps only — Zeebe, Operate, Tasklist, Identity.
For multi-app journeys spanning Modeler, Optimize, Connectors, or Console (SaaS/SM/C8Run),
use the sibling [`c8-cross-component-e2e-tests`](https://github.com/camunda/c8-cross-component-e2e-tests)
repository instead.

## Tech Stack

- **Framework**: `@playwright/test` ^1.51.0
- **Language**: TypeScript 5.9.3
- **Node.js**: 24.13.1 (pinned in `.nvmrc` / `.tool-versions` — use `nvm use` or `asdf install`)
- **Pattern**: Page Object Model (POM) with Playwright fixtures
- **API client**: `@camunda8/sdk`
- **Linting**: ESLint + Prettier (enforced via `npm run lint`)

> Tests are **not** version-segregated inside a single checkout (no `SM-8.x/` or `c8Run-8.x/`
> directories). Each `camunda/camunda` branch (`main`, `stable/8.10`, `stable/8.9`,
> `stable/8.8`, `stable/8.7`) carries its own copy of this directory, and the nightly workflow
> runs each branch's copy against its own build. **This file documents the `stable/8.9` copy.**

## Tasklist V1 / V2 Mode (8.9-specific)

8.9 supports **both Tasklist V1 and V2**. Mode is selected at runtime via the
`CAMUNDA_TASKLIST_V2_MODE_ENABLED` env variable, which `playwright.config.ts` reads to
gate `testMatch` / `testIgnore` for the browser projects:

- **V2 mode (default)** — `CAMUNDA_TASKLIST_V2_MODE_ENABLED=true` or unset.
  Includes `tests/tasklist/*.spec.ts` and `tests/common-flows/*.spec.ts`;
  excludes `tests/tasklist/v1/**` and `tests/common-flows/v1/**`.
- **V1 mode** — `CAMUNDA_TASKLIST_V2_MODE_ENABLED=false`.
  Includes only `tests/tasklist/v1/**` and `tests/common-flows/v1/**`.

Page objects are also split: `pages/*` are the V2 page objects (default); `pages/v1/*` are
the V1-specific Tasklist page objects (`TaskDetailsPage`, `TaskPanelPage`, `TasklistHeader`,
`TasklistProcessesPage`, `TaskListLoginPage`, `UtilitiesPage`). When writing a V1 test,
import from `@pages/v1/...`; when writing a V2 test, import from `@pages/...`.

## Directory Structure

```
tests/
  api/
    v1/            # V1 REST API tests (operate-api-tests, tasklist-api-tests)
    v2/            # V2 REST API tests by resource (stateful; run as api-tests project)
    *.spec.ts      # Top-level API spec files (authentication, cluster, license, document)
  common-flows/
    *.spec.ts      # V2 cross-component user-flow tests
    v1/            # V1 equivalents (run only when CAMUNDA_TASKLIST_V2_MODE_ENABLED=false)
  identity/        # Identity UI tests (mode-agnostic)
  operate/         # Operate UI tests (mode-agnostic)
  tasklist/
    *.spec.ts      # V2 Tasklist UI tests
    v1/            # V1 Tasklist UI tests
v2-stateless-tests/
  tests/request-validation/        # ⚠️ AUTO-GENERATED — do not edit by hand
  request-validation-test-generator/  # Generator that emits the specs above from the OpenAPI spec
fixtures.ts        # Playwright fixture definitions (page object DI) — at root, not under fixtures/
test-setup.ts      # Shared setup helpers (captureScreenshot, captureFailureVideo)
pages/             # V2 page objects (default)
  v1/              # V1-specific Tasklist page objects
utils/             # Helpers: zeebeClient, waitForAssertion, requestHelpers, cleanup utils, etc.
resources/         # Test data: BPMN files, forms, connector templates
json-body-assertions/    # OpenAPI-driven response assertions (regenerated via npm run responses:regenerate)
config/
  docker-compose.yml         # Local Camunda stack with ES / OpenSearch / Postgres (for Keycloak) / Kibana
  envs/                      # Per-database .env templates
```

The request-validation specs under `v2-stateless-tests/tests/request-validation/` are produced by
`request-validation-test-generator/` from `zeebe/gateway-protocol/.../rest-api.yaml`. Each file
carries a `GENERATED FILE - DO NOT EDIT MANUALLY` header. **To change behaviour, edit the
generator (`scripts/`, `src/`) and re-run `npm run regenerate` from the generator directory** —
never patch the generated `.spec.ts` files directly.

## TypeScript Path Aliases

Defined in `tsconfig.json`. Use these consistently in new tests and page objects:

| Alias | Resolves to | Use for |
|---|---|---|
| `@fixtures` | `fixtures.ts` | `import {test} from 'fixtures';` (page-object-injecting test) |
| `@setup` | `./test-setup.ts` | `captureScreenshot`, `captureFailureVideo` |
| `@pages/*` | `pages/*` | Page object classes (use `@pages/v1/...` for V1-specific pages) |
| `@requestHelpers` | `utils/requestHelpers/index.ts` | Typed REST API helpers |

Example test imports:

```typescript
import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {OperateProcessesPage} from '@pages/OperateProcessesPage';
// For V1 Tasklist tests:
// import {TaskDetailsPage} from '@pages/v1/TaskDetailsPage';
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
CORE_APPLICATION_URL=http://localhost:8080
CAMUNDA_AUTH_STRATEGY=BASIC
CAMUNDA_BASIC_AUTH_USERNAME=demo
CAMUNDA_BASIC_AUTH_PASSWORD=demo
ZEEBE_REST_ADDRESS=http://localhost:8080
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false   # set to true for V2 mode (default if unset)
```

Ensure the ports in your `.env` match those used in the local stack (e.g., 8080, 8081, 8089).

### Other environment variables read by the suite

| Variable | Purpose |
|---|---|
| `LOCAL_TEST` | Required by `npm run test:local` — gates the interactive runner in `runTest.js` |
| `CAMUNDA_TASKLIST_V2_MODE_ENABLED` | `true` (default) = V2 mode tests; `false` = V1 mode tests |
| `PLAYWRIGHT_BASE_URL` | Overrides default `http://localhost:8080` for all projects |
| `DATABASE` | Selects backing database; supports `elasticsearch`, `opensearch`, and `RDBMS` (a few API tests have RDBMS-specific conditional logic) |
| `V2_STATELESS_TESTS` | When `true`, switches Playwright to the stateless `request-validation-tests` project set |
| `API_TESTS_ONLY` | Reporter label flag for nightly API-only runs |
| `VERSION`, `DB_NAME` | Reporter labels (used in Slack title) |
| `INCLUDE_SLACK_REPORTER` | `true` activates the Slack reporter (set by CI only) |

## Running Tests

```bash
# Interactive local test runner (reads .env and prompts for category, browser, and headed/headless mode)
npm run test:local

# Single spec file
npx playwright test --project=chromium tests/operate/your-test.spec.ts

# V1 Tasklist tests (mode flag controls which set runs)
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false npx playwright test --project=tasklist-v1-e2e

# V2 Tasklist tests
npx playwright test --project=tasklist-v2-e2e

# API v2 tests against RDBMS backing store
DATABASE=RDBMS npm run test -- --project=api-tests

# V2 stateless request-validation tests only
V2_STATELESS_TESTS=true npm run test -- --project=request-validation-tests

# Interactive UI mode
npx playwright test --ui

# View the HTML report after a run
npx playwright show-report html-report
```

### Output artifacts

| Path | Contents |
|---|---|
| `html-report/` | Latest Playwright HTML report (open `index.html`) |
| `test-results/` | Per-test traces (`trace.zip`), screenshots, videos — only retained on failure |
| `test-results/junit-report.xml` | JUnit XML (consumed by TestRail) |
| `json-report/results.json` | JSON results (parsed by the flakiness agent and CI) |

Inspect a failing test's trace with `npx playwright show-trace test-results/<test-dir>/trace.zip`,
or upload the `.zip` to https://trace.playwright.dev.

## Playwright Projects

| Project | Test match | Notes |
|---|---|---|
| `chromium` | `tests/**/*.spec.ts` (conditional on V2 mode flag) | V2 mode: excludes V1 dirs + `task-panel`; V1 mode: includes V1 dirs only |
| `chromium-subset` | conditional (`tests/tasklist/task-panel.spec.ts` in V2 / `v1/task-panel.spec.ts` in V1) | Teardown project of chromium |
| `firefox` / `msedge` | Same as chromium | Cross-browser variants with their own subset teardowns |
| `firefox-subset` / `msedge-subset` | Same as `chromium-subset` | Teardown projects of firefox/msedge |
| `api-tests` | `tests/api/**/*.spec.ts` | Excludes `clock/`, `usage-metrics/`, `audit-log/`, `job/job-statistics-*` |
| `api-tests-subset` | `clock/`, `usage-metrics/`, `audit-log/`, `job/job-statistics-*` | Sequential teardown of api-tests (`workers: 1`) |
| `tasklist-v1-e2e` | `tests/tasklist/v1/*.spec.ts` | V1-specific Tasklist run |
| `tasklist-v2-e2e` | `tests/tasklist/*.spec.ts` | V2-specific Tasklist run |
| `identity-e2e` | `tests/identity/*.spec.ts` | Identity-scoped run (mode-agnostic) |
| `operate-e2e` | `tests/operate/*.spec.ts` | Operate-scoped run (mode-agnostic) |
| `request-validation-tests` | `v2-stateless-tests/tests/request-validation/*.spec.ts` | Stateless; set `V2_STATELESS_TESTS=true` |

Global settings: timeout 12 min, 4 workers, 1 retry, trace/screenshot/video retained on failure.

## Starting a Local Environment

### Elasticsearch (default)

```bash
# From config/

# V2 mode (default)
DATABASE=elasticsearch docker compose up -d camunda

# V1 mode
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false DATABASE=elasticsearch docker compose up -d camunda
```

This launches Zeebe + Operate + Tasklist + Identity + Keycloak with the chosen backing database.
Ensure ports in your `.env` match (defaults: 8080, 8081, 8089).

### OpenSearch

```bash
DATABASE=opensearch docker compose up -d camunda
```

### RDBMS

8.9 has limited RDBMS support: a small number of API tests check `DATABASE === 'RDBMS'` to skip
or vary behavior, but there is **no `application.yaml` / dist-based local setup** committed in
this branch (unlike 8.10). Run the suite with `DATABASE=RDBMS` against a Camunda instance you
have started separately with the appropriate Spring profiles.

## Linting

```bash
npm run lint          # tsc + eslint (CI gate)
npm run lint:fix      # auto-fix eslint issues
```

Always run `npm run lint` before committing. Fix all errors — do not commit with lint failures.

## Key Utilities

| File | Purpose |
|---|---|
| `utils/zeebeClient.ts` | Zeebe gRPC/REST client wrapper |
| `utils/waitForAssertion.ts` | Retry wrapper for async assertions |
| `utils/waitForItemInList.ts` | Paginated list polling helper |
| `utils/requestHelpers/` | Typed HTTP helpers for REST API tests |
| `utils/beans/` | Shared test fixtures / dependency injection |
| `utils/*Cleanup.ts` | Per-resource teardown helpers (roles, users, groups, etc.) |
| `utils/constants.ts` | Shared test constants |
| `fixtures.ts` | Playwright fixture definitions extending base test |

## CI Workflows

| Workflow | Trigger | Link |
|---|---|---|
| `c8-orchestration-cluster-e2e-tests-nightly.yml` | Nightly (all versions) | [Actions](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-nightly.yml) |
| `c8-orchestration-cluster-e2e-tests-on-demand.yml` | Manual | [Actions](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml) |

Nightly results post to Slack `#c8-orchestration-cluster-e2e-test-results` and TestRail.
Slack titles include `(Tasklist V1)` or `(Tasklist V2)` to disambiguate the mode.

## Branching and Backports

This file lives on `stable/8.9`. Each supported version has its own branch:

| Version | Branch |
|---|---|
| Next | `main` |
| 8.10 | `stable/8.10` |
| 8.9 | `stable/8.9` (this branch) |
| 8.8 | `stable/8.8` |
| 8.7 | `stable/8.7` |

- **New tests / refactors** — land on `main` first, then backport to each affected `stable/8.x`
  branch using the repo's `backport stable/8.x` label after the original PR merges.
- **Nightly failure fixes on 8.9** — branch off `stable/8.9`, fix there, then forward-port to
  newer branches (`stable/8.10`, `main`) so the fix doesn't regress.
- Each PR should reference the originating nightly run URL in the description so the next
  on-call can audit the trail.

## Contributing

- Follow the Page Object Model: page interactions belong in `pages/` (or `pages/v1/`), not in spec files.
- Every test must involve at least one core component (Operate, Tasklist, Identity, Zeebe).
- Reviewers must include someone from the Test Automation Team and a product team developer.
- **Run the on-demand workflow against your branch before requesting review.** PRs without a completed run will be returned. If failures exist, document them in the PR description and confirm they are pre-existing.
- Link the [TestRail test case suite](https://camunda.testrail.com/index.php?/suites/view/17050) in the PR description if any test or page file is modified.
- Track work on the [project board](https://github.com/orgs/camunda/projects/178/views/1).
- When changing Tasklist behavior, ensure **both** V1 and V2 specs are updated where applicable, or document why only one mode is affected.
- Avoid introducing new `test.skip()` or `test.fixme()` calls. If a skip is genuinely unavoidable
  (e.g. a confirmed upstream bug blocking the test), it must include a linked issue and a
  rationale comment, and a plan to re-enable it must be tracked on the project board.

## Commit Conventions

Follows the monorepo standard (Conventional Commits, no scope):

```
test: add operate batch cancel assertion for V2 tasklist mode
fix: retry flaky identity role assignment check on stable/8.9
```
