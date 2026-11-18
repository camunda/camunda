// Unit tests for the `writeFlow` scenario of `scripts/test.custom-model.js`. The k6 modules and the
// data loader are mocked, the Camunda client is real.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const { script, http, sleep, respondWith } = await loadK6Module(
  "../scripts/test.custom-model.js",
  { data: (name) => `content of ${name}` },
);

// k6 defines `__VU` in every VU.
globalThis.__VU = 1;

const requestOf = (call) => ({
  url: call.arguments[1],
  body: call.arguments[2] && JSON.parse(call.arguments[2]),
});

describe("test.custom-model.js writeFlow scenario", () => {
  it("should reference an exported function in every scenario", () => {
    assertScenariosExecExportedFunctions(script);
  });

  it("should parse the stages into rates and durations", () => {
    // when
    const stages = script.parseWriteFlowStages("50:10m, 100:30s");

    // then
    assert.deepEqual(stages, [
      { rate: 50, duration: "10m" },
      { rate: 100, duration: "30s" },
    ]);
    assert.deepEqual(script.parseWriteFlowStages("5:1m;6:2m"), [
      { rate: 5, duration: "1m" },
      { rate: 6, duration: "2m" },
    ]);
    assert.deepEqual(script.parseWriteFlowStages(""), []);
  });

  it("should reject a stage without a rate or a duration", () => {
    assert.throws(() => script.parseWriteFlowStages("50"), /Invalid/);
    assert.throws(() => script.parseWriteFlowStages("0:10m"), /Invalid/);
  });

  it("should step the rate and size the VUs for the highest rate", () => {
    // when
    const { writeFlow } = script.writeFlowScenario([
      { rate: 50, duration: "10m" },
      { rate: 100, duration: "5m" },
    ]);

    // then: 5 s per iteration by default
    assert.equal(writeFlow.executor, "ramping-arrival-rate");
    assert.equal(writeFlow.startRate, 50);
    assert.deepEqual(
      writeFlow.stages.map((s) => s.target),
      [50, 50, 100, 100],
    );
    assert.equal(writeFlow.preAllocatedVUs, 250);
    assert.equal(writeFlow.maxVUs, 500);
  });

  it("should add no scenario without stages or without users", () => {
    assert.deepEqual(script.writeFlowScenario([]), {});
    assert.deepEqual(script.userTasksScenario(0), {});
  });

  it("should start an instance, find its task by the process instance key, assign and complete it", async () => {
    // given: the task shows in the second search
    respondWith(
      { status: 200, json: () => "pi-1" },
      { status: 200, json: () => [] },
      { status: 200, json: () => [{ userTaskKey: "ut-1" }] },
      { status: 204 },
      { status: 204 },
    );

    // when
    script.writeFlow();

    // then
    const calls = http.request.mock.calls.map(requestOf);
    assert.deepEqual(
      calls.map((c) => c.url.replace("http://camunda:8080", "")),
      [
        "/v2/process-instances",
        "/v2/user-tasks/search",
        "/v2/user-tasks/search",
        "/v2/user-tasks/ut-1/assignment",
        "/v2/user-tasks/ut-1/completion",
      ],
    );
    assert.deepEqual(calls[1].body.filter, { processInstanceKey: "pi-1" });
    assert.ok(sleep.mock.callCount() >= 2, "pauses before each search");
  });

  it("should not assign anything if the instance start fails", () => {
    // given
    respondWith({ status: 503 });

    // when
    script.writeFlow();

    // then
    assert.equal(http.request.mock.callCount(), 1);
  });
});
