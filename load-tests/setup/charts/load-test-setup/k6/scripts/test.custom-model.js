import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

import { Client } from "./lib.camunda.js";
import { loadData } from "./lib.data.js";

/* Runs a custom model next to the load-tester starter and workers:
 * - setup() deploys MODEL_FILE from `k6/data`. The starter starts instances of PROCESS_ID.
 * - `userTasks` simulates USER_TASK_USERS people. Each one searches open, unassigned user
 *   tasks (oldest first), picks one at random, assigns it, works on it for a think time, and
 *   completes it.
 * - `drainUserTasks` (only if USER_TASK_DRAIN_RATE > 0) works on the oldest unassigned tasks, at
 *   USER_TASK_DRAIN_RATE tries per second. Use it with USER_TASK_ORDER=DESC.
 * - `apiMix` sends one request per read endpoint per iteration, at API_RATE iterations per
 *   second. Keys for the GET requests come from the search responses.
 * - `startInstances` (only if START_RATE > 0) starts START_RATE instances per second through
 *   the REST API, with START_PAYLOAD (a JSON object) as variables. Use it instead of the Java
 *   starter if the instances need specific start variables.
 */

const MODEL_FILE = __ENV.MODEL_FILE || "benchmark-process.bpmn";
const PROCESS_ID = __ENV.PROCESS_ID || "benchmark-process";
// Set when multi-tenancy checks are enabled, e.g. `<default>`.
const TENANT_ID = __ENV.TENANT_ID || "";
// Number of people that work on user tasks at the same time.
const USER_TASK_USERS = parseInt(__ENV.USER_TASK_USERS || "10");
// Time between assignment and completion, random in this range, e.g. "2s-8s" or "5s".
const USER_TASK_THINK_TIME = parseThinkTime(
  __ENV.USER_TASK_THINK_TIME || "2s-8s",
);
// Search order: "ASC" (oldest first, default), "DESC" (newest first), or "MIXED" (each search
// picks ASC or DESC at random).
// - ASC: a completion takes 1-2 s to show in the search, and all users take the oldest tasks. So
//   most of the oldest tasks in the list are already completed, and the assigns get 404.
// - DESC: tasks that nobody picks while they are among the newest can starve.
// - MIXED: the DESC searches find tasks that nobody took yet, and the ASC searches drain the
//   oldest tasks.
const USER_TASK_ORDER = ["DESC", "MIXED"].includes(__ENV.USER_TASK_ORDER)
  ? __ENV.USER_TASK_ORDER
  : "ASC";
// Number of open tasks that one search returns. A user picks one of them at random.
const USER_TASK_PAGE_SIZE = parseInt(__ENV.USER_TASK_PAGE_SIZE || "20");
// Tries per second on the oldest unassigned tasks, next to the `userTasks` users. With DESC, tasks
// that drop out of the newest page are never picked, so their instances stay open and the state
// grows. A rate, not a number of users, limits the extra load: with no think time, a few users
// could complete hundreds of tasks per second. Set it above the rate at which tasks are left
// behind, so that the old tasks go down.
const USER_TASK_DRAIN_RATE = parseInt(__ENV.USER_TASK_DRAIN_RATE || "0");
// Wait time when the search finds no open task, or after a rejected request (503).
const IDLE_SECONDS = 2;
const BACKOFF_SECONDS = 1;
// Pause after a conflict, so that a user does not try again in a tight loop. The pause doubles
// with each conflict in a row, up to the maximum. Most conflicts happen when there are more users
// than open tasks: the search then shows mostly tasks that other users took in the last seconds
// (a change takes 1-2 s to show in the search). Without the longer pause, the idle users send
// many assigns that fail, and each one is a command that the partition must process.
const CONFLICT_PAUSE_SECONDS = 0.1;
const CONFLICT_PAUSE_MAX_SECONDS = 2;
// A user remembers tasks that were gone (404) and does not pick them again. The search can show
// such tasks as open for a long time, e.g. if their completion was not exported.
const GONE_TASKS_LIMIT = 1000;
// A person tries a failed completion again a few times.
const COMPLETE_ATTEMPTS = 3;
// Assigned tasks that are still open after this time count as abandoned, e.g. because the user
// gave up after rejections or k6 restarted. Each user checks for one abandoned task every
// USER_TASK_USERS seconds, so all users together check about once per second.
const ABANDONED_AFTER_SECONDS = parseInt(
  __ENV.USER_TASK_ABANDONED_AFTER_SECONDS || "120",
);
const ABANDONED_CHECK_INTERVAL_MS = USER_TASK_USERS * 1000;
const API_RATE = parseInt(__ENV.API_RATE || "1");
const START_RATE = parseInt(__ENV.START_RATE || "0");
const START_PAYLOAD = JSON.parse(__ENV.START_PAYLOAD || "{}");
// For requests where only the status counts: k6 does not keep the response body.
const NO_BODY = { responseType: "none" };

