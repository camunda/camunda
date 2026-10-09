// Unit tests for `scripts/test.data-read.js`. The k6 modules are mocked, the Camunda client is real.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const { script, http, sleep, respondWith, checkResults } = await loadK6Module(
  "../scripts/test.data-read.js",
);

const definitions = (...ids) => ({
  status: 200,
  json: () => ({
    items: ids.map((processDefinitionId) => ({ processDefinitionId })),
  }),
});

describe("test.data-read.js k6 test script", () => {
  it("should reference an exported function in every scenario", () => {
    assertScenariosExecExportedFunctions(script);
  });

  it("should retry every 10 seconds until a process definition exists", async () => {
    // given: Camunda is ready, then an empty list and an HTTP error both trigger a retry.
    respondWith(
      { status: 200 },
      definitions(),
      { status: 503 },
      definitions("first", "second"),
    );

    // when
    const data = await script.setup();

    // then
    assert.deepEqual(data, { processDefinitionId: "first" });
    assert.deepEqual(
      sleep.mock.calls.map((c) => c.arguments[0]),
      [10, 10],
    );
  });

  it("should search instances of the process definition and check that status code is 200", async () => {
    // given
    respondWith({ status: 200 }, { status: 500 });

    // when
    await script.searchProcessInstances({ processDefinitionId: "my-process" });
    await script.searchProcessInstances({ processDefinitionId: "my-process" });

    // then
    const [, url, body] = http.request.mock.calls[0].arguments;
    assert.equal(url, "http://camunda:8080/v2/process-instances/search");
    assert.equal(JSON.parse(body).filter.processDefinitionId, "my-process");
    assert.deepEqual(checkResults(), [true, false]);
  });
});
