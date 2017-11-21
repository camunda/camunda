import {get} from 'request';

export async function getImportProgress() {
  const response = await get('/api/status/import-progress');

  return await response.json();
}