// Module-level code runs once per VU, so each VU has its own client.
const client = new Client();
const model = loadData(MODEL_FILE);

const userTasksCompleted = new Counter("custom_model_user_tasks_completed");
// Another user was faster, or the search showed a task that is already gone (404, 409).
const userTasksConflicts = new Counter("custom_model_user_tasks_conflicts");
// Other failures, e.g. backpressure (503). Tagged with `step` and `status`.
const userTasksFailed = new Counter("custom_model_user_tasks_failed");
// Abandoned tasks that a user took over.
const userTasksTakenOver = new Counter("custom_model_user_tasks_taken_over");

// A user works through the tasks of one search result before it searches again, like a person
// with a task list. A result older than this is stale, so the user searches again.
const SEARCH_REUSE_MS = parseInt(__ENV.USER_TASK_SEARCH_REUSE_MS || "2000");

// Per VU: keys of tasks that were gone, in insertion order.
const goneTasks = new Set();
// Per VU: tasks of the last search that the user did not try yet, in random order.
let candidates = [];
let candidatesExpireAt = 0;
// Per VU: number of conflicts in a row since the last successful assignment.
let conflictsInARow = 0;

/* Clears the per-VU state. Only for tests. */
export function resetUserTaskState() {
  goneTasks.clear();
  candidates = [];
  candidatesExpireAt = 0;
  conflictsInARow = 0;
}
// Per VU: time of the next check for abandoned tasks. The first check is staggered by the VU
// number, so that the users check one after the other from the start, about once per second.
let nextAbandonedCheck = null;

export const options = {
  setupTimeout: "20m",
  scenarios: {
    userTasks: {
      exec: "workOnUserTask",
      executor: "constant-vus",
      vus: USER_TASK_USERS,
      duration: "60d",
    },
    ...drainScenario(USER_TASK_DRAIN_RATE),
    ...apiMixScenario(API_RATE),
    ...startScenario(START_RATE),
  },
};

/* No scenario if the rate is 0. A try takes about 100 ms, or up to 2 s after conflicts in a row
 * (CONFLICT_PAUSE_MAX_SECONDS), so maxVUs covers about 2 s per try. */
export function drainScenario(rate) {
  if (rate <= 0) {
    return {};
  }
  return {
    drainUserTasks: {
      exec: "drainUserTask",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate,
      timeUnit: "1s",
      preAllocatedVUs: Math.max(5, Math.ceil(rate / 5)),
      maxVUs: Math.max(10, rate * 2),
    },
  };
}

/* No scenario if the rate is 0, for example to work down a user task backlog without reads. */
export function apiMixScenario(rate) {
  if (rate <= 0) {
    return {};
  }
  return {
    apiMix: {
      exec: "apiMix",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate,
      timeUnit: "1s",
      preAllocatedVUs: 5,
      maxVUs: 10,
    },
  };
}

/* No scenario if the rate is 0. One VU handles about 10 starts per second (~100 ms each).
 * k6 never removes a VU that it added, and each VU uses memory. So maxVUs is only enough for
 * starts of up to 500 ms. If the starts get slower, k6 drops starts (`dropped_iterations`) and
 * does not add more VUs. */
