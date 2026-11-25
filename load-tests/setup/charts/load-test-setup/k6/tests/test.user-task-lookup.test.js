// Unit tests for `scripts/test.user-task-lookup.js`. The k6 modules are mocked, the Camunda client is real.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const PAYLOAD = { customer: "my-customer" };

const { script, http, sleep, respondWith, checkResults } = await loadK6Module(
  "../scripts/test.user-task-lookup.js",
  {
    data: (name) =>
      name.endsWith(".json") ? JSON.stringify(PAYLOAD) : "<bpmn/>",
  },
);

const requests = () => http.request.mock.calls.map((c) => c.arguments);
const requestsTo = (url) =>
  requests().filter(([, requestUrl]) => requestUrl.endsWith(url));

// Fakes the Camunda endpoints used by `setup()`: `existing` user tasks are searchable from the
// start. Instances that are created become searchable after `exportLag` further searches.
// The first `failedDeployments` deployments, `failedSearches` searches and `failedCreations`
// creations fail.
function fakeCamunda({
  existing = [],
  failedDeployments = 0,
  failedSearches = 0,
  failedCreations = 0,
  exportLag = 0,
}) {
  const created = [];
  let deploymentFailures = failedDeployments;
  let searchFailures = failedSearches;
  let creationFailures = failedCreations;
  let lagLeft = exportLag;
  http.request.mock.mockImplementation((method, url, body) => {
    const path = url.replace("http://camunda:8080", "");
    if (path === "/v2/deployments" && deploymentFailures > 0) {
      deploymentFailures--;
      return { status: 503 };
    }
    if (path === "/v2/process-instances") {
      if (creationFailures > 0) {
        creationFailures--;
        return { status: 500 };
      }
      created.push(`created-${created.length}`);
      return { status: 200 };
    }
    if (path === "/v2/user-tasks/search") {
      if (searchFailures > 0) {
        searchFailures--;
        return { status: 503 };
      }
      const exported = created.length === 0 || lagLeft === 0;
      if (created.length > 0 && lagLeft > 0) {
        lagLeft--;
      }
      const visible = [...existing, ...(exported ? created : [])];
      const { limit, after } = JSON.parse(body).page;
      const offset = after === undefined ? 0 : Number(after);
      const items = visible
        .slice(offset, offset + limit)
        .map((userTaskKey) => ({ userTaskKey }));
      return {
        status: 200,
        json: () => ({ items, page: { endCursor: String(offset + limit) } }),
      };
    }
    return { status: 200 };
  });
  return { created };
}

const keys = (count, prefix = "existing") =>
  Array.from({ length: count }, (_, i) => `${prefix}-${i}`);

