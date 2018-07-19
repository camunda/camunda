import {get} from 'request';

export async function getCamundaEndpoints() {
  const response = await get('/api/camunda');
  return await response.json();
}

export function getRelativeValue(data, total) {
  return Math.round(data / total * 1000) / 10 + '%';
}
