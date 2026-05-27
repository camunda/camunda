# C8 Orchestration Cluster E2E Test Suite

Playwright + TypeScript end-to-end tests for the Camunda 8 orchestration cluster
(Zeebe/Operate/Tasklist/Identity). Lives inside the `camunda/camunda` monorepo at
`qa/c8-orchestration-cluster-e2e-test-suite/`. PRs must follow the `camunda/camunda`
contribution workflow and be cross-linked to any related Helm or cross-component E2E PRs.

## Tech Stack

- **Framework**: `@playwright/test` ^1.51.0
- **Language**: TypeScript 5.9
- **Pattern**: Page Object Model (POM) with Playwright fixtures
- **API client**: `@camunda8/sdk`
- **Linting**: ESLint + Prettier (enforced via `npm run lint`)

## Directory Structure

```
tests/
  api/v2/          # REST API v2 tests (stateful; run as api-tests project)
  common-flows/    # Cross-component flow tests
  identity/        # Identity UI tests
  operate/         # Operate UI tests
  tasklist/        # Tasklist UI tests
v2-stateless-tests/
  tests/request-validation/  # Stateless request-validation tests (no cluster state)
pages/             # Page Object Model classes (one file per page)
utils/             # Helpers: zeebeClient, waitForAssertion, requestHelpers, cleanup utils, etc.
resources/         # Test data: BPMN files, forms, connector templates
config/
  docker-compose.yml   # Local Elasticsearch + Camunda stack
  application.yaml     # RDBMS config template (copy to dist before starting)
  envs/                # Per-environment .env templates
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

## Running Tests

```bash
# All UI tests (headless, reads .env)
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

## Playwright Projects

| Project | Test match | Notes |
|---|---|---|
| `chromium` | `tests/**/*.spec.ts` | Main UI tests; excludes task-panel and api |
| `chromium-subset` | `tests/tasklist/task-panel.spec.ts` | Teardown of chromium; sequential (`workers: 1`) |
| `firefox` / `msedge` | Same as chromium | Cross-browser variants |
| `api-tests` | `tests/api/v2/**/*.spec.ts` | Stateful REST API tests; excludes clock/metrics/audit-log/job-stats/optimize |
| `api-tests-subset` | clock, metrics, audit-log, job-stats | Sequential teardown of api-tests |
| `tasklist-e2e` | `tests/tasklist/*.spec.ts` | Tasklist-scoped run |
| `identity-e2e` | `tests/identity/*.spec.ts` | Identity-scoped run |
| `operate-e2e` | `tests/operate/*.spec.ts` | Operate-scoped run |
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
# 1. Build distribution
./mvnw install -Dquickly -T1C -PskipFrontendBuild   # from monorepo root

# 2. Unpack
cd dist/target && tar -xzf camunda-orchestration-cluster-*.tar.gz

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

Never use `test.skip()` or `test.fixme()` — fix the test or document a pre-existing failure in the PR description.
