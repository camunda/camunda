import {post} from 'modules/request';

export async function fetchWorkflowInstancesCount(filter) {
  const response = await post('/api/workflow-instances/count', filter);

  return (await response.json()).count;
}

export async function fetchWorkflowInstances(filter, firstResult, maxResults) {
  const url = `/api/workflow-instances?firstResult=${firstResult}&maxResults=${maxResults}`;
  const response = await post(url, filter);

  return await response.json();
}
