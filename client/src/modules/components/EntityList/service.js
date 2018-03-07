import {get, post, del} from 'request';

export async function load(api, numResults, sortBy) {
  let url = `/api/${api}`;

  let params = {};
  if (numResults) {
    params['numResults'] = numResults;
  }

  if (sortBy) {
    params['orderBy'] = sortBy;
  }

  const response = await get(url, params);

  return await response.json();
}

export async function create(api) {
  const response = await post(`/api/${api}`);

  const json = await response.json();

  return json.id;
}

export async function remove(id, api) {
  return await del(`/api/${api}/${id}`);
}
