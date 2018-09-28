import {get} from 'request';

export async function getOptimizeVersion() {
  const response = await get('api/meta/version');

  const payload = await response.json();
  return payload.optimizeVersion;
}
