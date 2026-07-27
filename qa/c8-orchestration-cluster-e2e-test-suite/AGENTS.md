# C8 Orchestration Cluster E2E Test Suite

Playwright + TypeScript end-to-end tests for the Camunda 8 orchestration cluster
(Zeebe/Operate/Tasklist/Identity). Lives inside the `camunda/camunda` monorepo at
`qa/c8-orchestration-cluster-e2e-test-suite/`. PRs follow the `camunda/camunda`
contribution workflow; cross-link related Helm or cross-component E2E PRs when applicable.

**Scope:** orchestration-cluster apps only — Zeebe, Operate, Tasklist, Identity.
For multi-app journeys that span Modeler, Optimize, Connectors, or Console (SaaS/SM/C8Run),
use the sibling [`c8-cross-component-e2e-tests`](https://github.com/camunda/c8-cross-component-e2e-tests)
repository instead.

> **Exception — infrastructure smoke checks:** basic startup/accessibility checks for a
> component bundled in this suite's Docker Compose stack (e.g. verifying Optimize starts and
> is reachable) are in scope here, even though they exercise only that component. Deeper
> Optimize/Connectors/Modeler _feature_ coverage still belongs in `c8-cross-component-e2e-tests`.

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
- Every test must involve at least one core component (Operate, Tasklist, Identity, Zeebe),
  except infrastructure smoke checks that verify a component bundled in the suite's Docker
  Compose stack starts and is accessible (e.g. the Optimize startup check).
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

## Product-Bug Escalation

Read this whenever you suspect a failure is a **product regression**, not a test defect. A
**product-bug** verdict means: **file (or reuse) a tracking issue against the owning component, then
SKIP the failing test with a bug-linked annotation and open ONE PR carrying that skip.** Skipping is
the only sanctioned way to make a confirmed product regression green — you still must NOT mask it
with a viewport resize, timeout bump, selector tweak, or weakened assertion. **Skipping is allowed
ONLY here** (a confirmed product bug that passes all three gates AND has a filed/linked ticket); for
flakiness, a can't-determine failure, or any other reason, `test.skip()` / `test.fixme()` /
`test.only` remain **absolutely forbidden**.

You may reach a product-bug verdict ONLY after passing **all three gates below, in order**. If any
gate fails, fall back to the normal test-side fix. Product-bug is not an escape hatch for a test you
find hard to fix, and you must never mask a regression with a viewport resize, a timeout bump, or a
weakened assertion (e.g. an Operate variables-scrolling regression must NOT be "fixed" by resizing
the window).

**Gate A — Rule out flakiness.** The failure must be deterministic. You cannot re-run, so use
evidence: every retry attempt in `results.json` failed (a `failed → passed` retry is flakiness — fix
with a wait/retry); and the failure reproduces across both tasklist modes / DBs that share the flow,
or has failed on consecutive nights. If it looks intermittent → flakiness fix, not a product bug.

**Gate B — Pin it to a recent product change.** Identify the owning module (table below) and find
the breaking change. You are already inside the `camunda/camunda` checkout (on `${TARGET_REF}`), so
run `git` from the current directory — do not reference `$ws_dir` (it is not set in your shell):

```bash
git log --since=<YYYY-MM-DD> --oneline -- <module-path>   # e.g. operate/ identity/
# or, for a precise surface:
gh search code --repo camunda/camunda "<aria-label / data-testid / endpoint>" --limit 5 --json path
```

Bound the window with when the test first went red, and inspect the **entire** pass→fail commit
range for a functional product change. **The CI checkout is a shallow clone (`fetch-depth: 1`), so
local `git log`/`git blame` over history returns nothing** — this is exactly why past triage gave up
with "git history unavailable". Pin via the GitHub API (server-side history, no clone needed) rather
than local git:

```bash
# Preferred — works on the shallow CI checkout:
gh api "repos/camunda/camunda/compare/<last-green-sha>...<first-red-sha>" \
  --jq '.commits[] | "\(.sha[0:10])  \(.commit.message | split("\n")[0])"'
# Narrow to a module's files, or find the surface that changed:
gh api "repos/camunda/camunda/compare/<last-green-sha>...<first-red-sha>" \
  --jq '.files[].filename' | grep -E '^operate/|^identity/|^tasklist/'
gh search code --repo camunda/camunda "<aria-label / data-testid / endpoint>" --limit 5 --json path
# Fallback only if you must use local git: unshallow first (slow on this monorepo):
#   git fetch --unshallow   # or: git fetch --deepen=200
```

