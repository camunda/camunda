/* Wrapper for the Camunda API.
 *
 * Expose the Camunda API endpoints via convenient async functions to be used during tests.
 *
 * All these functions assumes the authentication token is valid and has been checked before.
 */
import http from 'k6/http';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';

export async function deploy(context, name, resource) {
  const endpoint = '/v2/deployments';

  const payload = new FormData();
  payload.append('resources', http.file(resource, name));

  const params = {
    headers: {
      'Content-Type': 'multipart/form-data; boundary=' + payload.boundary,
      'Authorization': "Bearer " + context.token.accessToken,
    },
    tags: { name: endpoint },
  };

  const response = http.post(context.baseURL + endpoint, payload.body(), params);
  if (response.status !== 200) {
    throw new Error(`Failed to deploy resource. Status: ${response.status}, Body: ${response.body}`);
  }

  console.log(`DMN deployed: ${response.status}, Body: ${response.body}`);

  return response;
}

export async function decisionDefinitionEvaluation(context, decisionDefinitionId, variables={}) {
  const endpoint = '/v2/decision-definitions/evaluation';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + context.token.accessToken,
    },
    tags: { name: endpoint },
  };

  const payload = {
    decisionDefinitionId: decisionDefinitionId,
    variables: variables,
  };

  const response = http.post(context.baseURL + endpoint, JSON.stringify(payload), params);
  return response;
}

export async function listProcessDefinitions(context) {
  // https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/specifications/search-process-definitions/
  // Assume the token is valid and has been checked before.
  const endpoint = '/v2/process-definitions/search';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + context.token.accessToken,
    },
    tags: { name: endpoint },
  };

  const payload = {
    sort: [
      {
        field: "processDefinitionKey",
        order: "ASC",
      },
    ],
    page: {
      limit: 10,
    },
  };

  const response = http.post(context.baseURL + endpoint, JSON.stringify(payload), params);
  return response;
}

// https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/get-topology/
export async function topology(context) {
  // Assume the token is valid and has been checked before.
  const endpoint = '/v2/topology';
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + context.token.accessToken,
    },
    tags: { name: endpoint },
  };

  const response = http.get(context.baseURL + endpoint, params);
  return response;
}
