import {get, post, del} from 'request';

export async function load(api, numResults, sortBy) {
  const url = `/api/${api}`;
  const apis = api + 's';

  const params = {};
  if (numResults) {
    params['numResults'] = numResults;
  }

  if (sortBy) {
    params['orderBy'] = sortBy;
  }

  const response = await get(url, params);
  const json = await response.json();

  const idList = json.map(entry => entry.id);

  const shareStatusResponse = await post(`/api/share/status`, {[apis]: idList});
  const shareStatus = await shareStatusResponse.json();

  return json.map(entry => ({
    ...entry,
    shared: shareStatus[apis][entry.id]
  }));
}

export async function create(api) {
  const response = await post(`/api/${api}`);

  const json = await response.json();

  return json.id;
}

export async function remove(id, api) {
  return await del(`/api/${api}/${id}`);
}