describe("test.user-task-lookup.js k6 test script", () => {
  it("should reference an exported function in every scenario", () => {
    assertScenariosExecExportedFunctions(script);
  });

  describe("options", () => {
    it("should ramp to and hold each default rate, then stay idle", () => {
      // given / when
      const { stages, executor, startRate } = script.options.scenarios.lookup;

      // then
      assert.equal(executor, "ramping-arrival-rate");
      assert.equal(startRate, 0);
      assert.deepEqual(stages.slice(0, 4), [
        { target: 30, duration: "1m" },
        { target: 30, duration: "15m" },
        { target: 150, duration: "1m" },
        { target: 150, duration: "15m" },
      ]);
      assert.deepEqual(stages.slice(-4), [
        { target: 3000, duration: "1m" },
        { target: 3000, duration: "15m" },
        { target: 0, duration: "1m" },
        { target: 0, duration: "60d" },
      ]);
      assert.equal(stages.length, 6 * 2 + 2);
    });

    it("should build the stages from custom rates, plateau and ramp", () => {
      // given
      const rates = script.parseRates("10:20");

      // when
      const stages = script.buildStages(rates, "5m", "30s");

      // then
      assert.deepEqual(stages, [
        { target: 10, duration: "30s" },
        { target: 10, duration: "5m" },
        { target: 20, duration: "30s" },
        { target: 20, duration: "5m" },
        { target: 0, duration: "30s" },
        { target: 0, duration: "60d" },
      ]);
    });

    it("should reject rates that are not non-negative integers", () => {
      for (const rates of ["", "abc", "10:-5", "10::20", "1.5", "10,20"]) {
        // when / then
        assert.throws(
          () => script.parseRates(rates),
          /Invalid LOOKUP_RATES/,
          `rates "${rates}"`,
        );
      }
    });
  });

  describe("setup", () => {
    it("should deploy the model and create the whole pool when it does not exist yet", async () => {
      // given
      globalThis.__ENV.POOL_SIZE = "3";
      const { created } = fakeCamunda({});

      // when
      const data = await script.setup();

      // then
      assert.deepEqual(http.file.mock.calls[0].arguments, [
        "<bpmn/>",
        "user-task-lookup.bpmn",
      ]);
      assert.equal(requestsTo("/v2/deployments").length, 1);
      const creations = requestsTo("/v2/process-instances");
      assert.equal(creations.length, 3);
      assert.deepEqual(JSON.parse(creations[0][2]), {
        processDefinitionId: "k6-user-task-lookup",
        variables: PAYLOAD,
      });
      assert.deepEqual(data, { userTaskKeys: created });
    });

    it("should only create the missing instances when part of the pool exists", async () => {
      // given
      globalThis.__ENV.POOL_SIZE = "5";
      const { created } = fakeCamunda({ existing: keys(2) });

      // when
      const data = await script.setup();

      // then
      assert.equal(created.length, 3);
      assert.deepEqual(data.userTaskKeys, [...keys(2), ...created]);
    });

    it("should not create instances when the pool already exists", async () => {
      // given
      globalThis.__ENV.POOL_SIZE = "2";
      fakeCamunda({ existing: keys(2) });

      // when
      const data = await script.setup();

      // then
      assert.equal(requestsTo("/v2/process-instances").length, 0);
      assert.deepEqual(data.userTaskKeys, keys(2));
    });

    it("should follow the search cursor to collect all existing user tasks", async () => {
      // given: the existing tasks span two search pages.
      globalThis.__ENV.POOL_SIZE = "1500";
      fakeCamunda({ existing: keys(1500) });

      // when
      const data = await script.setup();

      // then
      assert.deepEqual(data.userTaskKeys, keys(1500));
      const searches = requestsTo("/v2/user-tasks/search").map(([, , body]) =>
        JSON.parse(body),
      );
      assert.equal(searches.length, 2);
      assert.deepEqual(searches[0].filter, {
        processDefinitionId: "k6-user-task-lookup",
        state: "CREATED",
      });
      assert.equal(searches[0].page.after, undefined);
      assert.equal(searches[1].page.after, "1000");
    });

    it("should retry a failed creation after a second", async () => {
      // given
      globalThis.__ENV.POOL_SIZE = "2";
      const { created } = fakeCamunda({ failedCreations: 1 });

      // when
      const data = await script.setup();

      // then
      assert.equal(requestsTo("/v2/process-instances").length, 3);
      assert.deepEqual(
        sleep.mock.calls.map((c) => c.arguments[0]),
        [1],
      );
      assert.deepEqual(data.userTaskKeys, created);
    });

    it("should wait until all created user tasks are searchable", async () => {
      // given: the created tasks are exported after one more search.
      globalThis.__ENV.POOL_SIZE = "2";
      const { created } = fakeCamunda({ exportLag: 1 });

      // when
      const data = await script.setup();

      // then
      assert.deepEqual(data.userTaskKeys, created);
      assert.deepEqual(
        sleep.mock.calls.map((c) => c.arguments[0]),
        [10],
      );
    });

    it("should retry the deployment every 10 seconds until it succeeds", async () => {
      // given
      globalThis.__ENV.POOL_SIZE = "1";
      const { created } = fakeCamunda({ failedDeployments: 2 });

      // when
      const data = await script.setup();

      // then
      assert.equal(requestsTo("/v2/deployments").length, 3);
      assert.deepEqual(
        sleep.mock.calls.map((c) => c.arguments[0]),
        [10, 10],
      );
      assert.deepEqual(data.userTaskKeys, created);
    });

    it("should retry a failed search every 10 seconds", async () => {
      // given
      globalThis.__ENV.POOL_SIZE = "2";
      fakeCamunda({ existing: keys(2), failedSearches: 2 });

      // when
      const data = await script.setup();

      // then
      assert.deepEqual(data.userTaskKeys, keys(2));
      assert.deepEqual(
        sleep.mock.calls.map((c) => c.arguments[0]),
        [10, 10],
      );
    });

    it("should reject an invalid pool size", async () => {
      // given
      globalThis.__ENV.POOL_SIZE = "many";

      // when / then
      await assert.rejects(async () => script.setup(), /Invalid POOL_SIZE/);
    });
  });

  describe("lookup", () => {
    it("should read the user tasks round-robin and check that status code is 200", async () => {
      // given
      const data = { userTaskKeys: ["a", "b", "c"] };
      respondWith({ status: 200 }, { status: 500 });

      // when
      await script.lookup(data);
      await script.lookup(data);

      // then
      assert.deepEqual(checkResults(), [true, false]);
    });

    it("should visit every user task in order, wrapping around the pool", async () => {
      // given
      const data = { userTaskKeys: ["a", "b", "c"] };

      // when
      for (let i = 0; i < 4; i++) {
        await script.lookup(data);
      }

      // then: the random start offset is unknown, but the order is cyclic.
      const visited = requests().map(([method, url, body]) => {
        assert.equal(method, "POST");
        assert.equal(body, "{}");
        return url.match(/user-tasks\/(.+)\/lookup$/)[1];
      });
      assert.equal(new Set(visited.slice(0, 3)).size, 3);
      assert.equal(visited[3], visited[0]);
      const indexes = visited.map((key) => data.userTaskKeys.indexOf(key));
      for (let i = 1; i < indexes.length; i++) {
        assert.equal(indexes[i], (indexes[i - 1] + 1) % 3);
      }
    });

    it("should tag every request with the fixed endpoint name", async () => {
      // given
      const data = { userTaskKeys: ["a", "b"] };

      // when
      await script.lookup(data);
      await script.lookup(data);

      // then: the user task key must not end up in the tag, to keep the metric cardinality low.
      for (const [, , , params] of requests()) {
        assert.equal(params.tags.name, "/v2/user-tasks/{userTaskKey}/lookup");
      }
    });
  });
});