export function startScenario(rate) {
  if (rate <= 0) {
    return {};
  }
  return {
    startInstances: {
      exec: "startInstance",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate,
      timeUnit: "1s",
      preAllocatedVUs: Math.max(10, Math.ceil(rate / 5)),
      maxVUs: Math.max(20, Math.ceil(rate / 2)),
    },
  };
}

export function setup() {
  client.waitUntilReady();

  const waitTime = 10; // seconds
  while (true) {
    const response = client.deploy(MODEL_FILE, model, TENANT_ID);
    if (response.status === 200) {
      break;
    }
    console.error(
      `Unable to deploy ${MODEL_FILE} (will retry in ${waitTime}s), got HTTP status=${response.status}, body=${response.body}`,
    );
    sleep(waitTime);
  }
  console.log(`Deployed ${MODEL_FILE}, process ID ${PROCESS_ID}.`);
}

/* Starts one instance. A rejection (503 backpressure) is not retried: it shows as a failed
 * start in the k6 request metrics, and the arrival rate continues. */
export function startInstance() {
  const body = { processDefinitionId: PROCESS_ID, variables: START_PAYLOAD };
  if (TENANT_ID) {
    body.tenantId = TENANT_ID;
  }
  checkStatus(client.post("/v2/process-instances", body, NO_BODY), 200);
}

/* One iteration: one user picks one task and works on it. */
export function workOnUserTask() {
  const now = Date.now();
  if (nextAbandonedCheck === null) {
    nextAbandonedCheck = now + ((__VU || 0) % USER_TASK_USERS) * 1000;
  }
  if (now >= nextAbandonedCheck) {
    nextAbandonedCheck = now + ABANDONED_CHECK_INTERVAL_MS;
    if (takeOverAbandonedTask()) {
      return;
    }
  }
  pickAndWorkOn(USER_TASK_ORDER, now);
}

/* One try of the drain: picks one of the oldest unassigned tasks and works on it. The `userTasks`
 * users already take over abandoned tasks, so the drain does not check for them. */
export function drainUserTask() {
  pickAndWorkOn("ASC", Date.now());
}

function pickAndWorkOn(order, now) {
  if (candidates.length === 0 || now >= candidatesExpireAt) {
    if (!searchOpenTasks(now, order)) {
      return;
    }
  }
  const key = nextCandidate();
  if (key === undefined) {
    sleep(IDLE_SECONDS);
    return;
  }
  const assignment = client.post(
    `/v2/user-tasks/${key}/assignment`,
    { assignee: `user-${__VU}`, allowOverride: false },
    { ...NO_BODY, tags: { name: "/v2/user-tasks/{key}/assignment" } },
  );
  if (!checkStatus(assignment, 204)) {
    if (assignment.status === 404) {
      rememberGone(key);
    }
    onFailure("assign", assignment);
    return;
  }
  conflictsInARow = 0;

  sleep(thinkTime());
  completeTask(key);
}

/* Takes over one of the oldest abandoned tasks, if one exists, and completes it. A random pick
 * among the oldest keeps users from all taking the same task. */
export function takeOverAbandonedTask() {
  const cutoff = new Date(
    Date.now() - ABANDONED_AFTER_SECONDS * 1000,
  ).toISOString();
  const search = client.post("/v2/user-tasks/search", {
    filter: {
      state: "CREATED",
      processDefinitionId: PROCESS_ID,
      assignee: { $exists: true },
      creationDate: { $lt: cutoff },
    },
    sort: [{ field: "creationDate", order: "ASC" }],
    page: { limit: 50 },
  });
  const items = (
    (checkStatus(search, 200) && search.json("items")) ||
    []
  ).filter((task) => !goneTasks.has(task.userTaskKey));
  if (items.length === 0) {
    return false;
  }
  const key = ownCopy(
    items[Math.floor(Math.random() * items.length)].userTaskKey,
  );
  const assignment = client.post(
    `/v2/user-tasks/${key}/assignment`,
    { assignee: `user-${__VU}`, allowOverride: true },
    { ...NO_BODY, tags: { name: "/v2/user-tasks/{key}/assignment" } },
  );
  if (!checkStatus(assignment, 204)) {
    if (assignment.status === 404) {
      rememberGone(key);
    }
    onFailure("take-over", assignment);
    return true;
  }
  userTasksTakenOver.add(1);
  completeTask(key);
  return true;
}

