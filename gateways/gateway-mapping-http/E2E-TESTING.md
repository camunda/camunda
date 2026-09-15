# E2E Testing Procedure

This document describes how to run the orchestration cluster e2e test suite locally
to validate contract adaptation changes.

## Prerequisites

- Java 21
- Docker (for Elasticsearch)
- Node.js (for Playwright test runner)

## 1. Build the server

```bash
./mvnw install -pl dist -am -Dquickly -T1C -pl '!gateways/gateway-mcp'
```

To resume a failed build from a specific module:

```bash
./mvnw install -pl dist -am -Dquickly -T1C -pl '!gateways/gateway-mcp' -rf :module-name
```

## 2. Start Elasticsearch

```bash
bash start-es.sh
```

This starts a single-node Elasticsearch 8.17.3 container on port 9200 with security disabled.

Verify it's running:

```bash
curl -s http://localhost:9200 | head -5
```

## 3. Clear server data

Before each test run, delete stale data from any previous run:

```bash
rm -rf dist/target/camunda-zeebe/data/*
```

## 4. Start the Camunda server

```bash
bash start-server.sh
```

This starts the server with the e2e-test profile, consolidated auth, two test users
(demo/demo, lisa/lisa), Elasticsearch secondary storage, audit log enabled, and 2 broker
partitions.

Wait for the server to be ready. It prints `Started CamundaApplication` when ready.

## 5. Run the e2e tests

In a separate terminal:

```bash
bash run-e2e.sh
```

This runs all `api-tests` project tests via Playwright. Results go to `/tmp/e2e-results.txt`.
A sentinel file `/tmp/e2e-done.txt` is created when tests complete.

Monitor progress:

```bash
tail -f /tmp/e2e-results.txt
```

Check completion:

```bash
cat /tmp/e2e-done.txt
```

## 6. Review results

The test output at `/tmp/e2e-results.txt` contains pass/fail counts and failure details.

### Running a subset of tests

For targeted testing (e.g., batch operations only):

```bash
cd qa/c8-orchestration-cluster-e2e-test-suite
PLAYWRIGHT_HTML_OPEN=never npx playwright test \
  --project=api-tests \
  --grep "batch" \
  --repeat-each=3 \
  --workers=1
```

### A/B testing against main

To prove that contract adaptation changes don't introduce regressions:

1. Run the full suite on the feature branch, record results.
2. Switch to main, rebuild, clear data, run the same suite.
3. Compare pass/fail counts — feature branch should match or improve on main.

## Test projects

The Playwright config defines multiple test projects:

|      Project       |                                 Scope                                 |
|--------------------|-----------------------------------------------------------------------|
| `api-tests`        | Main API test suite (excludes clock, usage-metrics, audit-log)        |
| `api-tests-subset` | Clock, usage-metrics, audit-log tests (require special server config) |

When running `--project=api-tests`, Playwright substring-matches, so `api-tests-subset`
also runs. To run only the main suite, use `--project=api-tests$` or check the output
for both projects.

## Known flaky tests

- **Clock tests**: Pre-existing issue where StreamClock pin freezes CamundaExporter flush.
  See #49604.
- **Job error 409**: Intermittent due to shared jobApiTaskType across parallel workers.

