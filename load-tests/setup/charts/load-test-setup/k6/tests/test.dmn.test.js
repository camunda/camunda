// Unit tests for `scripts/test.dmn.js`. The k6 modules and the data loader are mocked, the Camunda client is real.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const { script, http, sleep, respondWith, checkResults } = await loadK6Module(
  "../scripts/test.dmn.js",
  { data: (name) => `content of ${name}` },
);

describe("test.dmn.js k6 test script", () => {
  it("should reference an exported function in every scenario", () => {
    assertScenariosExecExportedFunctions(script);
  });

  it("should ramp up to 1 evaluation per second by default", () => {
    // given
    const scenario = script.options.scenarios.evaluateDMN;

    // then
    assert.deepEqual(scenario.stages, [
      { target: 1, duration: "1m" },
      { target: 1, duration: "60d" },
    ]);
    assert.equal(scenario.preAllocatedVUs, 1);
    assert.equal(scenario.maxVUs, 20);
  });

  it("should read the load profile from the environment", async () => {
    // given
    Object.assign(globalThis.__ENV, {
      DMN_RATE: "6667",
      DMN_RAMP_UP: "10m",
      DMN_DURATION: "1h",
      DMN_PRE_ALLOCATED_VUS: "200",
      DMN_MAX_VUS: "2000",
    });

    // when
    // The query string bypasses the module cache, so the options are computed with this environment.
    const { options } = await import("../scripts/test.dmn.js?env");

    // then
    const scenario = options.scenarios.evaluateDMN;
    assert.deepEqual(scenario.stages, [
      { target: 6667, duration: "10m" },
      { target: 6667, duration: "1h" },
    ]);
    assert.equal(scenario.preAllocatedVUs, 200);
    assert.equal(scenario.maxVUs, 2000);
  });

  it("should deploy the DMN file and evaluate it once in setup", async () => {
    // given
    respondWith({ status: 200 }, { status: 200 }, { status: 200 });

    // when
    await script.setup();

    // then
    const urls = http.request.mock.calls.map((call) => call.arguments[1]);
    assert.deepEqual(urls, [
      "http://camunda:8080/v2/topology",
      "http://camunda:8080/v2/deployments",
      "http://camunda:8080/v2/decision-definitions/evaluation",
    ]);
    assert.deepEqual(http.file.mock.calls[0].arguments, [
      "content of small_decision.dmn",
      "small_decision.dmn",
    ]);
  });

  it("should retry the setup until the evaluation succeeds", async () => {
    // given
    respondWith(
      { status: 200 },
      { status: 200 },
      { status: 503, body: "resource exhausted" },
      { status: 200 },
      { status: 200 },
    );

    // when
    await script.setup();

    // then
    assert.equal(http.request.mock.callCount(), 5);
    assert.equal(sleep.mock.callCount(), 1);
  });

  it("should evaluate the decision with matching variables and check that status code is 200", () => {
    // given
    respondWith({ status: 200 }, { status: 503 });

    // when
    script.evaluateDMN();
    script.evaluateDMN();

    // then
    const [method, url, body] = http.request.mock.calls[0].arguments;
    assert.equal(method, "POST");
    assert.equal(url, "http://camunda:8080/v2/decision-definitions/evaluation");
    assert.deepEqual(JSON.parse(body), {
      decisionDefinitionId: "small_decision",
      variables: { category: "a" },
    });
    assert.deepEqual(checkResults(), [true, false]);
  });
});
