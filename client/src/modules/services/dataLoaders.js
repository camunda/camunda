import {get, post} from 'request';

export async function getOptimizeVersion() {
  const response = await get('api/meta/version');

  const payload = await response.json();
  return payload.optimizeVersion;
}

export async function getFlowNodeNames(processDefinitionKey, processDefinitionVersion) {
  const response = await post(`api/flow-node/flowNodeNames`, {
    processDefinitionKey,
    processDefinitionVersion,
    nodeIds: []
  });

  const json = await response.json();

  return await json.flowNodeNames;
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

export async function checkDeleteConflict(id, entity) {
  const response = await get(`api/${entity}/${id}/delete-conflicts`);
  return await response.json();
}
