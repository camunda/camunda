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
export {loadDecisionDefinitionXml, loadProcessDefinitionXml} from './dataLoaders.ts';
