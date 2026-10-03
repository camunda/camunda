import http from 'k6/http';
import { check, sleep } from 'k6';

import * as auth from './lib.auth.js';
import * as helpers from './lib.helpers.js';
import * as camunda from './lib.camunda.js';

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
  const context = await helpers.setupContext();

  // Process definition to search process instances for. Retries until one exists.
  const waitTime = 10; // seconds
  while (context.processDefinitionId === undefined) {
    auth.renew(context.token);
    const response = await camunda.listProcessDefinitions(context);
    if (response.status !== 200) {
      console.error(`Unable to list process definitions (will retry in ${waitTime}s), got HTTP status=${response.status}`);
    } else {
      const items = response.json().items;
      if (items.length > 0) {
        context.processDefinitionId = items[0].processDefinitionId;
        break;
      }
      console.info(`No process definition found yet (will retry in ${waitTime}s).`);
    }
    sleep(waitTime);
  }
  console.log(`Will search process instances from process definition ${context.processDefinitionId}.`);

  return context;
}

// https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-process-instances/
export async function searchProcessInstances(context) {
  const token = auth.renew(context.token);

  const endpoint = '/v2/process-instances/search';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + token.accessToken,
    },
    tags: { name: endpoint },
  };
  const payload = {
    sort: [
      {field: "startDate", order: "DESC"},
    ],
    filter: {
      processDefinitionId: context.processDefinitionId,
      $or: [
        { state: "ACTIVE" },
        { hasIncident: true },
      ],
    },
    page: {
      limit: 100,
    },
  };
  const response = http.post(context.baseURL + endpoint, JSON.stringify(payload), params);
  check(response, { 'status is 200': (r) => r.status === 200 });
};
