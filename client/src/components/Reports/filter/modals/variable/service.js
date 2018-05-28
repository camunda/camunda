import {get} from 'request';

export function loadVariables(processDefinitionKey, processDefinitionVersion) {
  return async namePrefix => {
    const response = await get(`/api/variables`, {
      processDefinitionKey,
      processDefinitionVersion,
      namePrefix
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
  numResults
) {
  const response = await get(`/api/variables/values`, {
    processDefinitionKey,
    processDefinitionVersion,
    name,
    type,
    resultOffset,
    numResults
  });

  return await response.json();
}
