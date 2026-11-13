// Unit tests for `scripts/test.optimize.js`. The k6 modules are mocked, the Optimize client is real.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
  okResponse,
} from "./helpers.js";

const { script, http, sleep, respondWith, checkResults } = await loadK6Module(
  "../scripts/test.optimize.js",
);

const overview = (...keys) => ({
  status: 200,
  json: () => keys.map((processDefinitionKey) => ({ processDefinitionKey })),
});

const dashboard = (...reportIds) => ({
  status: 200,
  json: (selector) =>
    selector === "tiles" ? reportIds.map((id) => ({ id })) : undefined,
});

describe("test.optimize.js k6 test script", () => {
  it("should reference an exported function in every scenario", () => {
    assertScenariosExecExportedFunctions(script);
  });

  it("should retry every 10 seconds until Optimize has a process definition", async () => {
    // given: an empty overview and an HTTP error both trigger a retry.
    respondWith(overview(), { status: 503 }, overview("first", "second"));

    // when
    const data = await script.setup();

    // then
    assert.deepEqual(data, { processDefinitionKey: "first" });
    assert.deepEqual(
      sleep.mock.calls.map((c) => c.arguments[0]),
      [10, 10],
    );
  });

  it("should skip process definitions whose key starts with `k6-`", async () => {
    // given
    respondWith(overview("k6-micro", "my-process"));

    // when
    const data = await script.setup();

    // then
    assert.equal(data.processDefinitionKey, "my-process");
    assert.equal(http.request.mock.callCount(), 1);
  });

  it("should use the configured process definition key without calling Optimize", async () => {
    // given
    globalThis.__ENV.OPTIMIZE_PROCESS_DEFINITION_KEY = "configured";

    // when
    const data = await script.setup();

    // then
    assert.deepEqual(data, { processDefinitionKey: "configured" });
    assert.equal(http.request.mock.callCount(), 0);
  });

  it("should open the management dashboard, then evaluate each of its reports", () => {
    // given
    respondWith(dashboard("report-a", "report-b"), okResponse, okResponse);

    // when
    script.evaluateHomepage();

    // then
    const calls = http.request.mock.calls.map((c) => c.arguments);
    assert.deepEqual(
      calls.map(([method, url]) => [method, url]),
      [
        ["GET", "http://optimize/api/dashboard/management"],
        ["POST", "http://optimize/api/report/report-a/evaluate"],
        ["POST", "http://optimize/api/report/report-b/evaluate"],
      ],
    );
    assert.equal(calls[1][2], "{}");
    assert.deepEqual(calls[1][3].tags, {
      name: "/api/report/{id}/evaluate",
      page: "homepage",
      phase: "report_evaluate",
      report_id: "report-a",
    });
    assert.equal(calls[1][3].headers["X-Optimize-Client-Timezone"], "UTC");
    assert.deepEqual(checkResults(), [true, true, true]);
  });

  it("should open the instant dashboard of the process definition, then evaluate its reports", () => {
    // given
    respondWith(dashboard("report-a"), okResponse);

    // when
    script.evaluateDetailed({ processDefinitionKey: "my-process" });

    // then
    const calls = http.request.mock.calls.map((c) => c.arguments);
    assert.equal(
      calls[0][1],
      "http://optimize/api/dashboard/instant/my-process",
    );
    assert.deepEqual(calls[0][3].tags, {
      name: "/api/dashboard/instant/{key}",
      page: "detailed",
      phase: "dashboard",
    });
    assert.equal(calls[1][3].tags.page, "detailed");
  });

  it("should not evaluate tiles that have no report id", () => {
    // given: the dashboard has a text tile without `id`.
    respondWith(
      {
        status: 200,
        json: () => [{ type: "text" }, { id: "report-a" }, { id: "" }],
      },
      okResponse,
    );

    // when
    script.evaluateHomepage();

    // then
    const urls = http.request.mock.calls.map((c) => c.arguments[1]);
    assert.deepEqual(urls, [
      "http://optimize/api/dashboard/management",
      "http://optimize/api/report/report-a/evaluate",
    ]);
  });

  it("should not evaluate reports when the dashboard request fails", () => {
    // given
    respondWith({ status: 500 });

    // when
    script.evaluateHomepage();

    // then
    assert.equal(http.request.mock.callCount(), 1);
    assert.deepEqual(checkResults(), [false]);
  });
});
