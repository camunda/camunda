import { check } from 'k6';

import * as auth from './lib.auth.js';
import * as helpers from './lib.helpers.js';
import * as camunda from './lib.camunda.js';

export const options = {
  // Give enough time for Camunda to be ready before continuing with the test.
  setupTimeout: '15m',
  scenarios: {
    /* Poll the topology endpoint to check whether the cluster is alive.
     * This is very basic, but should also work almost all the time. */
    topology: {
      exec: 'checkTopology',
      executor: 'constant-arrival-rate',
      duration: '60d',
      rate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
  },
};

export async function setup() {
  return helpers.setupContext();
}

export async function checkTopology(context) {
  auth.renew(context.token);
  const response = await camunda.topology(context);
  check(response, { 'status is 200': (r) => r.status === 200 });
};
