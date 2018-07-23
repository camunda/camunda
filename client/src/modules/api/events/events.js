import {post} from 'modules/request';

const URL = `/api/events`;

export async function fetchEvents(workflowInstanceId) {
  const response = await post(URL, {workflowInstanceId});
  return await response.json();
}
