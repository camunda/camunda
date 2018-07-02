import {post, get} from 'modules/request';

const URL = '/api/workflow-instances';

export async function fetchWorkflowInstance(id) {
  const response = await get(`${URL}/${id}`);
  return await response.json();
}

export async function fetchWorkflowInstances(filter, firstResult, maxResults) {
  const url = `${URL}?firstResult=${firstResult}&maxResults=${maxResults}`;
  const response = await post(url, filter);

  return await response.json();
}

export async function fetchWorkflowInstancesCount(payload) {
  const response = await post(`${URL}/count`, payload);
  const resJson = await response.json();
  return resJson.count;
}
