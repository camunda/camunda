// Unit tests for `scripts/test.custom-model.js`. Run with `make test` in the `k6` directory.
import { beforeEach, describe, it } from "node:test";
import assert from "node:assert/strict";

import {
  assertScenariosExecExportedFunctions,
  loadK6Module,
} from "./helpers.js";

const { script, http, sleep, counters, respondWith } = await loadK6Module(
  "../scripts/test.custom-model.js",
  { data: () => "<bpmn/>" },
);

const items = (field, ...keys) => ({
  status: 200,
  json: (key) =>
    key === "items" ? keys.map((k) => ({ [field]: k })) : undefined,
});
const tasks = (...keys) => items("userTaskKey", ...keys);
const status = (code) => ({ status: code });

// [method, path, parsed body] of each request.
const requests = () =>
  http.request.mock.calls.map(({ arguments: a }) => [
    a[0],
    a[1].replace("http://camunda:8080", ""),
    typeof a[2] === "string" ? JSON.parse(a[2]) : undefined,
  ]);
// The `responseType` param of each request.
const responseTypes = () =>
  http.request.mock.calls.map(({ arguments: a }) => a[3].responseType);
const sleeps = () => sleep.mock.calls.map((c) => c.arguments[0]);
const total = (name) => (counters[name] || []).reduce((sum, [v]) => sum + v, 0);

describe("test.custom-model.js", () => {
  it("should only use exported functions in scenarios", () => {
    assertScenariosExecExportedFunctions(script);
  });

  it("should run one VU per user", () => {
    assert.equal(script.options.scenarios.userTasks.executor, "constant-vus");
    assert.equal(script.options.scenarios.userTasks.vus, 10);
  });

  it("should retry the deployment until it succeeds", () => {
    // given
    respondWith(status(200), status(500), status(200));

    // when
    script.setup();

    // then
    assert.deepEqual(
      requests().map(([, path]) => path),
      ["/v2/topology", "/v2/deployments", "/v2/deployments"],
    );
    assert.deepEqual(sleeps(), [10]);
  });
});

