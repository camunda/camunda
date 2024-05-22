/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post} from 'request';

export async function loadFrequencyData(
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  identifier,
  filter
) {
  const response = await post(
    'api/report/evaluate',
    createFlowNodeFrequencyReport(
      processDefinitionKey,
      processDefinitionVersions,
      tenantIds,
      identifier,
      filter
    )
  );

  return await response.json();
}

function createFlowNodeFrequencyReport(key, versions, tenantIds, identifier, filter) {
  return {
    combined: false,
    reportType: 'process',
    data: {
      definitions: [{key, versions, tenantIds, identifier}],
      filter,
      view: {
        entity: 'flowNode',
        properties: ['frequency'],
      },
      groupBy: {
        type: 'flowNodes',
        unit: null,
      },
      visualization: 'heat',
    },
  };
}

export async function loadCorrelationData(
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  filter,
  gateway,
  end
) {
  const response = await post('api/analysis/correlation', {
    processDefinitionKey,
    processDefinitionVersions,
    tenantIds,
    filter,
    gateway,
    end,
  });

  return await response.json();
}
