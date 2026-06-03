# C8 Orchestration Cluster E2E Test Suite (stable/8.8)

Playwright + TypeScript end-to-end tests for the Camunda 8.8 orchestration cluster
(Zeebe/Operate/Tasklist/Identity). Lives inside the `camunda/camunda` monorepo at
`qa/c8-orchestration-cluster-e2e-test-suite/`. PRs follow the `camunda/camunda`
contribution workflow; cross-link related Helm or cross-component E2E PRs when applicable.

**Scope:** orchestration-cluster apps only — Zeebe, Operate, Tasklist, Identity.
For multi-app journeys spanning Modeler, Optimize, Connectors, or Console (SaaS/SM/C8Run),
use the sibling [`c8-cross-component-e2e-tests`](https://github.com/camunda/c8-cross-component-e2e-tests)
repository instead.

## Tech Stack

- **Framework**: `@playwright/test` ^1.51.0
- **Language**: TypeScript 5.8.3
- **Node.js**: 23.11.1 (pinned in `.nvmrc` — use `nvm use` or `asdf install`)
- **Pattern**: Page Object Model (POM) with Playwright fixtures
- **API client**: `@camunda8/sdk`
- **Linting**: ESLint + Prettier (enforced via `npm run lint`)

> Tests are **not** version-segregated inside a single checkout (no `SM-8.x/` or `c8Run-8.x/`
> directories). Each `camunda/camunda` branch (`main`, `stable/8.10`, `stable/8.9`,
> `stable/8.8`, `stable/8.7`) carries its own copy of this directory, and the nightly workflow
> runs each branch's copy against its own build. **This file documents the `stable/8.8` copy.**

## Tasklist V1 / V2 Mode (8.8-specific)

8.8 is the release where **Tasklist V2 became the default**, and the test suite supports both.
Unlike 8.9, mode selection here is **tag-based, not runtime-mode-based**:

- V2-only tests carry the `@v2-only` Playwright tag in their `test(...)` title.
- The `chromium` / `firefox` / `msedge` UI projects apply
  `grep: /^(?!.*@v2-only).*$/` — i.e. they **exclude** `@v2-only` tagged tests by default
  so they pass on V1 mode.
- The `tasklist-v2-e2e` project runs V2-tagged tests; the `tasklist-v1-e2e` project runs
  the V1-equivalent specs.
- `CAMUNDA_TASKLIST_V2_MODE_ENABLED` env var still controls Tasklist's runtime mode in
  `docker-compose.yml` — set to `false` to run the V1 backend locally.

**Page objects are shared.** Unlike 8.9, this branch has an **empty `pages/v1/` directory**;
V1 and V2 page objects both live in `pages/*.ts`. The split is at the **spec-file level**
(`tests/tasklist/v1/` for V1-only flows) and via tags.

## Directory Structure

```
tests/
  api/
    v1/            # V1 REST API tests (operate-api-tests, tasklist-api-tests)
    v2/            # V2 REST API tests by resource
    *.spec.ts      # Top-level API spec files (authentication, cluster, document)
  common-flows/    # Cross-component user-flow tests (no v1/ split on this branch)
  identity/        # Identity UI tests
  operate/         # Operate UI tests
  tasklist/
    *.spec.ts      # Tasklist UI tests (V2-only tests carry the @v2-only tag)
    v1/            # V1-only Tasklist specs (currently: public-start-form.spec.ts)
v2-stateless-tests/
  request-validation-test-generator/  # Generator only — no generated specs committed on this branch
fixtures.ts        # Playwright fixture definitions (page object DI) — at root, not under fixtures/
test-setup.ts      # Shared setup helpers (captureScreenshot, captureFailureVideo)
pages/             # Page objects (V1 and V2 share this directory on 8.8)
  v1/              # Reserved for future V1-only pages — currently empty on 8.8
utils/             # Helpers: zeebeClient, waitForAssertion, requestHelpers, cleanup utils, etc.
resources/         # Test data: BPMN files, forms, connector templates
json-body-assertions/    # OpenAPI-driven response assertions (regenerated via npm run responses:regenerate)
config/
  docker-compose.yml         # Local Camunda stack with ES / OpenSearch / Postgres (for Keycloak)
  els-snapshots/             # Elasticsearch snapshot mount (for restore-from-backup tests)
  envs/                      # Per-database .env templates
```

Unlike 8.9 and 8.10, this branch's `v2-stateless-tests/` only contains the **generator** —
no generated `request-validation` specs are committed, and there is **no
`request-validation-tests` Playwright project**. Do not assume that command works here.

`json-body-assertions/_generated/responses.json` is also auto-generated — **never edit it by hand**.
If an API response changes, regenerate it with `npm run responses:regenerate` and commit the result.

## TypeScript Path Aliases

Defined in `tsconfig.json`. Use these consistently in new tests and page objects:

|       Alias       |           Resolves to           |                            Use for                            |
|-------------------|---------------------------------|---------------------------------------------------------------|
| `@fixtures`       | `fixtures.ts`                   | `import {test} from 'fixtures';` (page-object-injecting test) |
| `@setup`          | `./test-setup.ts`               | `captureScreenshot`, `captureFailureVideo`                    |
| `@pages/*`        | `pages/*`                       | Page object classes — shared between V1 and V2 on this branch |
| `@requestHelpers` | `utils/requestHelpers/index.ts` | Typed REST API helpers                                        |

Example test imports:

```typescript
import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {OperateProcessesPage} from '@pages/OperateProcessesPage';

// V2-only test — note the @v2-only tag in the title so default UI projects skip it:
test('my new flow @v2-only', async ({page}) => { /* ... */ });
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
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false   # backend Tasklist mode — V1 if false, V2 if true/unset
```

Ensure the ports in your `.env` match those used in the local stack (e.g., 8080, 8081, 8089).

### Other environment variables read by the suite

|              Variable              |                                                  Purpose                                                  |
|------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `LOCAL_TEST`                       | Required by `npm run test:local` — gates the interactive runner in `runTest.js`                           |
| `CAMUNDA_TASKLIST_V2_MODE_ENABLED` | Controls Tasklist backend mode in docker-compose (`true` default = V2; `false` = V1)                      |
| `PLAYWRIGHT_BASE_URL`              | Overrides default `http://localhost:8080` for all projects                                                |
| `DATABASE`                         | Selects backing database; supports `elasticsearch` and `opensearch`. **No RDBMS support on this branch.** |
| `VERSION`                          | Reporter label (used in Slack title)                                                                      |

> Unlike 8.9/8.10, this branch does **not** read `V2_STATELESS_TESTS` or `API_TESTS_ONLY` —
> these flags were introduced later.

## Running Tests

```bash
# Interactive local test runner (reads .env and prompts for category, browser, and headed/headless mode)
npm run test:local

# Single spec file
npx playwright test --project=chromium tests/operate/your-test.spec.ts

# V1 Tasklist tests
npx playwright test --project=tasklist-v1-e2e

# V2 Tasklist tests
npx playwright test --project=tasklist-v2-e2e

# API tests
npm run test -- --project=api-tests

# Interactive UI mode
npx playwright test --ui

# View the HTML report after a run
npx playwright show-report html-report
```

> **No** `DATABASE=RDBMS` or `V2_STATELESS_TESTS=true` flows on this branch — those exist
> only from 8.9 / 8.10 onwards.

### Output artifacts

|              Path               |                                   Contents                                    |
|---------------------------------|-------------------------------------------------------------------------------|
| `html-report/`                  | Latest Playwright HTML report (open `index.html`)                             |
| `test-results/`                 | Per-test traces (`trace.zip`), screenshots, videos — only retained on failure |
| `test-results/junit-report.xml` | JUnit XML (consumed by TestRail)                                              |
| `json-report/results.json`      | JSON results (parsed by the flakiness agent and CI)                           |

Inspect a failing test's trace with `npx playwright show-trace test-results/<test-dir>/trace.zip`,
or upload the `.zip` to https://trace.playwright.dev.

## Playwright Projects

|              Project               |                         Test match                         |                                                        Notes                                                        |
|------------------------------------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `chromium`                         | `tests/**/*.spec.ts`                                       | Excludes `task-panel.spec.ts` + `tests/api/**`; applies `grep: /^(?!.*@v2-only).*$/` so V2-tagged tests are skipped |
| `chromium-subset`                  | `task-panel.spec.ts`                                       | Teardown project of chromium                                                                                        |
| `firefox` / `msedge`               | Same as chromium                                           | Cross-browser variants                                                                                              |
| `firefox-subset` / `msedge-subset` | `task-panel.spec.ts`                                       | Teardown projects                                                                                                   |
| `api-tests`                        | `tests/api/**/*.spec.ts`                                   | Excludes `tests/api/v2/clock/` and `tests/api/v2/usage-metrics/`                                                    |
| `api-tests-subset`                 | `clock/`, `usage-metrics/`                                 | Sequential teardown of api-tests (`workers: 1`)                                                                     |
| `tasklist-v1-e2e`                  | `tests/tasklist/*.spec.ts` + `tests/tasklist/v1/*.spec.ts` | V1 run — includes shared specs without the V2 tag                                                                   |
| `tasklist-v2-e2e`                  | `tests/tasklist/*.spec.ts`                                 | V2 run                                                                                                              |
| `identity-e2e`                     | `tests/identity/*.spec.ts`                                 | Identity-scoped run                                                                                                 |
| `operate-e2e`                      | `tests/operate/*.spec.ts`                                  | Operate-scoped run                                                                                                  |

Global settings: timeout 12 min, 4 workers, 1 retry, trace/screenshot/video retained on failure.

> **No** `request-validation-tests` and **no** `optimize-default-config` projects on this branch.

## Starting a Local Environment

### Elasticsearch (default)

```bash
# From config/

# V2 backend mode (default)
DATABASE=elasticsearch docker compose up -d camunda

# V1 backend mode
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false DATABASE=elasticsearch docker compose up -d camunda
```

This launches Zeebe + Operate + Tasklist + Identity + Keycloak with the chosen backing database.
Ensure ports in your `.env` match (defaults: 8080, 8081, 8089).

### OpenSearch

```bash
DATABASE=opensearch docker compose up -d camunda
```

### RDBMS — not available on 8.8

There is **no RDBMS support** on this branch:
- No `DATABASE=RDBMS` test code paths.
- No `application.yaml` template in `config/`.
- No dist-based startup documentation.

If you need to validate orchestration-cluster behavior against an RDBMS backend, work on
`stable/8.9` (env-var based) or `stable/8.10` (full local setup) instead.

## Linting

```bash
npm run lint          # tsc + eslint (CI gate)
npm run lint:fix      # auto-fix eslint issues
```

Always run `npm run lint` before committing. Fix all errors — do not commit with lint failures.

## Key Utilities

|             File             |                          Purpose                           |
|------------------------------|------------------------------------------------------------|
| `utils/zeebeClient.ts`       | Zeebe gRPC/REST client wrapper                             |
| `utils/waitForAssertion.ts`  | Retry wrapper for async assertions                         |
| `utils/waitForItemInList.ts` | Paginated list polling helper                              |
| `utils/requestHelpers/`      | Typed HTTP helpers for REST API tests                      |
| `utils/beans/`               | Shared test fixtures / dependency injection                |
| `utils/*Cleanup.ts`          | Per-resource teardown helpers (roles, users, groups, etc.) |
| `utils/constants.ts`         | Shared test constants                                      |
| `fixtures.ts`                | Playwright fixture definitions extending base test         |

## CI Workflows

|                      Workflow                      |        Trigger         |                                                       Link                                                       |
|----------------------------------------------------|------------------------|------------------------------------------------------------------------------------------------------------------|
| `c8-orchestration-cluster-e2e-tests-nightly.yml`   | Nightly (all versions) | [Actions](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-nightly.yml)   |
| `c8-orchestration-cluster-e2e-tests-on-demand.yml` | Manual                 | [Actions](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-on-demand.yml) |

Nightly results post to Slack `#c8-orchestration-cluster-e2e-test-results` and TestRail.

## Branching and Backports

This file lives on `stable/8.8`. Each supported version has its own branch:

| Version |           Branch           |
|---------|----------------------------|
| Next    | `main`                     |
| 8.10    | `stable/8.10`              |
| 8.9     | `stable/8.9`               |
| 8.8     | `stable/8.8` (this branch) |
| 8.7     | `stable/8.7`               |

- **New tests / refactors** — land on `main` first, then backport to each affected `stable/8.x`
  branch using the repo's `backport stable/8.x` label after the original PR merges.
- **Nightly failure fixes on 8.8** — branch off `stable/8.8`, fix there, then forward-port to
  newer branches (`stable/8.9`, `stable/8.10`, `main`) so the fix doesn't regress.
  Be aware that 8.9 and 8.10 have different V1/V2 split mechanics and RDBMS support — a
  straight cherry-pick will not always apply cleanly.
- Each PR should reference the originating nightly run URL in the description so the next
  on-call can audit the trail.

## Contributing

- Follow the Page Object Model: page interactions belong in `pages/`, not in spec files.
- Every test must involve at least one core component (Operate, Tasklist, Identity, Zeebe).
- Reviewers must include someone from the Test Automation Team and a product team developer.
- **Run the on-demand workflow against your branch before requesting review.** PRs without a completed run will be returned. If failures exist, document them in the PR description and confirm they are pre-existing.
- Link the [TestRail test case suite](https://camunda.testrail.com/index.php?/suites/view/17050) in the PR description if any test or page file is modified.
- Track work on the [project board](https://github.com/orgs/camunda/projects/178/views/1).
- For V2-only tests, **tag the test title with `@v2-only`** so the default UI projects exclude it. Without the tag, the test will run in V1 mode and likely fail.
- Avoid introducing new `test.skip()` or `test.fixme()` calls. If a skip is genuinely unavoidable
  (e.g. a confirmed upstream bug blocking the test), it must include a linked issue and a
  rationale comment, and a plan to re-enable it must be tracked on the project board.

## Commit Conventions

Follows the monorepo standard (Conventional Commits, no scope):

```
test: add operate batch cancel assertion on stable/8.8
fix: retry flaky identity role assignment check
```