describe("workOnUserTask", () => {
  beforeEach(() => script.resetUserTaskState());

  it("should search unassigned tasks, pick one, assign it, think, and complete it", (t) => {
    // given: Math.random 0.5 picks the second of two tasks and the middle of 2s-8s.
    t.mock.method(Math, "random", () => 0.5);
    globalThis.__VU = 7;
    respondWith(tasks("1", "2"), status(204), status(204));

    // when
    script.workOnUserTask();

    // then
    const [search, assign, complete] = requests();
    assert.equal(search[1], "/v2/user-tasks/search");
    assert.deepEqual(search[2].filter, {
      state: "CREATED",
      processDefinitionId: "benchmark-process",
      assignee: { $exists: false },
    });
    assert.equal(search[2].page.limit, 20);
    assert.deepEqual(assign, [
      "POST",
      "/v2/user-tasks/2/assignment",
      { assignee: "user-7", allowOverride: false },
    ]);
    assert.deepEqual(complete, [
      "POST",
      "/v2/user-tasks/2/completion",
      { variables: {} },
    ]);
    assert.deepEqual(sleeps(), [5]);
    assert.equal(total("custom_model_user_tasks_completed"), 1);
    // Only the search needs the response body.
    assert.deepEqual(responseTypes(), [undefined, "none", "none"]);
  });

  it("should wait when no task is open", () => {
    // given
    respondWith(tasks());

    // when
    script.workOnUserTask();

    // then
    assert.equal(requests().length, 1);
    assert.deepEqual(sleeps(), [2]);
  });

  it("should count a conflict and not complete when another user was faster", () => {
    // given
    respondWith(tasks("1"), status(409));

    // when
    script.workOnUserTask();

    // then
    assert.equal(requests().length, 2);
    assert.deepEqual(sleeps(), [0.1]);
    assert.deepEqual(counters["custom_model_user_tasks_conflicts"], [
      [1, { step: "assign", status: "409" }],
    ]);
  });

  it("should double the pause with each conflict in a row and reset it after an assignment", (t) => {
    // given: six conflicts in a row, then a successful assignment, then one more conflict.
    // Math.random 0 gives the minimum think time (2s).
    t.mock.method(Math, "random", () => 0);
    respondWith(
      tasks("1", "2", "3", "4", "5", "6", "7", "8"),
      status(409),
      status(409),
      status(409),
      status(409),
      status(409),
      status(409),
      status(204),
      status(204),
      status(409),
    );

    // when
    for (let i = 0; i < 8; i++) {
      script.workOnUserTask();
    }

    // then
    assert.deepEqual(sleeps(), [0.1, 0.2, 0.4, 0.8, 1.6, 2, 2, 0.1]);
  });

  it("should not pick a task again that was gone (404)", (t) => {
    // given: task 900 is gone. The next search shows it again, next to task 901.
    t.mock.method(Math, "random", () => 0);
    respondWith(
      tasks("900"),
      status(404),
      tasks("900", "901"),
      status(204),
      status(204),
    );

    // when
    script.workOnUserTask();
    script.workOnUserTask();

    // then
    assert.equal(requests()[3][1], "/v2/user-tasks/901/assignment");
    assert.equal(total("custom_model_user_tasks_completed"), 1);
  });

  it("should search oldest first by default", () => {
    // given
    respondWith(tasks());

    // when
    script.workOnUserTask();

    // then
    assert.deepEqual(requests()[0][2].sort, [
      { field: "creationDate", order: "ASC" },
    ]);
  });

  it("should drain the oldest unassigned tasks without a check for abandoned tasks", (t) => {
    // given: VU 10 of 10 users would check for abandoned tasks in its first iteration.
    t.mock.method(Math, "random", () => 0);
    globalThis.__VU = 10;
    respondWith(tasks("1"), status(204), status(204));

    // when
    script.drainUserTask();

    // then
    const [search, assign, complete] = requests();
    assert.deepEqual(search[2].filter, {
      state: "CREATED",
      processDefinitionId: "benchmark-process",
      assignee: { $exists: false },
    });
    assert.deepEqual(search[2].sort, [{ field: "creationDate", order: "ASC" }]);
    assert.equal(assign[1], "/v2/user-tasks/1/assignment");
    assert.equal(complete[1], "/v2/user-tasks/1/completion");
    assert.equal(total("custom_model_user_tasks_completed"), 1);
  });

  it("should copy task keys without changing them", () => {
    assert.equal(script.ownCopy("2251799813685251"), "2251799813685251");
  });

  it("should pick the search order at random with MIXED", (t) => {
    // given
    const random = t.mock.method(Math, "random", () => 0.2);

    // when, then
    assert.equal(script.searchOrder("MIXED"), "ASC");
    random.mock.mockImplementation(() => 0.7);
    assert.equal(script.searchOrder("MIXED"), "DESC");
    assert.equal(script.searchOrder("ASC"), "ASC");
    assert.equal(script.searchOrder("DESC"), "DESC");
  });

  it("should back off when the assignment is rejected with 503", () => {
    // given
    respondWith(tasks("1"), status(503));

    // when
    script.workOnUserTask();

    // then
    assert.deepEqual(sleeps(), [1]);
    assert.deepEqual(counters["custom_model_user_tasks_failed"], [
      [1, { step: "assign", status: "503" }],
    ]);
  });

  it("should retry a completion that is rejected with 503", (t) => {
    // given
    t.mock.method(Math, "random", () => 0);
    respondWith(tasks("1"), status(204), status(503), status(204));

    // when
    script.workOnUserTask();

    // then: think time (2s), then one backoff (1s).
    assert.deepEqual(sleeps(), [2, 1]);
    assert.equal(total("custom_model_user_tasks_failed"), 1);
    assert.equal(total("custom_model_user_tasks_completed"), 1);
  });

  it("should give up the completion after three rejections", (t) => {
    // given
    t.mock.method(Math, "random", () => 0);
    respondWith(tasks("1"), status(204), status(503), status(503), status(503));

    // when
    script.workOnUserTask();

    // then
    assert.equal(requests().length, 5);
    assert.equal(total("custom_model_user_tasks_failed"), 3);
    assert.equal(total("custom_model_user_tasks_completed"), 0);
  });
});

describe("search result reuse", () => {
  beforeEach(() => script.resetUserTaskState());

  it("should try the next task of the same search result before searching again", () => {
    // given: one search with two tasks, the first assignment is a conflict.
    globalThis.__VU = 7;
    respondWith(tasks("1", "2"), status(409), status(204), status(204));

    // when
    script.workOnUserTask();
    script.workOnUserTask();

    // then: only one search for both tasks.
    const paths = requests().map(([, path]) => path);
    assert.equal(paths.filter((p) => p === "/v2/user-tasks/search").length, 1);
    assert.equal(total("custom_model_user_tasks_completed"), 1);
  });
});

describe("takeOverAbandonedTask", () => {
  it("should take over and complete the oldest abandoned task", () => {
    // given
    globalThis.__VU = 3;
    respondWith(tasks("700"), status(204), status(204));

    // when
    const tookOver = script.takeOverAbandonedTask();

    // then
    assert.equal(tookOver, true);
    const [search, assign, complete] = requests();
    assert.deepEqual(search[2].filter.assignee, { $exists: true });
    assert.ok(search[2].filter.creationDate.$lt);
    assert.deepEqual(assign, [
      "POST",
      "/v2/user-tasks/700/assignment",
      { assignee: "user-3", allowOverride: true },
    ]);
    assert.equal(complete[1], "/v2/user-tasks/700/completion");
    assert.equal(total("custom_model_user_tasks_taken_over"), 1);
    assert.equal(total("custom_model_user_tasks_completed"), 1);
  });

  it("should do nothing if no task is abandoned", () => {
    // given
    respondWith(tasks());

    // when / then
    assert.equal(script.takeOverAbandonedTask(), false);
    assert.equal(requests().length, 1);
  });
});

