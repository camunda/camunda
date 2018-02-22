import {get} from 'request';

export async function loadProcessDefinitions() {
  const response = await get('/api/process-definition/groupedByKey');

  return await response.json();
}