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
