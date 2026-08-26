# Reproduction Environment Reference

Pick the method based on the bug type classified in Phase 1
(see [`triage-guidelines.md`](triage-guidelines.md#bug-type-classification-drives-phase-3s-reproduction-method)).
All methods share one rule: **tear down before moving on**, success or failure.

## c8run — shared runtime for all methods

The goal is a running Camunda instance with H2 storage (no Docker) — not necessarily `c8run`'s own
binary. **Default to building the actual Camunda distribution from this repo's source and running
it directly**, using c8run's own config as a drop-in overlay. Reach for c8run's packaged binary
only as a fallback (see [below](#fallback-c8runs-own-packaged-binary-rarely-needed)) — its
packaging step (`packager package`) always needs internal Nexus credentials
(`JAVA_ARTIFACTS_USER`/`PASSWORD` in `c8run/.env`, per `c8run/docs/local-development.md`), even
when the Camunda code itself was built locally, because it also fetches the bundled JRE and
Connectors artifacts from Nexus. Building the distribution with this repo's own `./mvnw`, by
contrast, needs nothing beyond what every other module in this repo already needs.

This also has a real advantage for Phase 5: it runs against the **current source tree**, including
any candidate fix already applied to the working copy. Editing the code, rebuilding, and rerunning
is the closest thing to an actual simulation this skill can do — a nightly CI artifact is always
frozen at last night's `main`, so it can't reflect an uncommitted fix at all.

### Build the distribution

A full package build is expensive (minutes — `dist` pulls in the whole product: Zeebe, gateway,
Tasklist, Operate, Identity), so check first whether one already exists and is fresh enough:

```bash
ls -la dist/target/camunda-zeebe/bin/camunda 2>/dev/null
```

If it's missing, or you've since edited code that should be reflected (e.g. a candidate fix from
Phase 5), (re)build:

```bash
./mvnw -B -T1C -DskipTests -DskipChecks -Dflatten.skip=true -Dskip.fe.build=false -DskipOptimize package
```

This is a full-repo build by design, not a shortcut around the repo's "never full-repo build for
single-module work" rule — `dist` packaging genuinely requires the whole product, and this is the
same build CI's `camunda-dist-build` job runs. Produces `dist/target/camunda-zeebe/` (an exploded
distribution with `bin/camunda`). Add `clean` if an existing `dist/target/` looks stale or broken
(see the UI-serving check below).

### Startup

Run the distribution's own launcher, layering c8run's H2/no-auth-friction config on top via Spring
Boot's config-import mechanism — this gets the same zero-Docker, zero-auth-friction setup c8run
provides, without going through c8run's binary at all:

```bash
cd dist/target/camunda-zeebe
REPO_ROOT=$(git rev-parse --show-toplevel)
nohup ./bin/camunda \
  --spring.config.additional-location="file:${REPO_ROOT}/c8run/configuration/application.yaml" \
  > /tmp/camunda-repro.log 2>&1 &
echo $! > /tmp/camunda-repro.pid
```

Default endpoints:

| Endpoint            | URL                                    |
|----------------------|-----------------------------------------|
| REST API / Web       | `http://localhost:8080`                |
| Tasklist             | `http://localhost:8080/tasklist`        |
| Operate              | `http://localhost:8080/operate`         |
| Health check         | `http://localhost:9600/actuator/health` |
| Zeebe gRPC           | `localhost:26500`                       |

Default credentials: `demo` / `demo` (from `c8run/configuration/application.yaml`; the overlay also
disables API authorization for local testing convenience — matches what c8run itself does).

Wait for healthy — this typically takes ~5–15 seconds (no compilation, just process startup):

```bash
attempt=0
until curl -sf http://localhost:9600/actuator/health | grep -q '"status":"UP"'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 24 ]; then   # ~2 minutes at 5s intervals
    break
  fi
  sleep 5
done

if ! curl -sf http://localhost:9600/actuator/health | grep -q '"status":"UP"'; then
  echo "camunda did not become healthy within 2 minutes" >&2
fi
```

If it never becomes healthy (the loop above gives up after ~2 minutes): mark Phase 3 ❌ in the
issue comment (per `SKILL.md`), tear down (below) anyway, and fall back to code-analysis-only for
Phases 4–5. This is an environment failure, not evidence the bug doesn't reproduce.

**For UI bugs specifically**, also confirm Tasklist/Operate actually render before trusting the
environment — health can report UP while the web UI itself 500s on a stale or partial build:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/tasklist/
```

Expect `200` or a redirect to login, not `500`. A 500 here (e.g. a Thymeleaf
`TemplateInputException: Error resolving template [tasklist/index]`) means the existing
`dist/target/` build is stale/incomplete — rebuild with `clean` before trusting it for a UI repro.

### Teardown — always run this

```bash
kill "$(cat /tmp/camunda-repro.pid)" 2>/dev/null || pkill -f 'bin/camunda'
rm -f /tmp/camunda-repro.pid
```

Run this immediately after the reproduction attempt concludes (Phase 3), not at the end of the
whole investigation — if a later phase fails or the run is interrupted, the environment must
already be down. Leave `dist/target/camunda-zeebe/` itself in place — it's ordinary (gitignored)
Maven build output, and keeping it speeds up the next investigation as long as it's rebuilt when
source changes.

### Optional: pre-built nightly artifact (faster, but frozen at last night's `main`)

If you specifically don't need to reflect any local code change — purely confirming a bug that's
already merged to `main` — the nightly `c8run-build.yaml` workflow publishes a ready-to-run,
fully self-contained package (bundled JRE; no Nexus needed *to use it*, only to build it) as a
short-lived (`retention-days: 1`) CI artifact. This avoids the local build time entirely:

```bash
WORKDIR=$(mktemp -d)
cd "$WORKDIR"

case "$(uname -s)-$(uname -m)" in
  Linux-*)          ARTIFACT=camunda8-run-build-ubuntu-latest ;;
  Darwin-arm64)     ARTIFACT=camunda8-run-build-macos-latest ;;
  Darwin-x86_64)    ARTIFACT=camunda8-run-build-macos-15-intel ;;
  *)                echo "unsupported platform for the pre-built artifact" >&2; exit 1 ;;
