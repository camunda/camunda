import {get} from 'request';

export async function getCamundaEndpoint() {
  const response = await get('/api/camunda');
  return await response.text();
}
