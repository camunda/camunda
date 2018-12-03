import {post, del, put} from 'request';
import {loadEntity} from 'services';

export async function load(api, numResults, sortBy) {
  const json = await loadEntity(api, numResults, sortBy);
  const apis = api + 's';

  const idList = json.map(entry => entry.id);

  const shareStatusResponse = await post(`api/share/status`, {[apis]: idList});
  const shareStatus = await shareStatusResponse.json();

  if (!shareStatus[apis]) return json;

  return json.map(entry => ({
    ...entry,
    shared: shareStatus[apis][entry.id]
  }));
}

export async function create(api, data) {
  const response = await post(`api/${api}`, data);

  const json = await response.json();

  return json.id;
}

export async function duplicate(api, copyData) {
  const idOfCopy = await create(api, copyData);
  return await update(api, idOfCopy, copyData);
}

export async function remove(id, api) {
  return await del(`api/${api}/${id}`, {force: api === 'report'});
}

export async function update(api, id, data) {
  return await put(`api/${api}/${id}`, data);
}
