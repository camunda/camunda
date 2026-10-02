import http from 'k6/http';
import { check } from 'k6';

import * as auth from './lib.auth.js';
import * as helpers from './lib.helpers.js';
import * as camunda from './lib.camunda.js';

/* Tests reading data from Camunda, which requires a secondary storage.
 * This test is disabled when running with `secondary_storage=none`. */
export const options = {
  // Give enough time for Camunda to be ready before continuing with the test.
  setupTimeout: '15m',
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
  return helpers.setupContext();
}

// Process definition to search for, kept for the lifetime of the VU.
let processDefinitionId = null;

// https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-process-instances/
export async function searchProcessInstances(context) {
  const token = auth.renew(context.token);

  if (processDefinitionId === null) {
    const processDefinitionsResponse = await camunda.listProcessDefinitions(context);
    if (processDefinitionsResponse.status !== 200) {
      console.error(`Unable to list process definitions, got HTTP status=${processDefinitionsResponse.status}`);
      return;
    }
    const items = processDefinitionsResponse.json().items;
    if (items.length === 0) {
      // Wait until we find at least one process definition.
      return;
    }
    processDefinitionId = items[0].processDefinitionId;
    console.log(`Will searching process instances from process definition ${processDefinitionId}.`);
  }

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
      processDefinitionId: processDefinitionId,
    },
  };
  const response = http.post(context.baseURL + endpoint, JSON.stringify(payload), params);
  check(response, { 'status is 200': (r) => r.status === 200 });
};
