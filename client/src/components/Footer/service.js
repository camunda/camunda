import {get} from 'request';

export async function getImportProgress() {
  const response = await get('/api/status/import-progress');

  return await response.json();
}

export async function getConnectionStatus() {
  const response = await get('/api/status/connection');

  return await response.json();
}
