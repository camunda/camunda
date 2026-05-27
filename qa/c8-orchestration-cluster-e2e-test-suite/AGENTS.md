# C8 Orchestration Cluster E2E Test Suite

Playwright + TypeScript end-to-end tests for the Camunda 8 orchestration cluster
(Zeebe/Operate/Tasklist/Identity). Lives inside the `camunda/camunda` monorepo at
`qa/c8-orchestration-cluster-e2e-test-suite/`. PRs must follow the `camunda/camunda`
contribution workflow and be cross-linked to any related Helm or cross-component E2E PRs.

## Tech Stack

- **Framework**: `@playwright/test` ^1.51.0
- **Language**: TypeScript 5.9
- **Node.js**: 24.16.0 (pinned in `.nvmrc` / `.tool-versions` — use `nvm use` or `asdf install`)
- **Pattern**: Page Object Model (POM) with Playwright fixtures
- **API client**: `@camunda8/sdk`
- **Linting**: ESLint + Prettier (enforced via `npm run lint`)

> Unlike the sibling `c8-cross-component-e2e-tests` repo, tests here are **not** version-segregated
> (no `SM-8.x/` or `c8Run-8.x/` directories). Versions are validated by running the suite against
> the matching nightly branch of `camunda/camunda`.

## Directory Structure

