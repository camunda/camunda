import { check, sleep } from "k6";

import { Client } from "./lib.camunda.js";
import { loadData } from "./lib.data.js";

// Module-level code runs once per VU, so each VU has its own client.
const client = new Client();

const DMN_RESOURCE_NAME = "small_decision";
const DMN_FILENAME = `${DMN_RESOURCE_NAME}.dmn`;
const dmn = loadData(DMN_FILENAME);

export const options = {
  // Give enough time for Camunda to be ready before continuing with the test.
  setupTimeout: "15m",
  scenarios: {
    evaluateDMN: {
      exec: "evaluateDMN",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate: 1,
      timeUnit: "1s",
      preAllocatedVUs: 1,
      maxVUs: 20,
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
      response = client.decisionDefinitionEvaluation(DMN_RESOURCE_NAME);
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
  const response = client.decisionDefinitionEvaluation(DMN_RESOURCE_NAME);
  check(response, { "status is 200": (r) => r.status === 200 });
}
