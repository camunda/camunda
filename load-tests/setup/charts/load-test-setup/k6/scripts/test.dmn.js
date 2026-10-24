import { check, sleep } from "k6";

import { Client } from "./lib.camunda.js";
import { loadData } from "./lib.data.js";

// Module-level code runs once per VU, so each VU has its own client.
const client = new Client();

const DMN_RESOURCE_NAME = "small_decision";
const DMN_FILENAME = `${DMN_RESOURCE_NAME}.dmn`;
const dmn = loadData(DMN_FILENAME);

// Matches the first rule of the decision table, so every evaluation produces an output.
const DMN_VARIABLES = { category: "a" };

// The load profile is configurable through environment variables (see `k6.tests.dmn.env` in
// the chart values), so a scenario can push the throughput without changing the script.
// When the test runs with `parallelism > 1`, k6 splits the rate and the VUs between the runners:
// these values are the totals for the whole test.
const DMN_RATE = parseInt(__ENV.DMN_RATE || "1");
const DMN_RAMP_UP = __ENV.DMN_RAMP_UP || "1m";
const DMN_DURATION = __ENV.DMN_DURATION || "60d";
const DMN_PRE_ALLOCATED_VUS = parseInt(__ENV.DMN_PRE_ALLOCATED_VUS || "1");
const DMN_MAX_VUS = parseInt(__ENV.DMN_MAX_VUS || "20");

export const options = {
  // Give enough time for Camunda to be ready before continuing with the test.
  setupTimeout: "15m",
  scenarios: {
    evaluateDMN: {
      exec: "evaluateDMN",
      // Ramp up to the target rate, so the cluster flow control adapts gradually instead of
      // rejecting a sudden burst, then hold it.
      executor: "ramping-arrival-rate",
      startRate: 0,
      timeUnit: "1s",
      stages: [
        { target: DMN_RATE, duration: DMN_RAMP_UP },
        { target: DMN_RATE, duration: DMN_DURATION },
      ],
      preAllocatedVUs: DMN_PRE_ALLOCATED_VUS,
      maxVUs: DMN_MAX_VUS,
    },
  },
};

export function setup() {
  client.waitUntilReady();

  const retryInterval = 5; // seconds
  let setupDone = false;

  // Deploy the DMN file and wait for the decision definition evaluation to be ready.
  while (!setupDone) {
    try {
      console.log(`Deploying DMN file: ${DMN_FILENAME}...`);

      let response = client.deploy(DMN_FILENAME, dmn);
      if (response.status !== 200) {
        throw new Error(
          `Deploy DMN file ${DMN_FILENAME} failed. Status: ${response.status}, Body: ${response.body}`,
        );
      }

      console.log(`DMN file ${DMN_FILENAME} deployed successfully.`);

      console.log(`Evaluating decision definition: ${DMN_RESOURCE_NAME}...`);
      response = client.decisionDefinitionEvaluation(
        DMN_RESOURCE_NAME,
        DMN_VARIABLES,
      );
      if (response.status !== 200) {
        throw new Error(
          `Decision definition evaluation ${DMN_RESOURCE_NAME} failed. Status: ${response.status}, Body: ${response.body}`,
        );
      }

      console.log("Decision definition evaluation is ready.");
      setupDone = true;
    } catch (e) {
      console.error(
        `Failed to setup test. Retrying in ${retryInterval}s...`,
        e,
      );
      sleep(retryInterval);
      continue;
    }
  }

  console.log("Setup completed successfully, will start the tests now.");
}

export function evaluateDMN() {
  const response = client.decisionDefinitionEvaluation(
    DMN_RESOURCE_NAME,
    DMN_VARIABLES,
  );
  check(response, { "status is 200": (r) => r.status === 200 });
}
