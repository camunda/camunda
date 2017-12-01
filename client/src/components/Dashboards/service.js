import {get, del, put} from 'request';

export async function loadDashboard(id) {
  const response = await get('/api/dashboard/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`/api/dashboard/${id}`);
}

export async function update(id, state) {
    return await put(`/api/dashboard/${id}`, state);
}

export async function loadReports() {
  const response = await get('/api/report');

  return await response.json();
}

export async function loadReport(id) {
  const response = await get(`/api/report/${id}/evaluate`);

  return await response.json();
}
