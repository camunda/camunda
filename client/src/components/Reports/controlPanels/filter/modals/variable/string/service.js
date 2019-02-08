import {get} from 'request';

export async function loadValues(
  processDefinitionKey,
  processDefinitionVersion,
  name,
  type,
  resultOffset,
  numResults,
  valueFilter
) {
  const response = await get(`api/variables/values`, {
    processDefinitionKey,
    processDefinitionVersion,
    name,
    type,
    resultOffset,
    numResults,
    valueFilter
  });

  return await response.json();
}
