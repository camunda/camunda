/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post} from 'request';

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

export async function loadDefinitions(type, collectionId) {
  const params = {};
  if (collectionId) {
    params.filterByCollectionScope = collectionId;
  }

  const response = await get(`api/${type}-definition/definitionVersionsWithTenants`, params);

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

  try {
    const response = await get('api/process-definition/xml', payload);

    return await response.text();
  } catch (e) {
    return null;
  }
}

export async function loadDecisionDefinitionXml(key, version, tenantId) {
  const payload = {key, version};
  if (tenantId) {
    payload.tenantId = tenantId;
  }
  try {
    const response = await get('api/decision-definition/xml', payload);

    return await response.text();
  } catch (e) {
    return null;
  }
}

export async function checkDeleteConflict(id, entity) {
  const response = await get(`api/${entity}/${id}/delete-conflicts`);
  return await response.json();
}
