import { check } from "k6";

import { Client } from "./lib.camunda.js";

// Module-level code runs once per VU, so each VU has its own client.
const client = new Client();

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
  },
};

export async function setup() {
  await client.waitUntilReady();
}

export async function checkTopology() {
  const response = await client.topology();
  check(response, { "status is 200": (r) => r.status === 200 });
}
