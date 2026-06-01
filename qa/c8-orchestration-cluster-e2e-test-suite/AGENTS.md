# C8 Orchestration Cluster E2E Test Suite

Playwright + TypeScript end-to-end tests for the Camunda 8 orchestration cluster
(Zeebe/Operate/Tasklist/Identity). Lives inside the `camunda/camunda` monorepo at
`qa/c8-orchestration-cluster-e2e-test-suite/`. PRs follow the `camunda/camunda`
contribution workflow; cross-link related Helm or cross-component E2E PRs when applicable.

**Scope:** orchestration-cluster apps only — Zeebe, Operate, Tasklist, Identity.
For multi-app journeys that span Modeler, Optimize, Connectors, or Console (SaaS/SM/C8Run),
use the sibling [`c8-cross-component-e2e-tests`](https://github.com/camunda/c8-cross-component-e2e-tests)
repository instead.

## Tech Stack

- **Framework**: `@playwright/test` ^1.51.0
- **Language**: TypeScript 5.9
- **Node.js**: 24.16.0 (pinned in `.nvmrc` / `.tool-versions` — use `nvm use` or `asdf install`)
- **Pattern**: Page Object Model (POM) with Playwright fixtures
- **API client**: `@camunda8/sdk`
- **Linting**: ESLint + Prettier (enforced via `npm run lint`)

> Tests are **not** version-segregated inside a single checkout (no `SM-8.x/` or `c8Run-8.x/`
> directories). Instead, each `camunda/camunda` branch (`main`, `stable/8.10`, `stable/8.9`,
> `stable/8.8`, `stable/8.7`) carries its own copy of this directory, and the nightly workflow
> runs each branch's copy against its own build.

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

|       Alias       |           Resolves to           |                                          Use for                                          |
|-------------------|---------------------------------|-------------------------------------------------------------------------------------------|
| `@fixtures`       | `fixtures.ts`                   | `import {test} from 'fixtures';` (page-object-injecting test)                             |
| `@setup`          | `./test-setup.ts`               | `captureScreenshot`, `captureFailureVideo`                                                |
| `@pages/*`        | `pages/*`                       | Page object classes — `import {OperateProcessesPage} from '@pages/OperateProcessesPage';` |
| `@requestHelpers` | `utils/requestHelpers/index.ts` | Typed REST API helpers                                                                    |

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

|         Variable         |                                                    Purpose                                                     |
|--------------------------|----------------------------------------------------------------------------------------------------------------|
| `LOCAL_TEST`             | Required by `npm run test:local` — gates the interactive runner in `runTest.js`                                |
| `PLAYWRIGHT_BASE_URL`    | Overrides default `http://localhost:8080` for all projects                                                     |
| `DATABASE`               | Selects backing database (`elasticsearch`, `opensearch`, `RDBMS`, ...); used by docker compose and `api-tests` |
| `V2_STATELESS_TESTS`     | When `true`, switches Playwright to the stateless `request-validation-tests` project set                       |
| `API_TESTS_ONLY`         | Reporter label flag for nightly API-only runs                                                                  |
| `VERSION`, `DB_NAME`     | Reporter labels (used in Slack title from `playwright.config.ts`)                                              |
| `INCLUDE_SLACK_REPORTER` | `true` activates the Slack reporter (set by CI only)                                                           |

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

|              Path               |                                   Contents                                    |
|---------------------------------|-------------------------------------------------------------------------------|
| `html-report/`                  | Latest Playwright HTML report (open `index.html`)                             |
| `test-results/`                 | Per-test traces (`trace.zip`), screenshots, videos — only retained on failure |
| `test-results/junit-report.xml` | JUnit XML (consumed by TestRail)                                              |
| `json-report/results.json`      | JSON results (parsed by the flakiness agent and CI)                           |

Inspect a failing test's trace with `npx playwright show-trace test-results/<test-dir>/trace.zip`,
or upload the `.zip` to https://trace.playwright.dev.

## Playwright Projects

|          Project           |                            Test match                            |                                                       Notes                                                       |
|----------------------------|------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `chromium`                 | `tests/**/*.spec.ts`                                             | Main UI tests; excludes `task-panel.spec.ts`, `tests/api/**`, and `v2-stateless-tests/**`                         |
| `chromium-subset`          | `tests/tasklist/task-panel.spec.ts`                              | Teardown project of chromium                                                                                      |
| `firefox`                  | Same as chromium                                                 | Cross-browser variant                                                                                             |
| `firefox-subset`           | `tests/tasklist/task-panel.spec.ts`                              | Teardown project of firefox                                                                                       |
| `msedge`                   | Same as chromium                                                 | Cross-browser variant                                                                                             |
| `msedge-subset`            | `tests/tasklist/task-panel.spec.ts`                              | Teardown project of msedge                                                                                        |
| `api-tests`                | `tests/api/v2/**/*.spec.ts`                                      | Stateful REST API tests; excludes `clock/`, `usage-metrics/`, `audit-log/`, `job/job-statistics-*`, `optimize/**` |
| `api-tests-subset`         | `clock/`, `usage-metrics/`, `audit-log/`, `job/job-statistics-*` | Sequential teardown of api-tests (`workers: 1`)                                                                   |
| `tasklist-e2e`             | `tests/tasklist/*.spec.ts`                                       | Tasklist-scoped run                                                                                               |
| `identity-e2e`             | `tests/identity/*.spec.ts`                                       | Identity-scoped run                                                                                               |
| `operate-e2e`              | `tests/operate/*.spec.ts`                                        | Operate-scoped run                                                                                                |
| `optimize-default-config`  | `tests/api/v2/optimize/default-config.spec.ts`                   | Single Optimize config test                                                                                       |
| `request-validation-tests` | `v2-stateless-tests/tests/request-validation/*.spec.ts`          | Stateless; set `V2_STATELESS_TESTS=true`                                                                          |

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

Each supported version lives on its own branch in `camunda/camunda`:

| Version |    Branch     |
|---------|---------------|
| Next    | `main`        |
| 8.10    | `stable/8.10` |
| 8.9     | `stable/8.9`  |
| 8.8     | `stable/8.8`  |
| 8.7     | `stable/8.7`  |

- **New tests / refactors** — land on `main` first, then backport to each affected `stable/8.x` branch
  using the repo's standard backport label (`backport stable/8.x`) once the original PR merges.
- **Nightly failure fixes** — branch off the **affected** stable branch, fix there first, then
  forward-port to newer branches up to `main` so the fix doesn't regress. If the failure also
  reproduces on `main`, land there first and backport instead.
- Each PR should reference the originating nightly run URL in the description so the next on-call
  can audit the trail.

## Contributing

- Follow the Page Object Model: page interactions belong in `pages/`, not in spec files.
- Every test must involve at least one core component (Operate, Tasklist, Identity, Zeebe).
- Reviewers must include someone from the Test Automation Team and a product team developer.
- **Run the on-demand workflow against your branch before requesting review.** PRs without a completed run will be returned. If failures exist, document them in the PR description and confirm they are pre-existing.
- Link the [TestRail test case suite](https://camunda.testrail.com/index.php?/suites/view/17050) in the PR description if any test or page file is modified.
- Track work on the [project board](https://github.com/orgs/camunda/projects/178/views/1).
- Avoid introducing new `test.skip()` or `test.fixme()` calls. If a skip is genuinely unavoidable
  (e.g. a confirmed upstream bug blocking the test), it must include a linked issue and a
  rationale comment, and a plan to re-enable it must be tracked on the project board.

## Commit Conventions

Follows the monorepo standard (Conventional Commits, no scope):

```
test: add operate batch cancel assertion for RDBMS
fix: retry flaky identity role assignment check
```

## Nightly Fix Agent

This section is read automatically by the Claude Code agent dispatched from
`c8-orchestration-cluster-e2e-nightly-triage.yml`. It defines the agent's
diagnosis steps and operating constraints.

### Context the agent receives

The triage workflow writes `/tmp/test_specs.json` before invoking the agent.
Each entry is one failing test:

```json
{
  "file": "tests/operate/processInstancesFilters.spec.ts",
  "test_name": "Filter process instances by parent key, date range, and error message",
  "error": "TimeoutError: locator.click: Timeout 10000ms exceeded.\n  - waiting for getByRole('menuitem', { name: 'Operation Id' })\n",
  "test_type": "e2e",
  "tasklist_mode": "v2"
}
```

For API entries, `test_type` is `"api"` and `tasklist_mode` is absent;
`database` is set (`"elasticsearch"` for ES nightlies, e.g. `"Postgres 17"`
for RDBMS).

`file` is **relative to this test suite** (`qa/c8-orchestration-cluster-e2e-test-suite/`).
To read the test code, open
`qa/c8-orchestration-cluster-e2e-test-suite/<file>`.

The agent is also given the failing nightly run IDs via env vars:
`E2E_RUN_ID`, `API_ES_RUN_ID`, `API_RDBMS_RUN_ID`. Empty string means that
test type did not fail (or does not exist for this version).

### Diagnosis steps

**Step 1 — Download nightly artifacts.** Run this block as-is (env vars are
already exported by the calling workflow):

```bash
mkdir -p /tmp/nightly-artifacts

if [ "$HAS_E2E" = "true" ] && [ -n "$E2E_RUN_ID" ]; then
  for mode in v1 v2; do
    # 8.7 has no v2, main has no v1 — gh silently no-ops on missing patterns
    gh run download "$E2E_RUN_ID" --repo "$REPO" \
      --pattern "json-report-nightly-e2e-${SAFE_VERSION}-${mode}" \
      --dir "/tmp/nightly-artifacts/e2e-${mode}" 2>/dev/null || true
    gh run download "$E2E_RUN_ID" --repo "$REPO" \
      --pattern "html-report-nightly-e2e-${SAFE_VERSION}-${mode}" \
      --dir "/tmp/nightly-artifacts/e2e-${mode}-html" 2>/dev/null || true
  done
fi

if [ "$HAS_API_ES" = "true" ] && [ -n "$API_ES_RUN_ID" ]; then
  gh run download "$API_ES_RUN_ID" --repo "$REPO" \
    --pattern "json-report-nightly-api-${SAFE_VERSION}" \
    --dir /tmp/nightly-artifacts/api-es 2>/dev/null || true
fi

if [ "$HAS_API_RDBMS" = "true" ] && [ -n "$API_RDBMS_RUN_ID" ]; then
  # Only download the DB-specific artifacts that have failing tests
  jq -r '[.[] | select(.test_type == "api" and .database != null
           and .database != "" and .database != "elasticsearch")
           | .database] | unique[]' /tmp/test_specs.json | while read -r db; do
    gh run download "$API_RDBMS_RUN_ID" --repo "$REPO" \
      --pattern "json-report-nightly-api-rdbms-${SAFE_VERSION}-${db}" \
      --dir "/tmp/nightly-artifacts/api-rdbms" 2>/dev/null || true
  done
fi
```

Resulting layout:

```
/tmp/nightly-artifacts/
  e2e-v1/        json-report-nightly-e2e-<safe_version>-v1/results.json
  e2e-v1-html/   html-report-nightly-e2e-<safe_version>-v1/...   ← contains screenshots
  e2e-v2/        json-report-nightly-e2e-<safe_version>-v2/results.json
  e2e-v2-html/   html-report-nightly-e2e-<safe_version>-v2/...
  api-es/        json-report-nightly-api-<safe_version>/results.json
  api-rdbms/     json-report-nightly-api-rdbms-<safe_version>-<db>/results.json
```

Screenshots from Playwright's HTML report live under
`html-report/.../data/*.png`. You can read PNGs directly — use them as the
primary signal for E2E diagnosis.

**Step 2 — Read the test file.** Open
`qa/c8-orchestration-cluster-e2e-test-suite/<file>` from the test spec.
Read any imported page objects under
`qa/c8-orchestration-cluster-e2e-test-suite/pages/` and helpers under
`qa/c8-orchestration-cluster-e2e-test-suite/utils/`.

**Step 3 — Form a hypothesis.** Match the screenshot and/or error message to
a specific assertion or action. Typical patterns:

- **Element not found within timeout** — the previous step did not finish
  rendering. Add a `expect(...).toBeVisible()` or `waitFor({state: 'visible'})`
  before the click. Do NOT use `page.waitForTimeout()` — it is banned.
- **Stale element / re-render race** — wrap the action in a retry helper if
  one exists in `utils/`, or split into smaller steps.
- **Auth / cookie state lost between describes** — check `beforeEach` for a
  missing `context.clearCookies()` or login step.
- **API response shape change** — only a test-side fix is in scope if the
  test was asserting on a field that legitimately moved/renamed. If the
  endpoint regressed, this is a product bug — see Step 4.

**Step 4 — Decide: fix or stop.**

If the failure is **clearly a product bug** (e.g., a 500 from the server, a
panel that never renders despite correct backend response, an authorization
regression) and no test-side change is reasonable: write `{"prs":[]}` to
`/tmp/fix-meta.json` and stop. Do not open a PR. Do not skip the test.

If a minimal test-side fix exists, apply it.

### Apply the fix

1. The repo is already on the correct target branch (`stable/<version>` or
   `main`). Verify with `git status`.
2. Create a fix branch:
   `git checkout -b fix/nightly-${VERSION}-<short-slug>`
3. Edit files under `qa/c8-orchestration-cluster-e2e-test-suite/` only.
   Touch page objects when the selector itself is wrong; touch utilities
   only when multiple tests share the same root cause.
4. Lint changed files:

   ```bash
   cd qa/c8-orchestration-cluster-e2e-test-suite
   npx prettier --write <changed-files>
   npx eslint <changed-files> --fix
   ```

   Fix any remaining eslint errors before committing.

5. Commit with `test:` (NOT `fix:` — see above on Conventional Commits):
   `git commit -m "test: <one-line description> (nightly ${VERSION})"`

6. Push: `git push -u origin fix/nightly-${VERSION}-<slug>`

7. Check for existing open PRs before opening a new one:

   ```bash
   gh pr list --repo camunda/camunda \
     --search "is:open label:failing-test-fix" \
     --json number,title,baseRefName,headRefName
   ```

   If an open PR targets the same `stable/<version>` for the same root
   cause: comment on it with your additional diagnosis; do not open a
   second PR.

8. Open a draft PR targeting the same base branch you are on, label
   `failing-test-fix`. Body must include:

   - Root cause analysis
   - List of fixed tests (file + name)
   - Link to the failing nightly run(s)
   - Link to the triage run

### Constraints

- **Allowed tools:** `gh`, `git`, `grep`, `rg`, `cat`, `find`, `jq`, `sed`, `awk`, `unzip`, `npx prettier`, `npx eslint`.
- **Forbidden:** `make`, `mvn`, `./mvnw`, `docker`, `kubectl`, `helm`, `npm install`, `npm run build`, `npm run test`, `npx playwright test`. The fix agent does **not** execute tests — it fixes from artifact evidence only. Verification is delegated to the on-demand workflows triggered by the calling workflow.
- **NO skipping — EVER:** `test.skip()`, `test.fixme()`, `test.only`, and all pending variants are banned. There are no exceptions, not even for confirmed product bugs. If you cannot fix in code, write `{"prs":[]}` and stop.
- **Minimal diff:** no refactoring, no dependency bumps, no unrelated edits, no formatting sweeps on untouched files.
- **PR title type must be `test:`** — commitlint rejects `fix:` for test-only changes (see Commit / PR Conventions above).
- **One PR per version** — a single PR may fix multiple tests on the same `stable/<version>` branch, including a mix of API and E2E failures, but never crosses branch boundaries.

### Result manifest

Always write `/tmp/fix-meta.json` before stopping:

```json
{
  "prs": [
    {
      "number": 1234,
      "owner": "camunda",
      "repo": "camunda",
      "branch": "fix/nightly-8.9-operate-filter-wait",
      "has_e2e": true,
      "has_api": false
    }
  ]
}
```

**`branch` is mandatory and must never be `null`.** Set it to the exact
`headRefName` of the PR you opened:
```bash
gh pr view <number> --repo camunda/camunda --json headRefName --jq '.headRefName'
```

Use `{"prs": []}` if no PR was opened (regardless of reason). The calling
workflow uses `has_e2e` and `has_api` to decide which on-demand verification
workflows to trigger.
