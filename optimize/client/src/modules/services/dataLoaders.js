/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, post} from 'request';

export {
  loadProcessDefinitionXml,
  loadDecisionDefinitionXml,
  loadVariables,
  loadInputVariables,
  loadOutputVariables,
} from './dataLoaders.ts';

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