esac

# retention-days: 1 — always pull the latest successful run, never an older one
RUN_ID=$(gh run list --repo camunda/camunda --workflow c8run-build.yaml --branch main \
  --status success --limit 1 --json databaseId --jq '.[0].databaseId')

gh run download "$RUN_ID" --repo camunda/camunda -n "$ARTIFACT" -D ./dl
unzip -q ./dl/camunda8-run-*.zip -d ./extracted
cd ./extracted/c8run-*/
chmod +x ./c8run
./c8run start --disable-connectors   # start/stop must run from this same directory (pidfile-based)
# ...
./c8run stop
rm -rf "$WORKDIR"
```

Prefer the local build above whenever Phase 5 needs to validate an actual code change — this path
can't reflect one.

### Fallback: c8run's own packaged binary (rarely needed)

Only reach for this if the bug is specifically about c8run itself (`component/c8run`) or needs its
process-management (PID files, cross-platform packaging) rather than just a running Camunda
instance. Requires Nexus creds — see `c8run/docs/local-development.md`. **Check for the creds
before starting a multi-minute build**, not after it fails partway through:

```bash
grep -q '^JAVA_ARTIFACTS_USER=.\+' c8run/.env 2>/dev/null && grep -q '^JAVA_ARTIFACTS_PASSWORD=.\+' c8run/.env 2>/dev/null \
  || { echo "no Nexus creds configured — use the local-build or nightly-artifact method instead"; exit 1; }

cd c8run
go build -o c8run ./cmd/c8run
go build -o packager ./cmd/packager
./packager package
./start.sh   # produces and runs camunda8-run-*, same layout as the nightly artifact above
```

## Method: Playwright (UI bugs)

The e2e suite lives at `qa/c8-orchestration-cluster-e2e-test-suite/`. It expects `c8run` (or an
equivalent stack) already running at `http://localhost:8080` — that's the suite's default
`baseURL` (`PLAYWRIGHT_BASE_URL` env var overrides it).

```bash
cd qa/c8-orchestration-cluster-e2e-test-suite
npm ci                        # first run only
npx playwright install chromium   # first run only — other browsers rarely needed for a repro
export CORE_APPLICATION_URL="http://localhost:8080"   # required by pages/UtilitiesPage.ts's navigateToApp; without it every test 404s on "/undefined/<app>/login" regardless of what's actually being tested
```

`CORE_APPLICATION_URL` is set by CI (`.github/workflows/c8-orchestration-cluster-reusable-e2e-tests.yml`)
but isn't in any checked-in `.env` — easy to miss locally, and the resulting failure (login 404s on
a URL containing the literal string `undefined`) looks nothing like the bug you're trying to
reproduce. If a UI test fails immediately on login rather than on the assertion the issue is about,
check this env var before concluding anything about the actual bug.

Reuse existing page objects from `pages/` (e.g. `pages/TaskPanelPage.ts`, `pages/LoginPage.ts`,
`utils/zeebeClient.ts` for deploying a process/creating an instance from the test itself) rather
than driving the browser with raw selectors — follow the pattern in the file the issue points at,
or in a sibling spec under `tests/<app>/` for the same page.

Scope to exactly the test the issue names with `-g "<exact test title>"` — don't run the whole spec
file. Files like `user-task-permission-management.spec.ts` hold ~10 unrelated tests each with their
own user/authorization setup; running all of them repeated is pure waste when only one is under
investigation:

```bash
npx playwright test tests/tasklist/user-task-permission-management.spec.ts \
  --project=chromium -g "should not display the assigned task without READ permission"
```

For a **flaky test** bug, repeat it to establish a failure rate — but stop as soon as the pattern is
clear, don't run a large fixed count regardless of outcome. `--repeat-each=3 --retries=0` is enough
to tell "fails every time" (already 3/3 — this is likely not a race at all, treat it as a
deterministic bug) from "genuinely intermittent" (a mix of pass/fail). Only extend beyond 3 if the
first 3 are inconclusive (e.g. 2/3 one way):

