import {post, get} from 'modules/request';

const URL = '/api/workflow-instances';

export async function fetchWorkflowInstance(id) {
  const response = await get(`${URL}/${id}`);
  return await response.json();
}

export async function fetchWorkflowInstances(options) {
  const {firstResult, maxResults, ...payload} = options;
  const url = `${URL}?firstResult=${firstResult}&maxResults=${maxResults}`;
  const response = await post(url, payload);

  return await response.json();
}

export async function fetchGroupedWorkflowInstances() {
  try {
    const response = await get('/api/workflows/grouped');
    return await response.json();
  } catch (e) {
    return [];
  }
}

export async function fetchWorkflowInstancesCount(payload) {
  const url = `${URL}?firstResult=${0}&maxResults=${0}`;
  const response = await post(url, {
    queries: [{...payload}]
  });
  const resJson = await response.json();
  return resJson.totalCount;
}

export async function fetchWorkflowInstanceBySelection(payload) {
  let query = payload.queries[0];

  if (query.ids) {
    query = {
      ...payload.queries[0],
      running: true,
      active: true,
      canceled: true,
      completed: true,
      finished: true,
      incidents: true
    };
  }

  const url = `${URL}?firstResult=${0}&maxResults=${10}`;
  const response = await post(url, {queries: [...payload.queries]});
  return await response.json();
}

export async function fetchWorkflowInstancesStatistics(payload) {
  const url = `${URL}/statistics`;
  const response = await post(url, {queries: [...payload.queries]});
  return await response.json();
}

/**
 * @param {*} operationType constants specifying the operation to be applied.
 * @param {*} queries object with query params.
 */
export async function applyOperation(operationType, queries) {
  const url = `${URL}/operation`;
  const payload = {operationType, queries};

  await post(url, payload);
}
