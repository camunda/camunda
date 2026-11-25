/* Benchmarks the engine cost of the user task lookup (`POST /v2/user-tasks/{userTaskKey}/lookup`).
 *
 * The lookup reads an active user task and all its variables from the engine state, and writes a
 * command and an event to the log, so it competes with process execution for the partition. Run
 * it next to the `realistic` scenario, and compare against a run without it to find which read
 * rate a partition sustains.
 *
 * Setup creates a pool of process instances that wait in a user task forever: nobody completes
 * them, they are only read. The pool is reused when the test restarts. The lookup rate then ramps
 * through a series of plateaus, and drops to 0 afterwards so the recovery can be observed.
 *
 * The client needs the READ_USER_TASK permission, which the load test setup grants.
 *
 * Settings, as environment variables (`k6.tests.user-task-lookup.env.<NAME>` in the chart):
 *
 * * POOL_SIZE (default 9000): number of active user tasks to read from. Instance creation spreads
 *   them over the partitions. The pool is large so that reads do not stay in a hot cache.
 * * LOOKUP_RATES (default 30:150:300:750:1500:3000): total lookup rates (requests per second) of
 *   the plateaus, separated by colons. Colons, because commas need escaping in Helm `--set`.
 * * LOOKUP_PLATEAU (default 15m): how long each rate is held.
 * * LOOKUP_RAMP (default 1m): ramp time to each plateau.
 */
import { check, sleep } from "k6";

import { Client } from "./lib.camunda.js";
import { loadData } from "./lib.data.js";

// Module-level code runs once per VU, so each VU has its own client.
const client = new Client();

const PROCESS_ID = "k6-user-task-lookup";
const BPMN_FILENAME = "user-task-lookup.bpmn";
const PAYLOAD_FILENAME = "user-task-lookup-payload.json";
const bpmn = loadData(BPMN_FILENAME);
const payloadJson = loadData(PAYLOAD_FILENAME);

// Must match the page limit of `Client.searchUserTasks`.
const SEARCH_PAGE_SIZE = 1000;

const DEFAULT_RATES = "30:150:300:750:1500:3000";

/* Parse colon-separated request rates, e.g. `30:150:300`. */
export function parseRates(text) {
  const rates = text.split(":");
  if (!rates.every((rate) => /^\d+$/.test(rate))) {
    throw new Error(
      `Invalid LOOKUP_RATES "${text}": expected non-negative integers separated by colons, e.g. 30:150:300`,
    );
  }
  return rates.map(Number);
}

/* Ramp to each rate and hold it, then ramp down and stay idle. */
export function buildStages(rates, plateau, ramp) {
  const stages = rates.flatMap((rate) => [
    { target: rate, duration: ramp },
    { target: rate, duration: plateau },
  ]);
  stages.push(
    { target: 0, duration: ramp },
    // Stay idle (for as long as the longest benchmark we run) to observe the recovery.
    { target: 0, duration: "60d" },
  );
  return stages;
}

export const options = {
  // Creating the pool and waiting for it to be exported can take a while.
  setupTimeout: "30m",
  scenarios: {
    lookup: {
      exec: "lookup",
      executor: "ramping-arrival-rate",
      startRate: 0,
      timeUnit: "1s",
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: buildStages(
        parseRates(__ENV.LOOKUP_RATES || DEFAULT_RATES),
        __ENV.LOOKUP_PLATEAU || "15m",
        __ENV.LOOKUP_RAMP || "1m",
      ),
    },
  },
};

/* Page through the active user tasks of the process, and return their keys. */
function searchPoolKeys() {
  const waitTime = 10; // seconds
  const keys = [];
  let after;
  while (true) {
    const response = client.searchUserTasks(PROCESS_ID, after);
    if (response.status !== 200) {
      console.error(
        `Unable to search user tasks (will retry in ${waitTime}s), got HTTP status=${response.status}`,
      );
      sleep(waitTime);
      continue;
    }
    const { items, page } = response.json();
    keys.push(...items.map((item) => item.userTaskKey));
    if (items.length < SEARCH_PAGE_SIZE) {
      return keys;
    }
    after = page.endCursor;
  }
}

export function setup() {
  const poolSize = parseInt(__ENV.POOL_SIZE || "9000", 10);
  if (!(poolSize > 0)) {
    throw new Error(`Invalid POOL_SIZE "${__ENV.POOL_SIZE}"`);
  }
  const waitTime = 10; // seconds

  client.waitUntilReady();

  // Zeebe deduplicates the deployment when the model is unchanged.
  while (true) {
    const response = client.deploy(BPMN_FILENAME, bpmn);
    if (response.status === 200) {
      break;
    }
    console.error(
      `Unable to deploy ${BPMN_FILENAME} (will retry in ${waitTime}s), got HTTP status=${response.status}`,
    );
    sleep(waitTime);
  }

  // The pool may already exist when the test is restarted.
  let userTaskKeys = searchPoolKeys();
  const missing = poolSize - userTaskKeys.length;
  console.log(
    `Found ${userTaskKeys.length} existing user tasks, will create ${Math.max(missing, 0)} instances to reach ${poolSize}.`,
  );

  const payload = JSON.parse(payloadJson);
  for (let created = 0; created < missing;) {
    const response = client.createProcessInstance(PROCESS_ID, payload);
    if (response.status !== 200) {
      console.error(
        `Unable to create a process instance (will retry), got HTTP status=${response.status}`,
      );
      sleep(1);
      continue;
    }
    created++;
    if (created % 1000 === 0) {
      console.log(`Created ${created}/${missing} instances.`);
    }
  }

  // The user tasks only show up in the search once they are exported.
  if (missing > 0) {
    userTaskKeys = searchPoolKeys();
  }
  while (userTaskKeys.length < poolSize) {
    console.log(
      `Waiting for the user tasks to be searchable: ${userTaskKeys.length}/${poolSize} (will retry in ${waitTime}s).`,
    );
    sleep(waitTime);
    userTaskKeys = searchPoolKeys();
  }
  console.log(`The pool has ${userTaskKeys.length} user tasks.`);

  return { userTaskKeys };
}

// Per-VU position in the pool. It starts at a random offset so that the VUs do not read the same
// keys in lockstep.
let next = Math.floor(Math.random() * 2 ** 31);

export function lookup(data) {
  const keys = data.userTaskKeys;
  const response = client.lookupUserTask(keys[next++ % keys.length]);
  check(response, { "status is 200": (r) => r.status === 200 });
}
