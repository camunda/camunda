import {get} from 'request';

export function loadVariables(processDefinitionKey, processDefinitionVersion) {
  return async namePrefix => {
    const response = await get(`/api/variables`, {
      processDefinitionKey,
      processDefinitionVersion,
      namePrefix,
      sortOrder: 'asc',
      orderBy: 'name'
    });

    return await response.json();
  };
}

export async function loadValues(
  processDefinitionKey,
  processDefinitionVersion,
  name,
  type,
  resultOffset,
  numResults,
  valuePrefix
) {
  const response = await get(`/api/variables/values`, {
    processDefinitionKey,
    processDefinitionVersion,
    name,
    type,
    resultOffset,
    numResults,
    valuePrefix
  });

  return await response.json();
}
