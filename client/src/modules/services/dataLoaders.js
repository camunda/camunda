/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'request';

export async function getOptimizeVersion() {
  const response = await get('api/meta/version');

  const payload = await response.json();
  return payload.optimizeVersion;
}

export async function getFlowNodeNames(processDefinitionKey, processDefinitionVersion, tenantId) {
  if (processDefinitionKey && processDefinitionVersion) {
    const payload = {
      processDefinitionKey,
      processDefinitionVersion
    };

    if (tenantId) {
      payload.tenantId = tenantId;
    }

    const response = await post(`api/flow-node/flowNodeNames`, payload);

    const json = await response.json();

    return await json.flowNodeNames;
  } else {
    return {};
  }
}

export async function loadDefinitions(type) {
  const response = await get(`api/${type}-definition/definitionVersionsWithTenants`);

  return await response.json();
}

export async function loadProcessDefinitionXml(
  processDefinitionKey,
  processDefinitionVersion,
  tenantId
) {
  const payload = {
    processDefinitionKey,
    processDefinitionVersion
  };

  if (tenantId) {
    payload.tenantId = tenantId;
  }
  const response = await get('api/process-definition/xml', payload);

  return await response.text();
}

export async function loadDecisionDefinitionXml(key, version, tenantId) {
  const payload = {key, version};
  if (tenantId) {
    payload.tenantId = tenantId;
  }
  const response = await get('api/decision-definition/xml', payload);

  return await response.text();
}

export async function checkDeleteConflict(id, entity) {
  const response = await get(`api/${entity}/${id}/delete-conflicts`);
  return await response.json();
}
