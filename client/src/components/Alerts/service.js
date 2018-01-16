import {get, post, del, put} from 'request';

export async function loadAlerts() {
  const response = await get('/api/alert');

  return await response.json();
}

export async function loadReports() {
  const response = await get('/api/report');

  return await response.json();
}

export async function saveNewAlert(alert) {
  const response = await post('/api/alert', alert);

  const json = await response.json();

  return json.id;
}

export async function updateAlert(id, alert) {
  return await put('/api/alert/' + id, alert);
}

export async function deleteAlert(id) {
  return await del('api/alert/' + id);
}