```bash
npx playwright test tests/tasklist/user-task-permission-management.spec.ts \
  --project=chromium -g "should not display the assigned task without READ permission" \
  --repeat-each=3 --retries=0
```

`--retries=0` matters here: the suite's default `retries: 1` will silently re-run and can mask a
real failure as a pass in the summary — you want each repeat's true outcome, not a retried one.

Screenshots (`screenshot: 'only-on-failure'` is already configured) and traces land under
`test-results/` locally — these aren't reachable from the issue comment (no `gh api` endpoint
uploads an image and returns an embeddable URL, and a local path means nothing to an issue
reader), so don't reference either as if they were. Instead, describe the failure precisely in
text: quote the actual-vs-expected assertion output from the test's console log, and mention
that the full screenshot/trace is available locally under `test-results/` for anyone who checks
out the branch and re-runs it.

## Method: Generated API script (API/behavior bugs)

For a bug reported purely via request/response bodies, reproduce with a short throwaway script
against the running `c8run` REST API — don't write a full Playwright spec for a pure API repro.

```bash
curl -s -u demo:demo -X POST http://localhost:8080/v2/<endpoint> \
  -H 'Content-Type: application/json' \
  -d '<body from the issue>'
```

If the repro needs a deployed process or a running instance first, reuse
`utils/zeebeClient.ts` from the e2e suite (`deploy`, `createSingleInstance`) rather than
hand-rolling gRPC calls — it already handles auth and connection setup consistent with how the rest
of the suite talks to `c8run`.

Compare the actual response/behavior against what the issue says was expected — this comparison
*is* the reproduction result, not just "ran the request."

## Method: Multi-backend query/aggregation bugs (RDBMS/ES/OS parity — Hard rule 8)

When Phase 2 flags the bug as shared query/aggregation/statistics logic reachable through more than
one storage backend, reproduce and validate against **every** backend via the `qa/acceptance-tests`
`@MultiDbTest` harness, not just the one the issue happened to mention. The backend is selected by
the `test.integration.camunda.database.type` system property (case-insensitive;
`CamundaMultiDBExtension` reads it and defaults to `LOCAL` if unset — an omitted property silently
runs the wrong backend, not "all of them").

Scope to the exact reproducing test (by class#method) the same way as any other targeted run — don't
run the whole `qa/acceptance-tests` suite per backend, that's what CI's full matrix is for:

```bash
# RDBMS (H2, no external services needed)
./mvnw verify -pl qa/acceptance-tests -Pmulti-db-test -Dit.test=<TestClass>#<method> \
  -Dtest.integration.camunda.database.type=rdbms_h2 -DskipTests=false -DskipUTs -Dquickly

# Elasticsearch — bring the container up first
docker compose -f db/docker-compose.yml up -d elasticsearch
./mvnw verify -pl qa/acceptance-tests -Pmulti-db-test -Dit.test=<TestClass>#<method> \
  -Dtest.integration.camunda.database.type=es -DskipTests=false -DskipUTs -Dquickly
docker compose -f db/docker-compose.yml down

# OpenSearch — same pattern, different service/value
docker compose -f db/docker-compose.yml up -d opensearch
./mvnw verify -pl qa/acceptance-tests -Pmulti-db-test -Dit.test=<TestClass>#<method> \
  -Dtest.integration.camunda.database.type=os -DskipTests=false -DskipUTs -Dquickly
docker compose -f db/docker-compose.yml down
```

Other valid `DatabaseType` values exist (`RDBMS_MARIADB`, `RDBMS_MSSQL`, `RDBMS_MYSQL`,
`RDBMS_ORACLE`, `RDBMS_POSTGRES`, `RDBMS_AURORA`, `AWS_OS`) but `rdbms_h2`/`es`/`os` are the three
that matter for this skill — they're what the repo actually ships as interchangeable backends and
what CI's `Database Integration Tests` matrix runs per PR.

Record the pass/fail per backend in the issue comment explicitly (e.g. "reproduces on ES/OS, not on
RDBMS" or "fails on all three") — a single "confirmed" checkbox for Phase 3 hides exactly the
information Hard rule 8 needs from Phase 5 onward. Tear down whichever container was started before
moving on, same as any other reproduction environment.

## Method: Backend/engine bugs (no live-environment repro)

Some backend bugs are only reachable through internal engine state that isn't practical to trigger
through the REST/gRPC surface (e.g. a specific event-ordering edge case). For these:

- Don't force a live `c8run` repro — go straight to code analysis (Phase 4) grounded in the
  existing unit/IT tests for the area (`zeebe/engine/src/test/...` and similar).
- If a minimal unit test can demonstrate the bug, write one — that test **is** the reproduction,
  and doubles as part of the eventual fix's regression coverage.
- Mark Phase 3 in the issue comment as ⏭️ with the reason ("no live-environment repro path; see
  Phase 4 for a reproducing unit test") rather than ❌ — this is a deliberate method choice, not a
  failure.
