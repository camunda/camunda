import {get, del} from 'request';

export async function loadDashboard(id) {
  const response = await get('/api/dashboard/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`/api/dashboard/${id}`);
}
