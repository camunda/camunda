import { check } from "k6";

import { Client } from "./lib.camunda.js";
import { loadData } from "./lib.data.js";

// Module-level code runs once per VU, so each VU has its own client.
const client = new Client();

const FILENAME = "micro.bpmn";
const bpmn = loadData(FILENAME);

export const options = {
  // Give enough time for Camunda to be ready before continuing with the test.
  setupTimeout: "15m",
  scenarios: {
    /* Poll the topology endpoint to check whether the cluster is alive.
     * This is very basic, but should also work almost all the time. */
    topology: {
      exec: "checkTopology",
      executor: "constant-arrival-rate",
      duration: "60d",
      rate: 10,
      timeUnit: "1s",
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
    deploy: {
      exec: "deploy",
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
}

export function checkTopology() {
  const response = client.topology();
  check(response, { "status is 200": (r) => r.status === 200 });
}

export function deploy() {
  /* Sanity check of the `/v2/deployments` API, not a load test: the same
   * unchanged resource is deployed on purpose, so Zeebe deduplicates it. */
  const response = client.deploy(FILENAME, bpmn);
  check(response, { "status is 200": (r) => r.status === 200 });
}
