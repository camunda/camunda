import {get} from 'request';

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