function completeTask(key) {
  for (let attempt = 1; attempt <= COMPLETE_ATTEMPTS; attempt++) {
    const completion = client.post(
      `/v2/user-tasks/${key}/completion`,
      { variables: {} },
      { ...NO_BODY, tags: { name: "/v2/user-tasks/{key}/completion" } },
    );
    if (checkStatus(completion, 204)) {
      userTasksCompleted.add(1);
      return;
    }
    onFailure("complete", completion);
    if (completion.status !== 503) {
      return;
    }
  }
}

/* 404 and 409 are conflicts: the task is gone or another user has it. Other failures (e.g.
 * 503 backpressure) make the user wait before the next try. */
function onFailure(step, response) {
  const tags = { step, status: String(response.status) };
  if (response.status === 404 || response.status === 409) {
    userTasksConflicts.add(1, tags);
    sleep(
      Math.min(
        CONFLICT_PAUSE_SECONDS * 2 ** conflictsInARow,
        CONFLICT_PAUSE_MAX_SECONDS,
      ),
    );
    conflictsInARow++;
    return;
  }
  userTasksFailed.add(1, tags);
  sleep(BACKOFF_SECONDS);
}

/* Searches open, unassigned tasks and keeps them as candidates in random order. Returns false
 * if the search failed. */
function searchOpenTasks(now, order) {
  const search = client.post("/v2/user-tasks/search", {
    filter: {
      state: "CREATED",
      processDefinitionId: PROCESS_ID,
      assignee: { $exists: false },
    },
    sort: [{ field: "creationDate", order: searchOrder(order) }],
    page: { limit: USER_TASK_PAGE_SIZE },
  });
  if (!checkStatus(search, 200)) {
    userTasksFailed.add(1, { step: "search", status: String(search.status) });
    sleep(BACKOFF_SECONDS);
    return false;
  }
  candidates = (search.json("items") || [])
    .map((task) => ownCopy(task.userTaskKey))
    .filter((key) => !goneTasks.has(key));
  for (let i = candidates.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [candidates[i], candidates[j]] = [candidates[j], candidates[i]];
  }
  candidatesExpireAt = now + SEARCH_REUSE_MS;
  return true;
}

/* Returns a copy of a string from a response, which does not keep the response in memory.
 * `response.json(selector)` returns strings that share memory with the full selected JSON (for a
 * search: all items). A task key that a user keeps (candidates, goneTasks) would keep that JSON in
 * memory. A heap profile showed this as more than 90% of the k6 memory. A concatenation creates a
 * new string. */
export function ownCopy(value) {
  return ("_" + value).substring(1);
}

/* Returns the sort order of one search for the USER_TASK_ORDER setting. */
export function searchOrder(setting) {
  if (setting === "MIXED") {
    return Math.random() < 0.5 ? "ASC" : "DESC";
  }
  return setting;
}

function nextCandidate() {
  while (candidates.length > 0) {
    const key = candidates.pop();
    if (!goneTasks.has(key)) {
      return key;
    }
  }
  return undefined;
}

function rememberGone(key) {
  goneTasks.add(key);
  if (goneTasks.size > GONE_TASKS_LIMIT) {
    goneTasks.delete(goneTasks.values().next().value);
  }
}

function thinkTime() {
  const [min, max] = USER_TASK_THINK_TIME;
  return min + Math.random() * (max - min);
}

