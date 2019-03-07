import {get} from 'request';

export async function loadEntity(api, numResults, sortBy) {
  const url = `api/${api}`;

  const params = {};
  if (numResults) {
    params['numResults'] = numResults;
  }

  if (sortBy) {
    params['orderBy'] = sortBy;
  }

  const response = await get(url, params);
  return await response.json();
}

export async function checkDeleteConflict(id, entity) {
  const response = await get(`api/${entity}/${id}/delete-conflicts`);
  return await response.json();
}
