import {get} from 'request';
import {getDataKeys} from 'services';

export async function loadVariables(processDefinitionKey, processDefinitionVersion) {
  const response = await get('api/variables', {
    processDefinitionKey,
    processDefinitionVersion,
    namePrefix: '',
    sortOrder: 'asc',
    orderBy: 'name'
  });

  return await response.json();
}

export function isChecked(data, current) {
  return (
    current &&
    getDataKeys(data).every(
      prop =>
        JSON.stringify(current[prop]) === JSON.stringify(data[prop]) || Array.isArray(data[prop])
    )
  );
}
