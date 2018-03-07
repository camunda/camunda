import {get} from 'request';

export async function loadVariables(processDefinitionKey, processDefinitionVersion) {
  const response = await get(`/api/variables`, {processDefinitionKey, processDefinitionVersion});

  return await response.json();
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