Get the two boundary SHAs from the nightly run history (the `headSha` of the last green run and the
first red run for this test — see the per-test `✓`/`✘` markers in the run logs).

**Pinning a specific commit (or a documented behavior change) is REQUIRED for a product-bug verdict
— this is the confidence gate. A `suspect_commit` of `"not pinned"` is no longer acceptable.** A real
regression has a cause you can point at. If the pass→fail window contains **no functional product
change** — e.g. only `deps:` dependency bumps, CI, or docs commits — then there is nothing to pin and
the green→red flip is almost certainly environmental / load-driven, not a product defect.
"Deterministic + test looks correct" (Gates A and C) is **not** sufficient on its own: without a
pinned change, confidence is too low to charge the product team with a bug. When you cannot pin the
change, do NOT file a bug — instead, in order:

1. **Harden the test** for the actual failure mode and ship that fix — no bug, no skip. Model it on
   any retry/backoff budget the suite already documents for that surface (e.g. re-drive a command
   through a documented eventual-consistency window, add a proper wait, mirror an existing request
   helper's retry). This is the default outcome for an unpinnable green→red flip.
2. **If the test is already hardened** as much as it reasonably can be — the failure mode is already
   retried/waited and it still fails with no pinnable cause — do NOT file a speculative bug and do
   NOT `test.skip()`. Escalate for **manual / human intervention**: write `{"prs": []}` with a
   `manual_intervention` note in the manifest (root cause unknown, what you ruled out, what hardening
   already exists) and flag it in the Slack thread for a human to investigate.

Only proceed to file a product bug when Gate B is satisfied (change pinned) AND Gates A and C hold.
Never mask the failure with a viewport/timeout/selector tweak or a weakened assertion, and never do a
bare `test.skip()` without a filed/linked ticket. Only re-examine for a test-side cause if Gate A or
Gate C is genuinely in doubt.

**Gate C — Confirm the test is still correct.** The assertion must still match intended behavior,
the selector must target a real element, and the test must not be stale. If the test itself is wrong
→ that is a test fix (Step 4), not a product bug. **For any API test that failed with a non-happy
response (unexpected status, error body, or wrong payload), "still correct" also requires the request
the test SENT to conform to the API agreement** — diff the request (endpoint, method, body shape,
field types, enum casing, params) against `zeebe/gateway-protocol/src/main/proto/v2/*.yaml`. If the
request conforms and the response is still wrong, it is a genuine product bug (escalate; do not change
the test); if the request is malformed or the test is badly planned, fix the test. **If the test uses
the conventional shape that sibling filters use but the contract itself is the outlier and no request
shape works, the contract/spec is the bug — escalate; do NOT rewrite the test to match a broken
agreement (#56636 is exactly this case).** A backend-agnostic failure is, by itself, evidence of
neither a product bug nor a test bug.

### Owning-component routing

Orchestration-suite failures almost always trace to a module inside this same repo (`camunda/camunda`):

|           Failure surface           |           Issue repo            |                 Module path                 |
|-------------------------------------|---------------------------------|---------------------------------------------|
| Operate                             | `camunda/camunda`               | `operate/`                                  |
| Tasklist                            | `camunda/camunda`               | `tasklist/`                                 |
| Identity / authorization / RBA      | `camunda/camunda`               | `identity/`, `authentication/`, `security/` |
| Zeebe / engine / gateway / REST API | `camunda/camunda`               | `zeebe/`, `gateways/`, `service/`           |
| Optimize                            | `camunda/camunda`               | `optimize/`                                 |
| c8Run setup / packaging             | `camunda/camunda`               | `c8run/`                                    |
| Helm chart / deploy config          | `camunda/camunda-platform-helm` | `charts/`                                   |

### Filing the bug ticket (dedupe FIRST)

> **Token:** use the default `GH_TOKEN` (the qa-processes App token) for ALL `gh` calls FIRST —
> filing/searching issues in `camunda/camunda` and Gate B lookups; qa-processes generally already
> has the access. ONLY if a call fails (e.g. filing in a different repo such as
> `camunda/camunda-platform-helm`) retry the SAME command once with the fallback PAT
> `GH_TOKEN="$GH_PAT" gh ...`. Always qa-processes first; the PAT is a backup.

1. **Compute the fingerprint** — `sha256` of `<version>::<file>::<test_name>`, first 8 chars (MUST
   match the triage dispatcher exactly so it can suppress re-dispatch):

   ```bash
   FP=$(printf '%s::%s::%s' "<version>" "<file>" "<test_name>" | sha256sum | cut -c1-8)
   ```

   **Search for an existing issue** (the marker is globally unique):

   ```bash
   gh search issues "nightly-product-bug fp=${FP} is:issue" --owner camunda --state all \
     --limit 1 --json number,url,state,repository --jq '.[0]'
   ```

   - Open issue exists → reuse it (comment with this nightly run); do NOT file a new one.
   - Closed issue for the same FP → reopen (`gh issue reopen`) and comment, then reuse its URL.
   - **Also check for a human-filed bug that predates this automation** (no fingerprint marker):
     `gh search issues "<key symptom words> is:issue" --owner camunda --state open --json number,title,url,repository`
     and scan titles for the same regression (e.g. #55864 "Variable list is no longer scrollable").
     If you find a clear match, **reuse it** instead of filing a duplicate: comment linking this
     nightly run, then **append** the fingerprint line to its body so future runs dedupe and the
     dispatcher suppresses it. Preserve the existing body and use real newlines (a literal `\n` in
     `--body` is written verbatim, not a newline):

     ```bash
     body=$(gh issue view <n> --repo <owner>/<repo> --json body --jq .body)
     printf '%s\n\nFingerprint: nightly-product-bug fp=%s\n' "$body" "${FP}" \
       | gh issue edit <n> --repo <owner>/<repo> --body-file -
     ```

     Put its URL in `fix-meta.json`.

2. **File the issue** when none exists. You MAY use the repo's `create-issue` skill (bug template +
   component label), but the body MUST contain the fingerprint line below so dedupe works:

   ```bash
   gh issue create --repo camunda/camunda \
     --title "<module>: <one-line symptom> (nightly <version>)" \
     --label "kind/bug" --label "nightly-detected" \
     --body "$(cat <<'BODY'
   Detected by orchestration-cluster nightly triage. The failing test is **correct** and the failure
   is **not flaky** — it traces to a recent product change.

   - **Failing test:** `<file>` › `<test_name>` (<test_type>, <version>)
   - **Symptom:** <what the screenshot / error shows>
   - **Suspected change:** <commit sha + subject, or PR #, or docs reference>
   - **Nightly run(s):** <e2e / api run URLs>
   - **Triage run:** <triage_run_url>

   Fingerprint: nightly-product-bug fp=<FP>
   BODY
   )"
   ```

   The `Fingerprint:` line is **mandatory**. `kind/bug` / `nightly-detected` are defaults — drop a
   label the repo rejects rather than failing.

3. **Skip the failing test with a bug-linked annotation**, static `test.skip(...)` form, with the
   annotation comment on the line directly above — exact format, no deviation:

   ```ts
   // Skipped due to bug #<number>: <issue-url>
   test.skip('<exact test title>', async ({ ... }) => {
   ```

   Skip only the failing test (not the whole `describe`) unless every test in the block shares the
   bug. Lint the changed file.

4. **Open ONE PR** with the skip(s): `test:` type (commitlint rejects `fix:` for test-only changes),
   title like `test: skip <spec> pending bug #<number>`, body linking the issue + nightly/triage
   runs. All product-bug skips for this dispatch go in the same branch + PR.

5. Record the verdict in `/tmp/fix-meta.json` (see the product-bug schema in **Result manifest**) —
   the skip PR goes in `prs`, the issue(s) in `product_bugs` — and stop.

---

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
- **Any unexpected / non-happy API response** (an unexpected status — 4xx *or*
  5xx — an error body, or a wrong/empty payload) — before deciding, check the
  request the test SENT against both the API agreement AND the established
  convention. Diff the request (endpoint, method, body shape, field types, enum
  values/casing, params) against the OpenAPI/proto contract in
  `zeebe/gateway-protocol/src/main/proto/v2/*.yaml`, compare with how sibling
  filters are queried (they share conventions — e.g. every enum filter is a single
  `field: {$in: [...]}` property), and re-read what the test verifies. Then decide:
  - **Request conforms and matches the test's intent, but the response is still
    wrong** → the product is misbehaving → **product bug** (Step 4); do NOT change
    the test to pass.
  - **Request is malformed / the test is badly planned** (wrong body shape, field
    type, enum casing, or endpoint) → **fix the test**, even when it looks
    well-formed.
  - **The contract/spec ITSELF is the bug** — the test uses the conventional shape
    that every sibling filter uses, yet the contract is the outlier and NO request
    shape works → escalate as a **product/spec bug** (Step 4). Do NOT rewrite the
    test to match a broken agreement — that masks the bug. "Contradicts the
    contract" is a test fix ONLY when the contract agrees with the convention; if
    the contract is the outlier, the contract is wrong.

  A backend-agnostic failure (same on ES and RDBMS) is by itself evidence of
  neither a product nor a test bug — a malformed request AND a broken contract both
  fail on every backend; decide by conformance to the *convention*, not the literal
  contract alone. Worked example (#56636): `eventTypes` is declared as an *array*
  while every other enum filter is a single `{$in: [...]}` property, so both
  `eventTypes: {$in: [...]}` (convention → "cannot be parsed") and
  `eventTypes: [{$in: [...]}]` (matches the array spec → "Type definition error")
  fail → the *spec* is the outlier → **product/spec bug: skip & escalate**, NOT a
  test fix.

**Step 4 — Decide: fix or stop.**

If a minimal test-side fix exists, apply it (Step 5 below).

If the failure is a **product regression** (e.g., a 500 from the server, a panel that never renders
despite a correct backend response, an authorization regression, lost scrolling) and no test-side
change is reasonable, follow **`## Product-Bug Escalation`**: pass Gate A (not flaky), Gate B (pin
the recent product change), Gate C (test still correct), then **file/reuse a tracking issue** against
the owning module, **skip** the failing test with the `// Skipped due to bug #<number>: <url>`
annotation, and open ONE skip PR (the skip PR goes in `prs`, the issue in `product_bugs`). Never mask
the regression with a viewport / timeout / selector tweak or a weakened assertion **instead of**
skipping.

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

- **Allowed tools:** `gh`, `git`, `grep`, `rg`, `cat`, `find`, `jq`, `sed`, `awk`, `unzip`, `npx prettier`, `npx eslint`, `npm run responses:regenerate`.
- **Forbidden:** `make`, `mvn`, `./mvnw`, `docker`, `kubectl`, `helm`, `npm install`, `npm run build`, `npm run test`, `npx playwright test`. The fix agent does **not** execute tests — it fixes from artifact evidence only. Verification is delegated to the on-demand workflows triggered by the calling workflow.
- **Skipping is forbidden EXCEPT for a confirmed product bug:** the ONLY sanctioned use of `test.skip()` is a product regression that passes all three gates in `## Product-Bug Escalation` and has a filed/linked ticket — there you skip with the mandatory `// Skipped due to bug #<number>: <url>` annotation and open one skip PR. For flakiness, can't-determine, or any other reason, `test.skip()` / `test.fixme()` / `test.only` remain **absolutely forbidden**. A bare `{"prs":[]}` is sanctioned ONLY for the Gate B manual-intervention case (an unpinnable green→red flip on a test that is already hardened) and must carry a `manual_intervention` note; never leave `{"prs":[]}` with no note and no issue filed for any other reason.
- **Never edit `json-body-assertions/_generated/responses.json` by hand.** This file is auto-generated. If an API response changes, regenerate it with `npm run responses:regenerate` and commit the result. Manual edits will be overwritten and produce misleading diffs.
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
      "has_api": false,
      "root_cause": "One-sentence explanation of why the test was failing.",
      "fix": "One-sentence description of what was changed and why it resolves the failure.",
      "tests_fixed": [
        {"file": "tests/api/v2/...", "test_name": "...", "test_type": "api"}
      ]
    }
  ]
}
```

All six fields — `number`, `owner`, `repo`, `branch`, `has_e2e`, `has_api` — are
**mandatory**. For all PRs in this repo: `"owner": "camunda", "repo": "camunda"`.
`root_cause` and `fix` are **strongly recommended** — they are surfaced directly
in the GitHub job summary so reviewers understand the agent's decision without
reading the full agent log.
Extra fields (e.g. `url`, `title`, `tests_fixed`) are allowed but do not replace
these six. **Never omit `owner` or `repo`** — the Slack step uses them to build
the PR link and will produce `github.com/null/null/pull/<n>` if they are absent.

**`branch`** must never be `null`. Set it to the exact `headRefName` of the PR:

```bash
gh pr view <number> --repo camunda/camunda --json headRefName --jq '.headRefName'
```

**`has_e2e` / `has_api`** — set based on the `test_type` of every test you fixed:
- Any fixed test with `test_type: "e2e"` → `"has_e2e": true`
- Any fixed test with `test_type: "api"` → `"has_api": true`
- Both can be `true` when a PR covers mixed failures.

The calling workflow uses these flags to determine scope, but only one
on-demand verification run is triggered: `c8-orchestration-cluster-e2e-tests-on-demand.yml`
(covers both E2E and API tests). **If `has_e2e` and `has_api` are both `false`,
no verification run is triggered and the fix is never validated automatically.**

Use `{"prs": []}` if no PR was opened (regardless of reason).

A confirmed product bug always lands as a skip PR, so `prs` carries that PR (set `has_e2e`/`has_api`
from the skipped test types so the skip is verified) and `product_bugs` lists one object per
filed/reused issue (a dispatch may yield several bugs):

```json
{
  "prs": [{"number": 1234, "owner": "camunda", "repo": "camunda", "branch": "fix/nightly-8.10-skip-operate-vars", "has_e2e": true, "has_api": false}],
  "category": "product-bug",
  "product_bugs": [
    {
      "repo": "camunda/camunda",
      "component": "operate",
      "issue_url": "https://github.com/camunda/camunda/issues/55864",
      "issue_number": 55864,
      "fingerprint": "<FP>",
      "root_cause": "One sentence: which product change broke which flow.",
      "suspect_commit": "<sha + subject, or PR #, or docs reference>",
      "skipped_tests": ["<file> › <test_name>"]
    }
  ]
}
```

`category`, the skip PR in `prs`, and a non-empty `product_bugs` are all required for this verdict.
`suspect_commit` is surfaced directly in the Slack thread. The triage dispatcher reads each issue's
fingerprint marker to suppress re-dispatch while the issue is open; once the skip PR merges the test
no longer runs, and when the issue is later closed the skip should be removed so the test runs again.

## Workflow-Level Failure Fix Agent

This section is read by the fix agent when `/tmp/test_specs.json` contains an empty array — the nightly run failed without producing test results, or failed in a non-test step.

The run IDs are in env vars: `E2E_RUN_ID`, `API_ES_RUN_ID`, `API_RDBMS_RUN_ID` (any may be `""`). `TRIAGE_RUN_URL` is the triage run URL for cross-linking.

### Mental model

A CI run is a chain of steps. Every step runs a command defined by a file in **this** repo — a workflow YAML, a shell script, a config file, a compose file, or a test. A failure means one of those files produced behaviour that ended the run.

Your job is to find that file and change it so the run reaches a **correct conclusion**: either it passes, or it fails for a real product reason that surfaces as a *test result* — never as a workflow crash, and never with a "please re-run" recommendation.

There is no catalogue of known failures to match against. Any step or any job can fail for any reason. You diagnose from first principles every time, using the full repository, which is entirely accessible to you.

### The loop

**1 — Enumerate every failed job and its failed step(s):**

```bash
for run_id in $E2E_RUN_ID $API_ES_RUN_ID $API_RDBMS_RUN_ID; do
  [ -z "$run_id" ] && continue
  echo "=== Run $run_id ==="
  gh run view "$run_id" --repo camunda/camunda --json jobs \
    --jq '.jobs[] | select(.conclusion == "failure") | {
      job: .name,
      failed_steps: [.steps[] | select(.conclusion == "failure") | .name]
    }'
