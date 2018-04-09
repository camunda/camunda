import {get} from 'request';

export default async function loadProcessDefinitions() {
  const response = await get('/api/process-definition/groupedByKey');

  return await response.json();
}
