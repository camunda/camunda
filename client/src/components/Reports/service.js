import {get, post, del} from 'request';

export async function loadReports() {
  const response = await get('/api/report');

  return await response.json();
}

export async function loadSingleReport(id) {
  const response = await get('/api/report/' + id);

  return await response.json();
}

export async function create() {
  const response = await post('/api/report');

  const json = await response.json();

  return json.id;
}

export async function remove(id) {
  return await del(`/api/report/${id}`);
}
