// Unit tests for `scripts/test.dmn-large.js`. The k6 modules and `open()` are mocked, the Camunda
// client is real.
import { describe, it, mock } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const vectorLine = (id, rate, variables = { Lane_Code: id }) =>
  JSON.stringify({ id, variables, expected: {}, rate });

const VECTORS = [
  vectorLine("Tariff_Base_Rate#1", 1.5),
  vectorLine("Tariff_Adj1_Rate#7", 2.25),
  vectorLine("random#1", 0.75),
].join("\n");

// Files of the generate-data init container, keyed by name.
const files = {
  "freight_tariff_48mb.dmn": "<definitions/>",
  "freight_tariff_48mb.vectors.jsonl": VECTORS,
};
const open = mock.fn((path) => {
  const content = files[path.split("/").pop()];
  if (content === undefined) {
    throw new Error(`no such file: ${path}`);
  }
  return content;
});
const execution = { scenario: { iterationInTest: 0 } };

mock.module("k6/data", {
  exports: {
    SharedArray: class extends Array {
      constructor(name, fn) {
        super(...fn());
      }
    },
  },
});
mock.module("k6/execution", { exports: { default: execution } });
globalThis.open = open;
globalThis.__VU = 0;

const { script, http, sleep, check, respondWith } = await loadK6Module(
  "../scripts/test.dmn-large.js",
);

const evaluationResponse = (rate, status = 200) => ({
  status,
  body: JSON.stringify({ output: JSON.stringify({ rate }) }),
});

const checkResultsOf = (response) => {
  const [, checks] = check.mock.calls.at(-1).arguments;
  return Object.fromEntries(
    Object.entries(checks).map(([name, fn]) => [name, fn(response)]),
  );
};

describe("test.dmn-large.js k6 test script", () => {
  it("should reference an exported function in every scenario", () => {
    assertScenariosExecExportedFunctions(script);
  });

  it("should read the generated files from GENERATED_DIR", () => {
    const paths = open.mock.calls.map((call) => call.arguments[0]);
    assert.deepEqual(paths, [
      "../generated/freight_tariff_48mb.dmn",
      "../generated/freight_tariff_48mb.vectors.jsonl",
    ]);
    assert.equal(open.mock.calls[0].arguments[1], "b");
  });

  it("should parse every vector with its target table, in the same shuffled order", () => {
    // when
    const first = script.parseVectors(VECTORS);
    const second = script.parseVectors(VECTORS);

    // then
    assert.deepEqual(first, second);
    assert.deepEqual(first.map((v) => v.target).sort(), [
      "Tariff_Adj1_Rate",
      "Tariff_Base_Rate",
      "random",
    ]);
    assert.deepEqual(
      first.find((v) => v.target === "Tariff_Adj1_Rate"),
      {
        target: "Tariff_Adj1_Rate",
        variables: { Lane_Code: "Tariff_Adj1_Rate#7" },
        rate: 2.25,
      },
    );
  });

  it("should deploy the generated DMN and evaluate a vector in setup", async () => {
    // given
    respondWith({ status: 200 }, { status: 200 }, evaluationResponse(1));

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
      "<definitions/>",
      "freight_tariff_48mb.dmn",
    ]);
    assert.equal(
      JSON.parse(http.request.mock.calls[2].arguments[2]).decisionDefinitionId,
      "Tariff_Rate",
    );
  });

  it("should retry the setup until the deployment succeeds", async () => {
    // given
    respondWith(
      { status: 200 },
      { status: 500, body: "EXCEEDED_BATCH_RECORD_SIZE" },
      { status: 200 },
      evaluationResponse(1),
    );

    // when
    await script.setup();

    // then
    assert.equal(http.request.mock.callCount(), 4);
    assert.equal(sleep.mock.callCount(), 1);
  });

  it("should evaluate the vectors in turn and check the returned rate", () => {
    // given
    const vectors = script.parseVectors(VECTORS);

    for (const [iteration, response, expected] of [
      [0, evaluationResponse(vectors[0].rate), true],
      [4, evaluationResponse(vectors[1].rate + 0.01), false],
    ]) {
      execution.scenario.iterationInTest = iteration;
      respondWith(response);

      // when
      script.evaluateDMN();

      // then
      const vector = vectors[iteration % vectors.length];
      const [, , body, params] = http.request.mock.calls.at(-1).arguments;
      assert.deepEqual(JSON.parse(body), {
        decisionDefinitionId: "Tariff_Rate",
        variables: vector.variables,
      });
      assert.equal(params.tags.target, vector.target);
      assert.deepEqual(checkResultsOf(response), {
        "status is 200": true,
        "rate is expected": expected,
      });
    }
  });

  it("should fail the rate check of a rejected evaluation", () => {
    // given
    respondWith({ status: 503, body: "resource exhausted" });

    // when
    script.evaluateDMN();

    // then
    assert.deepEqual(checkResultsOf({ status: 503, body: "" }), {
      "status is 200": false,
      "rate is expected": false,
    });
  });

  it("should read the rate from an output object or a JSON string", () => {
    assert.equal(script.evaluatedRate({ body: '{"output":{"rate":2}}' }), 2);
    assert.equal(script.evaluatedRate(evaluationResponse(3)), 3);
    assert.equal(script.evaluatedRate({ body: "not json" }), undefined);
  });

  it("should fail the setup without the generated files", async () => {
    // given
    open.mock.mockImplementation((path) => {
      throw new Error(`no such file: ${path}`);
    });

    // when
    // The query string bypasses the module cache, so the init context runs without the files.
    const { setup } = await import("../scripts/test.dmn-large.js?missing");

    // then
    assert.throws(() => setup(), /Missing generated data/);
  });
});
