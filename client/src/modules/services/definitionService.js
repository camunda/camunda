import {get} from 'request';

export function extractDefinitionName(key, xml) {
  return (
    new DOMParser()
      .parseFromString(xml, 'text/xml')
      .querySelector(`[id="${key}"]`)
      .getAttribute('name') || key
  );
}

export async function loadDefinitions(type) {
  const response = await get(`api/${type}-definition/groupedByKey`);

  return await response.json();
}

export async function loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersion) {
  const response = await get('api/process-definition/xml', {
    processDefinitionKey,
    processDefinitionVersion
  });

  return await response.text();
}

export async function loadDecisionDefinitionXml(key, version) {
  const response = await get('api/decision-definition/xml', {
    key,
    version
  });

  return await response.text();
}
