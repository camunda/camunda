// Unit tests for `scripts/test.dmn-large.js`. The k6 modules and `open()` are mocked, the Camunda
// client is real.
import { describe, it, mock } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const vectorLine = (
  id,
  rate,
  depth,
  model = 0,
  variables = { Lane_Code: id },
) => JSON.stringify({ model, id, variables, expected: {}, rate, depth });

const VECTORS = [
  vectorLine("Tariff_Base_Rate#1", 1.5, 0.1),
  vectorLine("Tariff_Adj1_Rate#7", 2.25, 0.6),
  vectorLine("random#1", 0.75, 1),
  vectorLine("fallthrough#1", 0.5, 1),
  vectorLine("payload#1", 0.25, 1),
].join("\n");

const PORTFOLIO = JSON.stringify({
  vectors: "freight_tariff.vectors.jsonl",
  models: [
    { file: "freight_tariff.dmn", decision: "Tariff_Rate", size: "20MiB+" },
  ],
});

// Files of the generate-data init container, keyed by name.
const files = {
  "portfolio.json": PORTFOLIO,
  "freight_tariff.dmn": "<definitions/>",
  "freight_tariff.vectors.jsonl": VECTORS,
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
      "../generated/portfolio.json",
      "../generated/freight_tariff.dmn",
      "../generated/freight_tariff.vectors.jsonl",
    ]);
    assert.equal(open.mock.calls[1].arguments[1], "b");
  });

  it("should parse every vector with its model, target and depth bucket, in the same shuffled order", () => {
    // when
    const first = script.parseVectors(VECTORS);
    const second = script.parseVectors(VECTORS);

    // then
    assert.deepEqual(first, second);
    assert.deepEqual(first.map((v) => `${v.target}:${v.depth}`).sort(), [
      "Tariff_Adj1_Rate:q3",
      "Tariff_Base_Rate:q1",
      "fallthrough:fallthrough",
      "payload:payload",
      "random:q4",
    ]);
    assert.deepEqual(
      first.find((v) => v.target === "Tariff_Adj1_Rate"),
      {
        model: 0,
        target: "Tariff_Adj1_Rate",
        variables: { Lane_Code: "Tariff_Adj1_Rate#7" },
        rate: 2.25,
        depth: "q3",
      },
    );
  });

  it("should put a depth in its quarter of the base table", () => {
    assert.equal(script.depthBucket("Tariff_Base_Rate", 0), "q1");
    assert.equal(script.depthBucket("Tariff_Base_Rate", 0.25), "q2");
    assert.equal(script.depthBucket("Tariff_Base_Rate", 0.7499), "q3");
    assert.equal(script.depthBucket("random", 1), "q4");
    assert.equal(script.depthBucket("payload", 1), "payload");
    assert.equal(script.depthBucket("fallthrough", 1), "fallthrough");
  });

  it("should evaluate every vector in turn without a depth mix", () => {
    // given
    const vectors = script.parseVectors(VECTORS);

    // when
    const plan = script.planTraffic("", "", vectors, 1);

    // then
    assert.equal(plan.slots, null);
    assert.deepEqual(
      [0, 1, 2, 3, 4, 5].map((i) =>
        script.vectorForIteration(i, vectors, plan),
      ),
      [...vectors, vectors[0]],
    );
  });

  it("should spread the buckets of a depth mix evenly over a cycle", () => {
    // given
    const vectors = script.parseVectors(VECTORS);

    // when
    const plan = script.planTraffic("q1=1, payload=3", "", vectors, 1);

    // then
    assert.deepEqual(plan.slots, ["payload", "q1", "payload", "payload"]);
    assert.deepEqual(
      [0, 1, 2, 3].map(
        (i) => script.vectorForIteration(i, vectors, plan).depth,
      ),
      plan.slots,
    );
  });

  it("should reject a depth mix with an unknown or empty bucket", () => {
    const vectors = script.parseVectors(VECTORS);
    assert.throws(
      () => script.planTraffic("deep=1", "", vectors, 1),
      /Invalid DMN_DEPTH_MIX/,
    );
    assert.throws(
      () => script.planTraffic("q1=x", "", vectors, 1),
      /Invalid DMN_DEPTH_MIX/,
    );
    assert.throws(
      () => script.planTraffic("q2=1", "", vectors, 1),
      /q2, which has no vectors/,
    );
  });

  it("should spread the iterations over the models of a portfolio", () => {
    // given
    const vectors = Array.from({ length: 10 }, (_, model) => ({
      model,
      depth: "q1",
    }));
    const plan = script.planTraffic("", "", vectors, 10);

    // when
    const counts = Array(10).fill(0);
    for (let i = 0; i < 10000; i++) {
      counts[script.vectorForIteration(i, vectors, plan).model]++;
    }

    // then
    for (const count of counts) {
      assert.ok(count > 950 && count < 1050, `${counts}`);
    }
  });

  it("should send the share of the hot models to them", () => {
    // given
    const vectors = Array.from({ length: 10 }, (_, model) => ({
      model,
      depth: "q1",
    }));
    const plan = script.planTraffic("", "20:80", vectors, 10);

    // when
    const counts = Array(10).fill(0);
    for (let i = 0; i < 10000; i++) {
      counts[script.vectorForIteration(i, vectors, plan).model]++;
    }

    // then
    const hot = plan.order.slice(0, 2).reduce((sum, m) => sum + counts[m], 0);
    assert.ok(hot > 7900 && hot < 8100, `${hot}`);
    assert.throws(
      () => script.planTraffic("", "80", vectors, 10),
      /Invalid DMN_HOT_MODELS/,
    );
  });

  it("should build numbered extra variables", () => {
    assert.deepEqual(script.extraVariables(0), {});
    assert.deepEqual(script.extraVariables(2), {
      Extra_Variable_001: "EXTRA_VALUE_001",
      Extra_Variable_002: "EXTRA_VALUE_002",
    });
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
      "freight_tariff.dmn",
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
      assert.equal(params.tags.depth, vector.depth);
      assert.equal(params.tags.size, "20MiB+");
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
