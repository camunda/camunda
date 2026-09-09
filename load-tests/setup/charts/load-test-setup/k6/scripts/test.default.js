import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

import { createToken, renew, ensureEnvVars } from './lib.helpers.js';


export const options = {
  scenarios: {
    topology: {
      exec: 'checkTopology',
      executor: 'constant-vus',
      duration: '1d',
      vus: 1,
    },
    searchInstances: {
      exec: 'checkForProcessInstances',
      executor: 'constant-vus',
      duration: '1d',
      vus: 2,
    },
  },
};

export async function setup() {
  ensureEnvVars();

  const token = createToken();
  // The returned context object must be JSON serializable.
  const context = {
    token: token,
  };
  return context;
}

export async function checkTopology(context) {
  const token = renew(context.token);
  const endpoint = '/v1/topology';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + token.accessToken,
    },
    tags: { name: endpoint },
  };

  http.get(__ENV.CAMUNDA_BASE_URL + endpoint, params);
  sleep(1);
};


// https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-process-instances/
export async function checkForProcessInstances(context) {
  const token = renew(context.token);

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
      {
        field: "startDate",
        order: "DESC",
      },
    ],
    filter: {
      processDefinitionId: "benchmark",
    },
  };

  http.post(__ENV.CAMUNDA_BASE_URL + endpoint, JSON.stringify(payload), params);
  sleep(1);
};
