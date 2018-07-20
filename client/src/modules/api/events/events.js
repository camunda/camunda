import {post} from 'modules/request';

const URL = `/api/events?firstResult=0&maxResults=1000`;

export async function fetchEvents(workflowInstanceId) {
  const response = await post(URL, {workflowInstanceId});
  return await response.json();
}
