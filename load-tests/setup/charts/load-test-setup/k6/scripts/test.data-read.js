import { check, sleep } from 'k6';

import { Client } from './lib.camunda.js';

// Module-level code runs once per VU, so each VU has its own client.
const client = new Client();

/* Tests reading data from Camunda, which requires a secondary storage.
 * This test is disabled when running with `secondary_storage=none`. */
export const options = {
  // Give enough time for Camunda to be ready and for a process definition to
  // exist before continuing with the test.
  setupTimeout: '20m',
  scenarios: {
    /* Search for process instances.
     * Run a basic search for process instances at a low rate. */
    searchProcessInstances: {
      exec: 'searchProcessInstances',
      executor: 'constant-arrival-rate',
      duration: '60d', // As long as the longest benchmark we run
      rate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
  },
};

export async function setup() {
  await client.waitUntilReady();

  // Process definition to search process instances for. Retries until one exists.
  const waitTime = 10; // seconds
  let processDefinitionId;
  while (processDefinitionId === undefined) {
    const response = await client.searchProcessDefinitions();
    if (response.status == 200) {
      const items = response.json().items;
      if (items.length > 0) {
        processDefinitionId = items[0].processDefinitionId;
        break;
      }
      console.info(`No process definition found yet (will retry in ${waitTime}s).`);
    } else {
      console.error(`Unable to list process definitions (will retry in ${waitTime}s), got HTTP status=${response.status}`);
    }
    sleep(waitTime);
  }
  console.log(`Will search process instances from process definition ${processDefinitionId}.`);

  return { processDefinitionId };
}

export async function searchProcessInstances(data) {
  const response = await client.searchProcessInstances(data.processDefinitionId);
  check(response, { 'status is 200': (r) => r.status === 200 });
};
