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
    searchInstances: {
      exec: 'searchProcessInstances',
      executor: 'constant-arrival-rate',
      duration: '7d',
      rate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
  },
};

export async function setup() {
  return helpers.setupContext();
}

// List of process definitions we want to search for.
let processDefinitionIds = [];

// https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-process-instances/
export async function searchProcessInstances(context) {
  const token = auth.renew(context.token);

  // Process definitions are listed until at least one has been found, then kept for the
  // lifetime of the VU.
  if (processDefinitionIds.length === 0) {
    const processDefinitionsResponse = await camunda.listProcessDefinitions(context);
    if (processDefinitionsResponse.status !== 200) {
      console.error(`Unable to list process definitions, got HTTP status=${processDefinitionsResponse.status}`);
      return;
    }
    processDefinitionIds = processDefinitionsResponse.json().items.map((pd) => pd.processDefinitionId);
    if (processDefinitionIds.length === 0) {
      // Wait until we find at least one process definition.
      return;
    }
    console.log(`Found ${processDefinitionIds.length} process definitions, will not search for them anymore.`);
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
      // Search only the first process definition discovered earlier..
      processDefinitionId: processDefinitionIds[0],
    },
  };
  const response = http.post(context.baseURL + endpoint, JSON.stringify(payload), params);
  check(response, { 'status is 200': (r) => r.status === 200 });
};