/* Parses "2s-8s", "500ms-2s", or "5s" into [min, max] seconds. */
export function parseThinkTime(value) {
  const toSeconds = (v) => {
    const match = /^(\d+(?:\.\d+)?)(ms|s)$/.exec(v.trim());
    if (!match) {
      throw new Error(`Invalid think time: ${value}`);
    }
    return match[2] === "ms"
      ? parseFloat(match[1]) / 1000
      : parseFloat(match[1]);
  };
  const parts = value.split("-").map(toSeconds);
  return parts.length === 1 ? [parts[0], parts[0]] : [parts[0], parts[1]];
}

export function apiMix() {
  const instances = client.post("/v2/process-instances/search", {
    filter: { processDefinitionId: PROCESS_ID },
    sort: [{ field: "startDate", order: "DESC" }],
    page: { limit: 10 },
  });
  const processInstanceKey = firstItem(instances, "processInstanceKey");
  if (processInstanceKey) {
    getByKey("/v2/process-instances/{key}", processInstanceKey);
    getByKey("/v2/process-instances/{key}/sequence-flows", processInstanceKey);
    getByKey(
      "/v2/process-instances/{key}/statistics/element-instances",
      processInstanceKey,
    );

    const elements = client.post("/v2/element-instances/search", {
      filter: { processInstanceKey },
      sort: [{ field: "startDate", order: "ASC" }],
      page: { limit: 50 },
    });
    const elementInstanceKey = firstItem(elements, "elementInstanceKey");
    if (elementInstanceKey) {
      getByKey("/v2/element-instances/{key}", elementInstanceKey);
    }

    const variables = client.post("/v2/variables/search", {
      filter: { processInstanceKey },
      sort: [{ field: "name", order: "ASC" }],
      page: { limit: 50 },
    });
    const variableKey = firstItem(variables, "variableKey");
    if (variableKey) {
      getByKey("/v2/variables/{key}", variableKey);
    }
  }

  const incidents = client.post("/v2/incidents/search", {
    sort: [{ field: "creationTime", order: "DESC" }],
    page: { limit: 10 },
  });
  const incidentKey = firstItem(incidents, "incidentKey");
  if (incidentKey) {
    getByKey("/v2/incidents/{key}", incidentKey);
  }

  const definitions = client.post("/v2/process-definitions/search", {
    filter: { processDefinitionId: PROCESS_ID },
    page: { limit: 10 },
  });
  const processDefinitionKey = firstItem(definitions, "processDefinitionKey");
  if (processDefinitionKey) {
    getByKey("/v2/process-definitions/{key}", processDefinitionKey);
    getByKey("/v2/process-definitions/{key}/xml", processDefinitionKey);
    checkStatus(
      client.post(
        `/v2/process-definitions/${processDefinitionKey}/statistics/element-instances`,
        { filter: { state: "ACTIVE" } },
        {
          tags: {
            name: "/v2/process-definitions/{key}/statistics/element-instances",
          },
        },
      ),
      200,
    );
  }

  checkStatus(
    client.post("/v2/decision-instances/search", {
      sort: [{ field: "evaluationDate", order: "DESC" }],
      page: { limit: 10 },
    }),
    200,
  );

  const tasks = client.post("/v2/user-tasks/search", {
    filter: { state: "CREATED", processDefinitionId: PROCESS_ID },
    sort: [{ field: "creationDate", order: "DESC" }],
    page: { limit: 10 },
  });
  const userTaskKey = firstItem(tasks, "userTaskKey");
  if (userTaskKey) {
    checkStatus(
      client.post(
        `/v2/user-tasks/${userTaskKey}/variables/search`,
        { page: { limit: 50 } },
        { tags: { name: "/v2/user-tasks/{key}/variables/search" } },
      ),
      200,
    );
  }
}

function getByKey(template, key) {
  return checkStatus(
    client.get(template.replace("{key}", key), { tags: { name: template } }),
    200,
  );
}

function firstItem(response, field) {
  if (!checkStatus(response, 200)) {
    return undefined;
  }
  const items = response.json("items");
  return items && items.length > 0 ? items[0][field] : undefined;
}

function checkStatus(response, expected) {
  check(response, {
    [`status is ${expected}`]: (r) => r.status === expected,
  });
  return response.status === expected;
}
