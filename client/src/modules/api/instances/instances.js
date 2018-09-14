import {post, get} from 'modules/request';
import {OPERATION_TYPE} from 'modules/constants';

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
  const response = await get('/api/workflows/grouped');

  return await response.json();
}

export async function fetchWorkflowInstancesCount(payload) {
  const url = `${URL}?firstResult=${0}&maxResults=${1}`;
  const response = await post(url, {
    queries: [{...payload}]
  });
  const resJson = await response.json();
  return resJson.totalCount;
}

export async function fetchWorkflowInstanceBySelection(payload) {
  const url = `${URL}?firstResult=${0}&maxResults=${10}`;
  const response = await post(url, {queries: [...payload.queries]});
  return await response.json();
}

export async function retryInstances(queries) {
  const url = `${URL}/operation`;
  const payload = {operationType: OPERATION_TYPE.UPDATE_RETRIES, queries};

  await post(url, payload);
}
