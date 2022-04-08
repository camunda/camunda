/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, post} from 'request';

export async function getFlowNodeNames(processDefinitionKey, processDefinitionVersion, tenantId) {
  if (processDefinitionKey && processDefinitionVersion) {
    const payload = {
      processDefinitionKey,
      processDefinitionVersion,
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

export async function loadProcessDefinitionXml(key, version, tenantId) {
  const payload = {key, version};
  if (tenantId) {
    payload.tenantId = tenantId;
  }

  try {
    const response = await get('api/definition/process/xml', payload);

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
    const response = await get('api/definition/decision/xml', payload);

    return await response.text();
  } catch (e) {
    return null;
  }
}

export async function checkDeleteConflict(id, entity) {
  const response = await get(`api/${entity}/${id}/delete-conflicts`);
  return await response.json();
}

const loadVariablesFrom = (endpoint) => async (payload) => {
  const response = await post(endpoint, payload);

  return await response.json();
};

export const loadVariables = loadVariablesFrom('api/variables');
export const loadInputVariables = loadVariablesFrom('api/decision-variables/inputs/names');
export const loadOutputVariables = loadVariablesFrom('api/decision-variables/outputs/names');
