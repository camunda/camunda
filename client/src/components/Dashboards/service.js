import {get, post, del} from 'request';

export async function loadDashboards() {
  const response = await get('/api/dashboard');

  return await response.json();
}

export async function loadDashboard(id) {
  const response = await get('/api/dashboard/' + id);

  return await response.json();
}

export async function create() {
  const response = await post('/api/dashboard');

  const json = await response.json();

  return json.id;
}

export async function remove(id) {
  return await del(`/api/dashboard/${id}`);
}