```
tests/
  api/             # API tests
    v2/            # REST API v2 tests by resource (stateful; run as api-tests project)
    *.spec.ts      # Top-level API spec files (e.g. authentication, cluster, license)
  common-flows/    # Cross-component flow tests
  identity/        # Identity UI tests
  operate/         # Operate UI tests
  tasklist/        # Tasklist UI tests
v2-stateless-tests/
  tests/request-validation/        # ⚠️ AUTO-GENERATED — do not edit by hand
  request-validation-test-generator/  # Generator that emits the specs above from the OpenAPI spec
fixtures.ts        # Playwright fixture definitions (page object DI) — note: at root, not in fixtures/
test-setup.ts      # Shared setup helpers (captureScreenshot, captureFailureVideo)
pages/             # Page Object Model classes (one file per page)
utils/             # Helpers: zeebeClient, waitForAssertion, requestHelpers, cleanup utils, etc.
resources/         # Test data: BPMN files, forms, connector templates
json-body-assertions/    # OpenAPI-driven response assertions (regenerated via npm run responses:regenerate)
config/
  docker-compose.yml         # Local Elasticsearch + Camunda stack
  application.yaml           # RDBMS config template (copy to dist before starting)
  envs/                      # Per-database .env templates (e.g. .env.database.elasticsearch)
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
| `@pages/*` | `pages/*` | Page object classes — `import {OperateProcessesPage} from '@pages/OperateProcessesPage';` |
| `@requestHelpers` | `utils/requestHelpers/index.ts` | Typed REST API helpers |

Example test imports (from `tests/operate/batchOperations.spec.ts`):

```typescript
import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {createCancellationBatch, expectBatchState} from '@requestHelpers';
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
DATABASE_CONTAINER=<Service name from db/docker-compose.yml>
```

Ensure the ports in your `.env` match those used in the local stack (e.g., 8080, 8081, 8089).

### Other environment variables read by the suite

| Variable | Purpose |
|---|---|
| `LOCAL_TEST` | Required by `npm run test:local` — gates the interactive runner in `runTest.js` |
| `PLAYWRIGHT_BASE_URL` | Overrides default `http://localhost:8080` for all projects |
| `DATABASE` | Selects backing database (`elasticsearch`, `opensearch`, `RDBMS`, ...); used by docker compose and `api-tests` |
| `V2_STATELESS_TESTS` | When `true`, switches Playwright to the stateless `request-validation-tests` project set |
| `API_TESTS_ONLY` | Reporter label flag for nightly API-only runs |
| `VERSION`, `DB_NAME` | Reporter labels (used in Slack title from `playwright.config.ts`) |
| `INCLUDE_SLACK_REPORTER` | `true` activates the Slack reporter (set by CI only) |

## Running Tests

```bash
# Interactive local test runner (reads .env and prompts for category, browser, and headed/headless mode)
npm run test:local

# Single spec file
npx playwright test --project=chromium tests/operate/your-test.spec.ts

# API v2 tests against RDBMS setup
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
| `chromium` | `tests/**/*.spec.ts` | Main UI tests; excludes `task-panel.spec.ts`, `tests/api/**`, and `v2-stateless-tests/**` |
| `chromium-subset` | `tests/tasklist/task-panel.spec.ts` | Teardown project of chromium |
| `firefox` | Same as chromium | Cross-browser variant |
| `firefox-subset` | `tests/tasklist/task-panel.spec.ts` | Teardown project of firefox |
| `msedge` | Same as chromium | Cross-browser variant |
| `msedge-subset` | `tests/tasklist/task-panel.spec.ts` | Teardown project of msedge |
| `api-tests` | `tests/api/v2/**/*.spec.ts` | Stateful REST API tests; excludes `clock/`, `usage-metrics/`, `audit-log/`, `job/job-statistics-*`, `optimize/**` |
| `api-tests-subset` | `clock/`, `usage-metrics/`, `audit-log/`, `job/job-statistics-*` | Sequential teardown of api-tests (`workers: 1`) |
| `tasklist-e2e` | `tests/tasklist/*.spec.ts` | Tasklist-scoped run |
| `identity-e2e` | `tests/identity/*.spec.ts` | Identity-scoped run |
| `operate-e2e` | `tests/operate/*.spec.ts` | Operate-scoped run |
| `optimize-default-config` | `tests/api/v2/optimize/default-config.spec.ts` | Single Optimize config test |
| `request-validation-tests` | `v2-stateless-tests/tests/request-validation/*.spec.ts` | Stateless; set `V2_STATELESS_TESTS=true` |

Global settings: timeout 12 min, 4 workers, 1 retry, trace/screenshot/video retained on failure.

## Starting a Local Environment

### Elasticsearch (default)

```bash
# From config/
DATABASE=elasticsearch docker compose up -d camunda
```

Ensure `.env` ports match (default `CORE_APPLICATION_URL=http://localhost:8080`).

### RDBMS (Oracle, PostgreSQL, etc.)

```bash
# All commands run from the monorepo root unless noted otherwise

# 1. Build distribution
./mvnw install -Dquickly -T1C -PskipFrontendBuild

# 2. Unpack (stay in monorepo root)
tar -xzf dist/target/camunda-orchestration-cluster-*.tar.gz -C dist/target/

# 3. Copy RDBMS config
cp qa/c8-orchestration-cluster-e2e-test-suite/config/application.yaml \
   dist/target/camunda-zeebe-<version>/config/

# 4. Add JDBC driver (example: Oracle)
curl -L -o dist/target/camunda-zeebe-<version>/lib/ojdbc11.jar \
    https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.3.0.23.09/ojdbc11-23.3.0.23.09.jar

# 5. Start DB container
docker compose -f db/docker-compose.yml up -d --wait <service-name>

# 6. Start Camunda
cd dist/target/camunda-zeebe-<version>
export SPRING_PROFILES_ACTIVE="broker,consolidated-auth,admin,operate,tasklist"
export CAMUNDA_SECURITY_AUTHENTICATION_METHOD="BASIC"
export CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED="true"
# ... (see README.md for full env var list)
./bin/camunda
```

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

## Contributing

- Follow the Page Object Model: page interactions belong in `pages/`, not in spec files.
- Every test must involve at least one core component (Operate, Tasklist, Identity, Zeebe).
- Reviewers must include someone from the Test Automation Team and a product team developer.
- **Run the on-demand workflow against your branch before requesting review.** PRs without a completed run will be returned. If failures exist, document them in the PR description and confirm they are pre-existing.
- Link the [TestRail test case suite](https://camunda.testrail.com/index.php?/suites/view/17050) in the PR description if any test or page file is modified.
- Track work on the [project board](https://github.com/orgs/camunda/projects/178/views/1).

## Commit Conventions

Follows the monorepo standard (Conventional Commits, no scope):

```
test: add operate batch cancel assertion for RDBMS
fix: retry flaky identity role assignment check
```

Avoid introducing new `test.skip()` or `test.fixme()` calls. If a skip is genuinely unavoidable (e.g. a confirmed upstream bug blocking the test), it must include a linked issue and a rationale comment, and a plan to re-enable it must be tracked on the project board.
