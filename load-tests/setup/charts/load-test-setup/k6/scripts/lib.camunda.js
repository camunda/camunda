/* Wrapper for the Camunda API.
 *
 * Expose the Camunda API endpoints via convenient async functions to be used during tests.
 *
 * All these functions assumes the authentication token is valid and has been checked before.
 */
import http from 'k6/http';

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
