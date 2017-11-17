import {get} from 'request';

export async function loadVariables(id) {
  const response = await get(`/api/variables/${id}`);

  return await response.json();
}

export async function loadValues(id, name, type, resultOffset, numResults) {
  const response = await get(`/api/variables/${id}/values`, {
    name,
    type,
    resultOffset,
    numResults
  });

  return await response.json();
}
