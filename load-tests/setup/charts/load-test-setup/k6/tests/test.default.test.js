// Unit tests for `scripts/test.default.js`. The k6 modules and the data loader are mocked, the Camunda client is real.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const { script, http, respondWith, checkResults } = await loadK6Module(
  "../scripts/test.default.js",
  { data: (name) => `content of ${name}` },
);

describe("test.default.js k6 test script", () => {
  it("should reference an exported function in every scenario", () => {
    assertScenariosExecExportedFunctions(script);
  });

  it("should wait until Camunda is ready in setup", async () => {
    // given
    respondWith({ status: 200 });

    // when
    await script.setup();

    // then
    assert.equal(http.request.mock.callCount(), 1);
    assert.equal(
      http.request.mock.calls[0].arguments[1],
      "http://camunda:8080/v2/topology",
    );
  });

  it("should check that topology status code is 200", async () => {
    // given
    respondWith({ status: 200 }, { status: 503 });

    // when
    await script.checkTopology();
    await script.checkTopology();

    // then
    assert.deepEqual(checkResults(), [true, false]);
  });

  it("should deploy the BPMN file as a multipart request and check that status code is 200", () => {
    // given
    respondWith({ status: 200 }, { status: 500 });

    // when
    script.deploy();
    script.deploy();

    // then
    assert.deepEqual(http.file.mock.calls[0].arguments, [
      "content of micro.bpmn",
      "micro.bpmn",
    ]);
    const [method, url, body] = http.request.mock.calls[0].arguments;
    assert.equal(method, "POST");
    assert.equal(url, "http://camunda:8080/v2/deployments");
    assert.deepEqual(body, {
      resources: { content: "content of micro.bpmn", name: "micro.bpmn" },
    });
    assert.deepEqual(checkResults(), [true, false]);
  });
});
