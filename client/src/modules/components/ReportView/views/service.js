import {get} from 'request';

export async function getCamundaEndpoints() {
  const response = await get('/api/camunda');
  return await response.json();
}