done
```

**2 — Read the failing step's logs to get the exact error:**

```bash
gh run view <run_id> --repo camunda/camunda --log-failed 2>/dev/null | head -300
```

The root cause is almost always visible in the first 300 lines. Read the actual error text — do not assume.

**3 — Locate the file that owns the failing step.** The step `name` from step 1 is a literal key in a workflow YAML. Find it, then follow it to the real source:

```bash
# Find which workflow defines the failed step
grep -rn "<failed step name>" .github/workflows/

# If the step runs a script, open the script. If it runs a command,
# find the config that command reads (compose file, package.json script,
# trcli invocation, mvn goal, etc.) and open that.
```

Keep following until you reach the concrete file whose contents determine whether that step passes — the YAML, the script, the config, the compose file, or the test.

**4 — Classify the true cause, then fix it in that file.** Every failure is one of three kinds, and each has a definite fix shape:

- **The step's own logic is wrong** — a bug in the workflow, script, config, compose file, or test. Fix the logic.
- **The step depends on something flaky or external** — a registry, a network call, a remote API, a download. Encode resilience *in the file*: add a retry with backoff, pin or cache the dependency, or turn a cryptic crash into a clear, actionable failure. Recommending a human re-run is **not** a fix — the resilience must live in the repo so the next run survives the same blip on its own.
- **A real product or test defect crashed the run instead of being reported** — the run died before the test framework could record the failure. Fix it so the defect surfaces as a normal test result, never as a workflow-level crash.

Make the **minimal** change that addresses the cause. Do not refactor surrounding code, suppress the error, or widen the scope.

> **Illustration of the loop (not a lookup row):** Suppose step 1 reports the failed step is `Start Camunda` and step 2 shows `context deadline exceeded` pulling an image. Step 3 greps the workflows, finds the step in a reusable workflow, and sees it runs `docker compose up` once. Step 4 classifies this as "depends on something flaky/external" → the fix is to wrap the pull in a retry-with-backoff loop *in that workflow file* so a transient registry blip no longer fails the run. You would reach the same shape of answer for a flaky `npm ci`, a flaky artifact download, or a flaky DB container start — by running the same loop, not by recognising "Docker".

**5 — Open a PR.** Use `ci:` if only `.github/workflows/` files changed; `test:` if only test files changed; `fix:` if application code changed. Never use `fix:` for test-only changes — commitlint rejects it. Cross-link `TRIAGE_RUN_URL` in the PR body.

Write to `/tmp/fix-meta.json`:

```json
{"prs": [{"number": 123, "owner": "camunda", "repo": "camunda", "branch": "ci/...", "has_e2e": false, "has_api": false}], "category": "workflow-fix"}
```

### When — and only when — you cannot fix

`{"prs": [], "category": "not-determined"}` is a last resort, expected to be extremely rare because the whole repo is in scope. To use it you must be able to state all three: the failed step, the file that owns it, and a concrete reason why **no edit to that file or anything it touches** could change the outcome.

Realistically that is limited to: the runner host itself died (out of memory or disk on the GitHub Actions runner), or a fully-down external service with no surface in our code to add a retry or fallback against. Note that even most "external service was down" cases have a fix — adding backoff so a brief outage no longer fails the run — so reach for `not-determined` only after confirming there is genuinely nothing in the repo to change.

### Constraints

- **Never recommend re-running the workflow as the outcome** — if flakiness is the cause, the resilience goes into the file, not into a human instruction.
- **`continue-on-error: true` is absolutely forbidden — with no exceptions and no rationalisations.** This includes post-test reporting steps (TestRail, artifact upload, Slack notification). The reasoning "it is only reporting, not a gate" is exactly the rationalisation that must be rejected: if a post-test step fails, that failure is a defect in our code or configuration that deserves a real fix. `continue-on-error` silences the failure rather than fixing it, which means the same bug runs again tomorrow and the day after.
  - **TestRail `add_case` failure** → the step fails because something in *our* code caused trcli to error (e.g. a test case title whose derived `custom_automation_id` exceeds 250 characters). Find the offending test title in the spec files and shorten it. That is the fix.
  - **TestRail auth / network failure** → add a retry around the trcli call, or fix the credential configuration. Do not silence.
  - **Any other post-test step** → find what our code does wrong and fix it. If you genuinely cannot find any code fix after thorough investigation, write `not-determined` — but `continue-on-error` is never a valid alternative.
- **No skipping** — same absolute no-skip / no-fixme rule as the Nightly Fix Agent.
- **`.github/workflows/` is always in scope** — the repo "Ask first" constraint applies to application libraries (`webapps-common/`, `webapp/client/`, `security/`), not to CI workflow files.
- **Minimal diff** — fix only what is broken; no refactoring, no dependency bumps, no unrelated edits.
- **Allowed tools**: `gh`, `git`, `grep`, `rg`, `cat`, `find`, `jq`, `sed`, `awk`, `unzip`
- **Forbidden**: `make`, `helm`, `kubectl`, `npm run build`, `go test`, any deploy command

