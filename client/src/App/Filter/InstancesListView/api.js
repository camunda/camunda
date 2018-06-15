import {post} from 'modules/request';

export async function getData(filter, firstResult, maxResults) {
  const url = `/api/workflow-instances?firstResult=${firstResult}&maxResults=${maxResults}`;
  const response = await post(url, filter);

  return await response.json();
}