describe("startInstance", () => {
  it("should create an instance of the process with the start variables", () => {
    // given
    respondWith(status(200));

    // when
    script.startInstance();

    // then
    assert.deepEqual(requests(), [
      [
        "POST",
        "/v2/process-instances",
        { processDefinitionId: "benchmark-process", variables: {} },
      ],
    ]);
    assert.deepEqual(responseTypes(), ["none"]);
  });

  it("should add a scenario only if the rate is above 0", () => {
    assert.equal(script.options.scenarios.startInstances, undefined);
    assert.deepEqual(script.startScenario(0), {});
    assert.deepEqual(script.startScenario(75).startInstances, {
      exec: "startInstance",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate: 75,
      timeUnit: "1s",
      preAllocatedVUs: 15,
      maxVUs: 38,
    });
  });

  it("should add the drain only if the rate is above 0", () => {
    assert.equal(script.options.scenarios.drainUserTasks, undefined);
    assert.deepEqual(script.drainScenario(0), {});
    assert.deepEqual(script.drainScenario(30).drainUserTasks, {
      exec: "drainUserTask",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate: 30,
      timeUnit: "1s",
      preAllocatedVUs: 6,
      maxVUs: 60,
    });
  });

  it("should add the API mix only if the rate is above 0", () => {
    assert.deepEqual(script.apiMixScenario(0), {});
    assert.deepEqual(script.apiMixScenario(1).apiMix, {
      exec: "apiMix",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 5,
      maxVUs: 10,
    });
  });
});

describe("parseThinkTime", () => {
  it("should parse ranges and single values in seconds", () => {
    assert.deepEqual(script.parseThinkTime("2s-8s"), [2, 8]);
    assert.deepEqual(script.parseThinkTime("500ms-2s"), [0.5, 2]);
    assert.deepEqual(script.parseThinkTime("5s"), [5, 5]);
  });

  it("should reject an invalid value", () => {
    assert.throws(
      () => script.parseThinkTime("5 minutes"),
      /Invalid think time/,
    );
  });
});

describe("apiMix", () => {
  it("should use keys from the search responses and group paths by template", () => {
    // given: the incident search returns nothing.
    respondWith(
      items("processInstanceKey", "10"),
      status(200),
      status(200),
      status(200),
      items("elementInstanceKey", "20"),
      status(200),
      items("variableKey", "30"),
      status(200),
      items("incidentKey"),
      items("processDefinitionKey", "40"),
      status(200),
      status(200),
      status(200),
      status(200),
      tasks("50"),
      status(200),
    );

    // when
    script.apiMix();

    // then
    const calls = http.request.mock.calls.map(({ arguments: a }) => [
      a[0],
      a[1].replace("http://camunda:8080", ""),
      a[3].tags.name,
    ]);
    assert.deepEqual(calls, [
      ["POST", "/v2/process-instances/search", "/v2/process-instances/search"],
      ["GET", "/v2/process-instances/10", "/v2/process-instances/{key}"],
      [
        "GET",
        "/v2/process-instances/10/sequence-flows",
        "/v2/process-instances/{key}/sequence-flows",
      ],
      [
        "GET",
        "/v2/process-instances/10/statistics/element-instances",
        "/v2/process-instances/{key}/statistics/element-instances",
      ],
      ["POST", "/v2/element-instances/search", "/v2/element-instances/search"],
      ["GET", "/v2/element-instances/20", "/v2/element-instances/{key}"],
      ["POST", "/v2/variables/search", "/v2/variables/search"],
      ["GET", "/v2/variables/30", "/v2/variables/{key}"],
      ["POST", "/v2/incidents/search", "/v2/incidents/search"],
      [
        "POST",
        "/v2/process-definitions/search",
        "/v2/process-definitions/search",
      ],
      ["GET", "/v2/process-definitions/40", "/v2/process-definitions/{key}"],
      [
        "GET",
        "/v2/process-definitions/40/xml",
        "/v2/process-definitions/{key}/xml",
      ],
      [
        "POST",
        "/v2/process-definitions/40/statistics/element-instances",
        "/v2/process-definitions/{key}/statistics/element-instances",
      ],
      [
        "POST",
        "/v2/decision-instances/search",
        "/v2/decision-instances/search",
      ],
      ["POST", "/v2/user-tasks/search", "/v2/user-tasks/search"],
      [
        "POST",
        "/v2/user-tasks/50/variables/search",
        "/v2/user-tasks/{key}/variables/search",
      ],
    ]);
  });
});
