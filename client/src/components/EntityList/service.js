import {get, post, del} from 'request';

export async function load(api, numResults, sortBy) {
  let url = `/api/${api}`;

  if(numResults) {
    url += `?numResults=${numResults}`;
  }

  if (sortBy) {
    if (url.indexOf('?') >= 0 ) {
      url += `&orderBy=${sortBy}`
    } else {
      url += `?orderBy=${sortBy}`;
    }
  }

  const response = await get(url);

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
