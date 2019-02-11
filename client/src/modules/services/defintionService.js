import {get} from 'request';

export function extractProcessDefinitionName(processDefinitionXml) {
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(processDefinitionXml, 'text/xml');

  const processNode = xmlDoc.getElementsByTagNameNS('*', 'process')[0];
  return processNode ? processNode.getAttribute('name') || '' : '';
}

export async function loadProcessDefinitions() {
  const response = await get('api/process-definition/groupedByKey');

  return await response.json();
}

export async function loadDecisionDefinitions() {
  const response = await get('api/decision-definition/groupedByKey');

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
