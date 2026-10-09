---
name: new-k6-load-test
description: Add or extend a k6 load test in the load-test-setup chart (load-tests/setup/charts/load-test-setup/k6/). Use when asked to create, write, or add a k6 test, scenario, or Camunda API call to the k6 tests.
---

# New k6 load test

All paths are relative to `load-tests/setup/charts/load-test-setup/`. Read `README.md` in that
directory (section "k6 tests") before starting.

## 1. Decide whether to extend an existing test

Existing tests are `k6/scripts/test.*.js` and are registered in `k6.tests` in `values.yaml`.

1. Read the existing test scripts first.
2. Recommend extending an existing test (add a scenario to its `options.scenarios`, plus an
   exported function) when the new load targets the same cluster state or the same setup. This
   avoids a second `TestRun`, a second k6 pod, and a second set of metrics.
3. Create a new test file only for one-shot experiments, or when the setup, duration, or load
   shape cannot share a script with an existing test.
4. State the decision and the reason to the user before writing code.

See "In which case to use k6 tests?" in `README.md`.

## 2. Put the test script in `k6/scripts/`

1. Name the file `test.<name>.js`, directly in `k6/scripts/`. Do not create sub-folders: the
   `k6-scripts` ConfigMap (`templates/k6/configmap-scripts.yaml`) only includes `k6/scripts/*.js`,
   so scripts in a sub-folder are not deployed.
2. Import `Client` from `./lib.camunda.js` and create it at module level (`const client = new
   Client();`). Module-level code runs once per VU.
3. Wait for Camunda to be ready in `setup()` with `client.waitUntilReady()`, because the cluster can still
   be starting when the k6 test starts.
4. Setup and pre-load any data in the `setup()` function: this runs once per
   VU. Use retries to wait until the cluster has reach the correct state.
5. Define the load in `options.scenarios`, with one exported function per scenario (`exec`).
   Set a long `duration` (for example `"60d"`) so that the test runs as long as the load test.
7. Call `check()` on the response of each request. Do not use `thresholds`: `K6_NO_THRESHOLDS`
   is set in `templates/k6/testrun.yaml` and k6 ignores them.
8. Do not store per-request data in module-level arrays, and do not add custom `Trend` metrics.
   k6 memory grows without bound on long runs, the pod is OOM-killed and not restarted.

## 3. Put test data in `k6/data/`

1. Put every file the test reads (DMN, BPMN, JSON, etc.) directly in `k6/data/`. The `k6-data`
   ConfigMap (`templates/k6/configmap-data.yaml`) includes `k6/data/*`. The files are mounted in
   `/data` and `DATA_DIR` points to it. Don't use subfolders, because the
   ConfigMap does not include them.
2. Load the file with `loadData(name)` from `./lib.data.js`, at module level. k6 only allows
   `open()` in the init context.
3. Skip this step when the test has no data.

## 4. Wrap each Camunda API in `k6/scripts/lib.camunda.js`

1. To make test simpler to read and more reusable, try not to call `http.*` or
   build URLs in the test script directly. Add one method per endpoint to the
   `Client` class, next to the existing ones (`deploy`,
   `decisionDefinitionEvaluation`, `searchProcessDefinitions`, ...).
2. Implement the method with `this.get(endpoint, params)` or `this.post(endpoint, payload,
   params)`. These methods authenticate the request, renew the token, and tag the metrics with
   `name: endpoint`. For multipart bodies, pass a `FormData` as the payload.
3. Use the endpoint path without a variable part as `endpoint` where possible, because it
   becomes the `name` tag and a path with an ID creates high-cardinality metrics. If the path
   must contain an ID, pass `{ tags: { name: "/v2/path/{id}" } }` as `params`.
4. Add the link to the endpoint's page in the
   [Orchestration Cluster REST API specification](https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/specifications/)
   in a one-line comment above the method, as the existing methods do.
5. Reuse an existing method when it already covers the endpoint. Do not duplicate it.

## 5. Register and enable the test in `values.yaml`

Add the test under `k6.tests` and set `enabled: true`:

```yaml
k6:
  tests:
    my-test:
      enabled: true
      script: test.mytest.js
```

1. The key (`my-test`) becomes the `TestRun` name `k6-my-test`.
2. `script` is the file name in `k6/scripts/`.
3. Skip this step when the test extends an existing test that is already enabled.
4. If the test needs a feature that is not available everywhere (a secondary storage, an
   endpoint missing in a stable version), disable it for those cases as `data-read` does (see
   `k6_data_read_supported` in `common.mk`). Ask the user before changing the Makefiles.

## 6. Validate

1. Run the test locally if a cluster is available:
   1. Set up the port-forwards and export the `CAMUNDA_*` variables as described in "How to run
      the k6 tests locally?" in `README.md`.
   2. Run k6 from `load-tests/setup/charts/load-test-setup/`:
      ```sh
      CAMUNDA_BASE_URL=http://localhost:8080 k6 run k6/scripts/test.mytest.js
      ```
      `DATA_DIR` is not set locally, so `lib.data.js` reads the data files from `../data`,
      relative to the script. This is `k6/data/` when the script is in `k6/scripts/`.
   3. To read the data files from another directory, set `DATA_DIR` to an absolute path, or to a
      path relative to the script:
      ```sh
      DATA_DIR=/path/to/data CAMUNDA_BASE_URL=http://localhost:8080 k6 run k6/scripts/test.mytest.js
      k6 run -e DATA_DIR=/path/to/data k6/scripts/test.mytest.js
      ```
      In the cluster, `testrun.yaml` sets `DATA_DIR=/data`, which is where the `k6-data`
      ConfigMap is mounted.
2. Regenerate the golden files, because enabling a test changes the rendered manifests:
   ```sh
   cd load-tests/setup/test && make update-golden
   ```
   Review `git diff golden/` and commit it in the same PR.
3. Update the "Which tests are deployed?" list in `README.md` with one line for the new test.
   Skip this when the test extends an existing one and the description is still correct.
4. Run `./mvnw license:format spotless:apply -T1C` when a `.md` file changed.
